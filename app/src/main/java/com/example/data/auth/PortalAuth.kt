package com.example.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.os.Build
import com.example.util.UniTrackLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

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

object PortalAuth {
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val BASE = "https://portal.aiub.edu"

    const val MAX_SOLVER_ATTEMPTS = 30
    const val MAX_SUBMISSIONS = 3
    const val OVERALL_TIMEOUT_MS = 90_000L

    private val loginMutex = Mutex()
    private val cookieJar = InMemoryCookieJar()
    private val client = OkHttpClient.Builder()
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
        onProgress: (attempt: Int, max: Int) -> Unit = { _, _ -> }
    ): LoginResult = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            try {
                loginMutex.withLock {
                    executeLogin(context, username, password, onProgress)
                }
            } catch (e: CancellationException) {
                LoginResult.Cancelled
            } catch (e: Exception) {
                UniTrackLogger.e("PortalAuth login exception: ${e.message}", e)
                LoginResult.NetworkError(e.message ?: "Unknown error")
            }
        }
        result ?: LoginResult.CaptchaFailed
    }

    private suspend fun executeLogin(
        context: Context,
        username: String,
        password: String,
        onProgress: (attempt: Int, max: Int) -> Unit
    ): LoginResult {
        cookieJar.clear()

        var submissionAttempts = 0
        var totalSolverAttempts = 0

        while (submissionAttempts < MAX_SUBMISSIONS) {
            currentCoroutineContext().ensureActive()
            submissionAttempts++

            try {
                // Step 1: GET "/"
                val getReq = Request.Builder()
                    .url(BASE)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .build()

                val getCall = client.newCall(getReq)
                val getResp = try {
                    getCall.execute()
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    throw e
                }
                val html = getResp.body?.string() ?: ""
                getResp.close()

                val (verificationToken, initialCaptchaToken) = parseLoginPage(html)
                var captchaToken = initialCaptchaToken

                var captchaAns = ""
                if (captchaToken.isNotBlank()) {
                    var solved: Int? = null

                    while (totalSolverAttempts < MAX_SOLVER_ATTEMPTS && solved == null) {
                        currentCoroutineContext().ensureActive()
                        totalSolverAttempts++
                        onProgress(totalSolverAttempts, MAX_SOLVER_ATTEMPTS)

                        // Step 2: GET captcha image
                        val imgReq = Request.Builder()
                            .url("$BASE/DefaultCaptcha/Generate?t=$captchaToken")
                            .header("User-Agent", DESKTOP_USER_AGENT)
                            .header("Referer", "$BASE/")
                            .header("Accept", "image/*")
                            .build()

                        val imgCall = client.newCall(imgReq)
                        val imgResp = try {
                            imgCall.execute()
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            throw e
                        }
                        val bytes = imgResp.body?.bytes()
                        imgResp.close()

                        if (bytes != null && bytes.isNotEmpty()) {
                            val bitmap = decodeGifToBitmap(bytes)
                            if (bitmap != null) {
                                solved = CaptchaSolver.solve(context, bitmap)
                            }
                        }

                        if (solved == null) {
                            if (totalSolverAttempts >= MAX_SOLVER_ATTEMPTS) break

                            // Step 4: Refresh captcha
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
                                val refCall = client.newCall(refreshReq)
                                val refResp = refCall.execute()
                                val refScript = refResp.body?.string() ?: ""
                                refResp.close()

                                val newToken = parseRefreshToken(refScript)
                                if (!newToken.isNullOrBlank()) {
                                    captchaToken = newToken
                                }
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                            }

                            delay(400)
                        }
                    }

                    if (solved == null) {
                        return LoginResult.CaptchaFailed
                    }
                    captchaAns = solved.toString()
                }

                currentCoroutineContext().ensureActive()

                // Step 5: Submit form
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

                val postCall = client.newCall(postReq)
                val postResp = try {
                    postCall.execute()
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    throw e
                }
                val finalUrl = postResp.request.url.toString()
                val respHtml = postResp.body?.string() ?: ""
                postResp.close()

                // Step 6: Success check
                if (finalUrl.contains("/Student", ignoreCase = true) || respHtml.contains("/Login/Logout", ignoreCase = true)) {
                    return LoginResult.Success(finalUrl)
                }

                val errDoc = Jsoup.parse(respHtml)
                val summaryErr = errDoc.select(".validation-summary-errors, .field-validation-error").text()

                val classifiedError = classifyLoginError(summaryErr)
                when (classifiedError) {
                    LoginErrorType.CAPTCHA -> {
                        // Re-submit allowed ONLY for captcha error
                        continue
                    }
                    LoginErrorType.LOCKED -> {
                        return LoginResult.Locked
                    }
                    LoginErrorType.WRONG_CREDENTIALS -> {
                        return LoginResult.WrongCredentials
                    }
                    LoginErrorType.UNKNOWN -> {
                        // ANY other outcome, including empty error text -> NetworkError immediately
                        return LoginResult.NetworkError("Unexpected response")
                    }
                }
            } catch (e: CancellationException) {
                return LoginResult.Cancelled
            } catch (e: Exception) {
                UniTrackLogger.e("PortalAuth network error: ${e.message}", e)
                return LoginResult.NetworkError(e.message ?: "Unknown network error")
            }
        }

        return LoginResult.CaptchaFailed
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
