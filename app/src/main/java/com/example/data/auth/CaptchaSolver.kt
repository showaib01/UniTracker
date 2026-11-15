package com.example.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import com.example.util.UniTrackLogger
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

interface CaptchaSolverEngine {
    suspend fun solveCaptcha(context: Context, webView: WebView?, imgUrl: String?): String?
}

object CaptchaSolverConstants {
    const val SCALE_FACTOR = 3
    const val TESS_WHITELIST = "0123456789+-x*=/?"
}

class TesseractCaptchaSolver : CaptchaSolverEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun solveCaptcha(context: Context, webView: WebView?, imgUrl: String?): String? = withContext(Dispatchers.Default) {
        val tessDir = ensureTessData(context) ?: run {
            UniTrackLogger.w("UniTrack: CaptchaSolver - tessdata setup failed")
            return@withContext null
        }

        for (attempt in 1..5) {
            UniTrackLogger.d("UniTrack: CaptchaSolver attempt $attempt/5")

            var bitmap: Bitmap? = if (webView != null) {
                getBitmapFromWebViewCanvas(webView)
            } else null

            if (bitmap == null && !imgUrl.isNullOrBlank()) {
                bitmap = downloadCaptchaBitmap(imgUrl, webView?.settings?.userAgentString)
            }

            if (bitmap == null) {
                UniTrackLogger.w("UniTrack: CaptchaSolver - failed to acquire bitmap")
                continue
            }

            val pass1Processed = preprocessBitmap(bitmap, blueThresholdSensitivity = 0.15f)
            val pass2Processed = preprocessBitmap(bitmap, blueThresholdSensitivity = 0.25f)

            val textPass1 = runOcrOnBitmap(tessDir, pass1Processed)
            val textPass2 = runOcrOnBitmap(tessDir, pass2Processed)

            val ans1 = evalMathEquation(textPass1)
            val ans2 = evalMathEquation(textPass2)

            UniTrackLogger.d("UniTrack: Captcha Pass1='$textPass1'->$ans1, Pass2='$textPass2'->$ans2")

            if (ans1 != null && ans1 == ans2) {
                UniTrackLogger.i("UniTrack: Captcha solver SUCCESS -> $ans1")
                return@withContext ans1.toString()
            }

            if (attempt < 5 && webView != null) {
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "(function(){ var img = document.querySelector('img[src*=\"captcha\"]') || document.querySelector('img[src*=\"Captcha\"]'); if(img){ img.click(); } })();",
                        null
                    )
                }
                kotlinx.coroutines.delay(1000)
            }
        }

        UniTrackLogger.w("UniTrack: CaptchaSolver failed after 5 attempts")
        null
    }

    private suspend fun getBitmapFromWebViewCanvas(webView: WebView): Bitmap? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val js = """
                (function() {
                    var img = document.querySelector('img[src*="captcha"]') || document.querySelector('img[src*="Captcha"]') || document.querySelector('#captchaImg');
                    if (!img) return '';
                    var canvas = document.createElement('canvas');
                    canvas.width = img.naturalWidth || img.width;
                    canvas.height = img.naturalHeight || img.height;
                    var ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0);
                    return canvas.toDataURL('image/png').replace(/^data:image\/png;base64,/, '');
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) { rawJson ->
                if (rawJson.isNullOrBlank() || rawJson == "null" || rawJson == "\"\"") {
                    continuation.resume(null)
                    return@evaluateJavascript
                }
                val cleanedB64 = rawJson.replace("\"", "").trim()
                try {
                    val bytes = Base64.decode(cleanedB64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    continuation.resume(bmp)
                } catch (e: Exception) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun downloadCaptchaBitmap(imgUrl: String, userAgent: String?): Bitmap? {
        return try {
            val cookies = CookieManager.getInstance().getCookie(imgUrl) ?: ""
            val request = Request.Builder()
                .url(imgUrl)
                .header("User-Agent", userAgent ?: "Mozilla/5.0 (Linux; Android 14)")
                .header("Cookie", cookies)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun preprocessBitmap(original: Bitmap, blueThresholdSensitivity: Float): Bitmap {
        val scaledWidth = original.width * CaptchaSolverConstants.SCALE_FACTOR
        val scaledHeight = original.height * CaptchaSolverConstants.SCALE_FACTOR
        val scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true)

        val processed = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val hsv = FloatArray(3)

        for (x in 0 until scaledWidth) {
            for (y in 0 until scaledHeight) {
                val pixel = scaled.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val valVal = hsv[2]

                val isBluePixel = (hue in 180f..260f) && (sat > (0.2f - blueThresholdSensitivity)) && (valVal > 0.2f)

                if (isBluePixel) {
                    processed.setPixel(x, y, Color.BLACK)
                } else {
                    processed.setPixel(x, y, Color.WHITE)
                }
            }
        }

        return addWhiteBorderPadding(processed, padding = 12)
    }

    private fun addWhiteBorderPadding(bmp: Bitmap, padding: Int): Bitmap {
        val newWidth = bmp.width + (padding * 2)
        val newHeight = bmp.height + (padding * 2)
        val padded = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        padded.eraseColor(Color.WHITE)

        val canvas = android.graphics.Canvas(padded)
        canvas.drawBitmap(bmp, padding.toFloat(), padding.toFloat(), null)
        return padded
    }

    private fun runOcrOnBitmap(tessDir: File, bitmap: Bitmap): String {
        var tessApi: TessBaseAPI? = null
        return try {
            tessApi = TessBaseAPI()
            tessApi.init(tessDir.parentFile?.absolutePath, "eng")
            tessApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            tessApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, CaptchaSolverConstants.TESS_WHITELIST)
            tessApi.setImage(bitmap)
            tessApi.utF8Text ?: ""
        } catch (e: Exception) {
            ""
        } finally {
            tessApi?.recycle()
        }
    }

    private fun ensureTessData(context: Context): File? {
        val tessDataFolder = File(context.filesDir, "tessdata")
        if (!tessDataFolder.exists()) {
            tessDataFolder.mkdirs()
        }

        val targetFile = File(tessDataFolder, "eng.traineddata")
        if (targetFile.exists() && targetFile.length() > 0) {
            return tessDataFolder
        }

        try {
            var inputStream: InputStream? = null
            val assetPaths = arrayOf("tessdata/eng.traineddata", "eng.traineddata")
            for (path in assetPaths) {
                try {
                    inputStream = context.assets.open(path)
                    if (inputStream != null) break
                } catch (e: Exception) {
                    // Try next path
                }
            }

            if (inputStream == null) {
                // If asset doesn't exist, create minimal traineddata placeholder file
                FileOutputStream(targetFile).use { it.write("tessdata_dummy".toByteArray()) }
                return tessDataFolder
            }

            FileOutputStream(targetFile).use { out ->
                inputStream.copyTo(out)
            }
            return tessDataFolder
        } catch (e: Exception) {
            return null
        }
    }
}

/**
 * Pure Kotlin arithmetic equation evaluator:
 * Parses OCR math strings like "7 + 5 = ?", "12 - 3 = ?", "4 x 2 = ?"
 */
fun evalMathEquation(rawOcr: String): Int? {
    if (rawOcr.isBlank()) return null

    val cleaned = rawOcr.replace(" ", "")
        .replace("×", "x")
        .replace("*", "x")
        .replace("÷", "/")
        .replace("?", "")
        .replace("=", "")
        .trim()

    val operatorRegex = Regex("""([+x/\\-])""")
    val parts = cleaned.split(operatorRegex)
    if (parts.size != 2) return null

    val operand1 = parts[0].filter { it.isDigit() }.toIntOrNull() ?: return null
    val operand2 = parts[1].filter { it.isDigit() }.toIntOrNull() ?: return null

    val opMatch = operatorRegex.find(cleaned)?.value ?: return null

    return when (opMatch) {
        "+" -> operand1 + operand2
        "-" -> operand1 - operand2
        "x" -> operand1 * operand2
        "/" -> if (operand2 != 0) operand1 / operand2 else null
        else -> null
    }
}
