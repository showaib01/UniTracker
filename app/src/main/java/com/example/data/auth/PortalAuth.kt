package com.example.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.os.Build
import com.example.BuildConfig
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class LoginErrorType {
    CAPTCHA,
    LOCKED,
    WRONG_CREDENTIALS,
    UNKNOWN
}

sealed interface LoginResult {
    data class Success(val landingUrl: String) : LoginResult
    object WrongCredentials : LoginResult
    object Locked : LoginResult
    object CaptchaFailed : LoginResult
    data class NetworkError(val message: String) : LoginResult
    object Cancelled : LoginResult
}

class InMemoryCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        for (c in cookies) {
            list.removeIf { it.name == c.name }
            list.add(c)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val result = mutableListOf<Cookie>()
        cookieStore[host]?.let { result.addAll(it) }
        for ((domain, cookies) in cookieStore) {
            if (host.endsWith(".$domain") || host == domain) {
                result.addAll(cookies)
            }
        }
        return result.distinctBy { it.name }
    }

    fun getCookiesForHost(host: String): List<Cookie> {
        val result = mutableListOf<Cookie>()
        for ((domain, cookies) in cookieStore) {
            if (host.endsWith(".$domain") || host == domain) {
                result.addAll(cookies)
            }
        }
        return result.distinctBy { it.name }
    }

    fun clear() {
        cookieStore.clear()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    })
}

object PortalAuth {
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val BASE = "https://portal.aiub.edu"

    const val MAX_SOLVER_ATTEMPTS = 30
    const val MAX_SUBMISSIONS = 3
    const val OVERALL_TIMEOUT_MS = 90_000L

    private val loginMutex = Mutex()
    private val cookieJar = InMemoryCookieJar()
    val baseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()

    fun parseLoginPage(html: String): Pair<String, String> {
        if (html.isBlank()) return Pair("", "")
        val doc = Jsoup.parse(html)
        val verificationToken = doc.select("input[name=__RequestVerificationToken]").attr("value")
        val captchaToken = doc.select("#CaptchaDeText").attr("value")
        return Pair(verificationToken, captchaToken)
    }

    fun parseRefreshToken(js: String): String? {
        if (js.isBlank()) return null
        val matcher = Regex("""attr\("value",\s*"([^"]+)"""").find(js)
        return if (matcher != null && matcher.groupValues.size > 1) {
            matcher.groupValues[1]
        } else null
    }

    fun classifyLoginError(text: String): LoginErrorType {
        if (text.isBlank()) return LoginErrorType.UNKNOWN
        val lower = text.lowercase()
        return when {
            lower.contains("captcha") -> LoginErrorType.CAPTCHA
            lower.contains("locked") -> LoginErrorType.LOCKED
            lower.contains("invalid") || lower.contains("incorrect") || lower.contains("wrong") || lower.contains("not match") -> LoginErrorType.WRONG_CREDENTIALS
            else -> LoginErrorType.UNKNOWN
        }
    }

    suspend fun login(
        context: Context,
        username: String,
        password: String,
        manualCaptcha: (suspend (image: Bitmap, refresh: suspend () -> Bitmap?) -> String?)? = null,
        onProgress: (attempt: Int, max: Int) -> Unit = { _, _ -> }
    ): LoginResult = withContext(Dispatchers.IO) {
        val autoResult = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            loginMutex.withLock {
                executeLogin(context, username, password, onProgress)
            }
        }
        if (autoResult != null && autoResult !is LoginResult.CaptchaFailed && autoResult !is LoginResult.Cancelled) {
            return@withContext autoResult
        }
        if (manualCaptcha != null) {
            loginMutex.withLock {
                executeLoginManual(context, username, password, manualCaptcha)
            }
        } else {
            autoResult ?: LoginResult.CaptchaFailed
        }
    }

    private suspend fun executeLogin(
        context: Context,
        username: String,
        password: String,
        onProgress: (attempt: Int, max: Int) -> Unit
    ): LoginResult {
        cookieJar.clear()

        if (BuildConfig.DEBUG) {
            CaptchaSolver.debugBitmapSink = { fileName, bmp ->
                try {
                    val debugDir = context.getExternalFilesDir("debug")
                    if (debugDir != null) {
                        if (!debugDir.exists()) debugDir.mkdirs()
                        val file = File(debugDir, fileName)
                        FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        var submissionAttempts = 0
        var totalSolverAttempts = 0

        while (submissionAttempts < MAX_SUBMISSIONS) {
            currentCoroutineContext().ensureActive()
            submissionAttempts++

            try {
                val getReq = Request.Builder()
                    .url(BASE)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .build()

                val getResp = baseClient.newCall(getReq).await()
                val html = getResp.body?.string() ?: ""
                getResp.close()

                val (verificationToken, initialCaptchaToken) = parseLoginPage(html)
                var captchaToken = initialCaptchaToken

                UniTrackLogger.d("PortalAuth: GET '/' status=${getResp.code}, hasCaptchaToken=${initialCaptchaToken.isNotBlank()}")

                var captchaAns = ""
                if (captchaToken.isNotBlank()) {
                    var solved: Int? = null

                    while (totalSolverAttempts < MAX_SOLVER_ATTEMPTS && solved == null) {
                        currentCoroutineContext().ensureActive()
                        totalSolverAttempts++
                        onProgress(totalSolverAttempts, MAX_SOLVER_ATTEMPTS)

                        val imgReq = Request.Builder()
                            .url("$BASE/DefaultCaptcha/Generate?t=$captchaToken")
                            .header("User-Agent", DESKTOP_USER_AGENT)
                            .header("Referer", "$BASE/")
                            .header("Accept", "image/*")
                            .build()

                        val imgResp = baseClient.newCall(imgReq).await()
                        val bytes = imgResp.body?.bytes()
                        imgResp.close()

                        val bitmap = if (bytes != null && bytes.isNotEmpty()) decodeGifToBitmap(bytes) else null
                        UniTrackLogger.d("PortalAuth: Captcha img bytes=${bytes?.size ?: 0}, bmpSize=${bitmap?.let { "${it.width}x${it.height}" } ?: "decode failed"}")

                        if (bitmap != null) {
                            solved = CaptchaSolver.solve(context, bitmap)
                        }

                        var refreshSuccess = false
                        if (solved == null) {
                            if (totalSolverAttempts >= MAX_SOLVER_ATTEMPTS) break

                            val refreshBody = FormBody.Builder()
                                .add("t", captchaToken)
                                .add("__m__", "0")
                                .build()

                            val refreshReq = Request.Builder()
                                .url("$BASE/DefaultCaptcha/Refresh")
                                .post(refreshBody)
                                .header("User-Agent", DESKTOP_USER_AGENT)
                                .header("Referer", "$BASE/")
                                .header("X-Requested-With", "XMLHttpRequest")
                                .build()

                            try {
                                val refResp = baseClient.newCall(refreshReq).await()
                                val refScript = refResp.body?.string() ?: ""
                                refResp.close()

                                val newToken = parseRefreshToken(refScript)
                                if (!newToken.isNullOrBlank()) {
                                    captchaToken = newToken
                                    refreshSuccess = true
                                }
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                            }

                            delay(400)
                        }

                        UniTrackLogger.d("PortalAuth: Solver attempt $totalSolverAttempts -> ${if (solved != null) "solved" else "failed"}, refreshed=$refreshSuccess")
                    }

                    if (solved == null) {
                        return LoginResult.CaptchaFailed
                    }
                    captchaAns = solved.toString()
                }

                currentCoroutineContext().ensureActive()

                val formBuilder = FormBody.Builder()
                    .add("__RequestVerificationToken", verificationToken)
                    .add("UserName", username)
                    .add("Password", password)
                    .add("fingerPrint", "-")
                    .add("CaptchaDeText", captchaToken)
                    .add("CaptchaInputText", captchaAns)

                val postReq = Request.Builder()
                    .url(BASE)
                    .post(formBuilder.build())
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .header("Referer", "$BASE/")
                    .build()

                val postResp = baseClient.newCall(postReq).await()
                val finalUrl = postResp.request.url.toString()
                val finalPath = postResp.request.url.encodedPath
                val respHtml = postResp.body?.string() ?: ""
                postResp.close()

                if (finalUrl.contains("/Student", ignoreCase = true) || respHtml.contains("/Login/Logout", ignoreCase = true)) {
                    UniTrackLogger.d("PortalAuth: POST success -> final path='$finalPath'")
                    return LoginResult.Success(finalUrl)
                }

                val errDoc = Jsoup.parse(respHtml)
                val summaryErr = errDoc.select(".validation-summary-errors, .field-validation-error").text()

                val classifiedError = classifyLoginError(summaryErr)
                UniTrackLogger.d("PortalAuth: POST final path='$finalPath', errType=$classifiedError, errText='$summaryErr'")

                when (classifiedError) {
                    LoginErrorType.CAPTCHA -> {
                        continue
                    }
                    LoginErrorType.LOCKED -> {
                        return LoginResult.Locked
                    }
                    LoginErrorType.WRONG_CREDENTIALS -> {
                        return LoginResult.WrongCredentials
                    }
                    LoginErrorType.UNKNOWN -> {
                        return LoginResult.NetworkError("Unexpected response")
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                UniTrackLogger.e("PortalAuth network error: ${e.message}", e)
                return LoginResult.NetworkError(e.message ?: "Unknown network error")
            }
        }

        return LoginResult.CaptchaFailed
    }

    private suspend fun executeLoginManual(
        context: Context,
        username: String,
        password: String,
        manualCaptcha: suspend (image: Bitmap, refresh: suspend () -> Bitmap?) -> String?
    ): LoginResult {
        try {
            val getReq = Request.Builder()
                .url(BASE)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .build()

            val getResp = baseClient.newCall(getReq).await()
            val html = getResp.body?.string() ?: ""
            getResp.close()

            val (verificationToken, initialCaptchaToken) = parseLoginPage(html)
            var captchaToken = initialCaptchaToken

            val refreshLambda: suspend () -> Bitmap? = {
                val refreshBody = FormBody.Builder()
                    .add("t", captchaToken)
                    .add("__m__", "0")
                    .build()
                val refreshReq = Request.Builder()
                    .url("$BASE/DefaultCaptcha/Refresh")
                    .post(refreshBody)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .header("Referer", "$BASE/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                try {
                    val refResp = baseClient.newCall(refreshReq).await()
                    val refScript = refResp.body?.string() ?: ""
                    refResp.close()
                    val newToken = parseRefreshToken(refScript)
                    if (!newToken.isNullOrBlank()) {
                        captchaToken = newToken
                        val imgReq = Request.Builder()
                            .url("$BASE/DefaultCaptcha/Generate?t=$captchaToken")
                            .header("User-Agent", DESKTOP_USER_AGENT)
                            .header("Referer", "$BASE/")
                            .header("Accept", "image/*")
                            .build()
                        val imgResp = baseClient.newCall(imgReq).await()
                        val bytes = imgResp.body?.bytes()
                        imgResp.close()
                        if (bytes != null && bytes.isNotEmpty()) decodeGifToBitmap(bytes) else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }

            val imgReq = Request.Builder()
                .url("$BASE/DefaultCaptcha/Generate?t=$captchaToken")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Referer", "$BASE/")
                .header("Accept", "image/*")
                .build()

            val imgResp = baseClient.newCall(imgReq).await()
            val bytes = imgResp.body?.bytes()
            imgResp.close()

            val bitmap = if (bytes != null && bytes.isNotEmpty()) decodeGifToBitmap(bytes) else null
            if (bitmap == null) return LoginResult.CaptchaFailed

            val captchaAns = manualCaptcha(bitmap, refreshLambda) ?: return LoginResult.Cancelled

            val formBuilder = FormBody.Builder()
                .add("__RequestVerificationToken", verificationToken)
                .add("UserName", username)
                .add("Password", password)
                .add("fingerPrint", "-")
                .add("CaptchaDeText", captchaToken)
                .add("CaptchaInputText", captchaAns)

            val postReq = Request.Builder()
                .url(BASE)
                .post(formBuilder.build())
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Referer", "$BASE/")
                .build()

            val postResp = baseClient.newCall(postReq).await()
            val finalUrl = postResp.request.url.toString()
            val respHtml = postResp.body?.string() ?: ""
            postResp.close()

            if (finalUrl.contains("/Student", ignoreCase = true) || respHtml.contains("/Login/Logout", ignoreCase = true)) {
                return LoginResult.Success(finalUrl)
            }

            val errDoc = Jsoup.parse(respHtml)
            val summaryErr = errDoc.select(".validation-summary-errors, .field-validation-error").text()
            val classifiedError = classifyLoginError(summaryErr)
            return when (classifiedError) {
                LoginErrorType.CAPTCHA -> LoginResult.CaptchaFailed
                LoginErrorType.LOCKED -> LoginResult.Locked
                LoginErrorType.WRONG_CREDENTIALS -> LoginResult.WrongCredentials
                else -> LoginResult.NetworkError("Unexpected response")
            }
        } catch (e: Exception) {
            return LoginResult.NetworkError(e.message ?: "Unknown error")
        }
    }

    private fun decodeGifToBitmap(bytes: ByteArray): Bitmap? {
        return try {
            val srcBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
                @Suppress("DEPRECATION")
                val width = if (movie.width() > 0) movie.width() else 150
                @Suppress("DEPRECATION")
                val height = if (movie.height() > 0) movie.height() else 50
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                @Suppress("DEPRECATION")
                movie.setTime(0)
                @Suppress("DEPRECATION")
                movie.draw(canvas, 0f, 0f)
                bmp
            } ?: return null

            val width = srcBitmap.width
            val height = srcBitmap.height
            val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flattened)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(srcBitmap, 0f, 0f, null)
            flattened
        } catch (e: Exception) {
            UniTrackLogger.e("PortalAuth decodeGif error: ${e.message}")
            null
        }
    }

    fun cookiesForWebView(): List<String> {
        val cookies = cookieJar.getCookiesForHost("portal.aiub.edu")
        return cookies.map { "${it.name}=${it.value}" }
    }
}
