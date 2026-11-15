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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

sealed interface LoginResult {
    data class Success(val landingUrl: String) : LoginResult
    object WrongCredentials : LoginResult
    object Locked : LoginResult
    object CaptchaFailed : LoginResult
    data class NetworkError(val message: String) : LoginResult
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

    private val cookieJar = InMemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()

    suspend fun login(context: Context, username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        cookieJar.clear()

        var submissionAttempts = 0
        while (submissionAttempts < 3) {
            submissionAttempts++

            try {
                // Step 1: GET "/"
                val getReq = Request.Builder()
                    .url(BASE)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .build()

                val getResp = client.newCall(getReq).execute()
                val html = getResp.body?.string() ?: ""
                getResp.close()

                val doc = Jsoup.parse(html)
                val verificationToken = doc.select("input[name=__RequestVerificationToken]").attr("value")
                var captchaToken = doc.select("#CaptchaDeText").attr("value")

                var captchaAns = ""
                if (captchaToken.isNotBlank()) {
                    var solved: Int? = null
                    var solverAttempts = 0

                    while (solverAttempts < 30 && solved == null) {
                        solverAttempts++

                        // Step 2: GET captcha image
                        val imgReq = Request.Builder()
                            .url("$BASE/DefaultCaptcha/Generate?t=$captchaToken")
                            .header("User-Agent", DESKTOP_USER_AGENT)
                            .header("Referer", "$BASE/")
                            .header("Accept", "image/*")
                            .build()

                        val imgResp = client.newCall(imgReq).execute()
                        val bytes = imgResp.body?.bytes()
                        imgResp.close()

                        if (bytes != null && bytes.isNotEmpty()) {
                            val bitmap = decodeGifToBitmap(bytes)
                            if (bitmap != null) {
                                solved = CaptchaSolver.solve(context, bitmap)
                            }
                        }

                        if (solved == null) {
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
                                val refResp = client.newCall(refreshReq).execute()
                                val refScript = refResp.body?.string() ?: ""
                                refResp.close()

                                val matcher = Regex("""attr\("value",\s*"([^"]+)"""").find(refScript)
                                if (matcher != null && matcher.groupValues.size > 1) {
                                    captchaToken = matcher.groupValues[1]
                                }
                            } catch (_: Exception) {}

                            delay(400)
                        }
                    }

                    if (solved == null) {
                        return@withContext LoginResult.CaptchaFailed
                    }
                    captchaAns = solved.toString()
                }

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

                val postResp = client.newCall(postReq).execute()
                val finalUrl = postResp.request.url.toString()
                val respHtml = postResp.body?.string() ?: ""
                postResp.close()

                // Step 6: Success check
                if (finalUrl.contains("/Student", ignoreCase = true) || respHtml.contains("/Login/Logout", ignoreCase = true)) {
                    return@withContext LoginResult.Success(finalUrl)
                }

                val errDoc = Jsoup.parse(respHtml)
                val summaryErr = errDoc.select(".validation-summary-errors, .field-validation-error").text()

                if (summaryErr.isNotBlank()) {
                    UniTrackLogger.w("PortalAuth login error: $summaryErr")
                    val lower = summaryErr.lowercase()
                    when {
                        lower.contains("captcha") -> {
                            continue
                        }
                        lower.contains("locked") -> {
                            return@withContext LoginResult.Locked
                        }
                        lower.contains("invalid") || lower.contains("incorrect") || lower.contains("user") || lower.contains("password") -> {
                            return@withContext LoginResult.WrongCredentials
                        }
                    }
                }
            } catch (e: Exception) {
                UniTrackLogger.e("PortalAuth network error: ${e.message}", e)
                return@withContext LoginResult.NetworkError(e.message ?: "Unknown network error")
            }
        }

        LoginResult.CaptchaFailed
    }

    private fun decodeGifToBitmap(bytes: ByteArray): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                @Suppress("DEPRECATION")
                movie.setTime(0)
                @Suppress("DEPRECATION")
                movie.draw(canvas, 0f, 0f)
                bitmap
            }
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
