package com.example.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.BuildConfig
import com.example.util.UniTrackLogger
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CaptchaSolver {

    private const val CONFIDENCE_THRESHOLD = 85
    private const val MIN_INK_COUNT = 100
    private const val MIN_BLOB_SIZE = 5
    private const val UPSCALE_SCALE = 4
    private const val MIN_ASSET_SIZE_BYTES = 5 * 1024 * 1024L

    private var tessApi: TessBaseAPI? = null
    private var initFailed = false

    var debugBitmapSink: ((String, Bitmap) -> Unit)? = null

    @Synchronized
    private fun getTessApi(context: Context): TessBaseAPI? {
        if (initFailed) return null
        if (tessApi != null) return tessApi
        try {
            val tessDir = ensureTessData(context)
            if (tessDir == null) {
                initFailed = true
                return null
            }
            val api = TessBaseAPI()
            val success = api.init(tessDir.absolutePath, "eng")
            if (!success) {
                api.recycle()
                initFailed = true
                return null
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789+-=?")
            tessApi = api
            return api
        } catch (e: Exception) {
            UniTrackLogger.e("CaptchaSolver: Failed to initialize Tesseract: ${e.message}")
            initFailed = true
            return null
        }
    }

    private fun ensureTessData(context: Context): File? {
        val tessFolder = File(context.filesDir, "tessdata")
        if (!tessFolder.exists()) tessFolder.mkdirs()
        val targetFile = File(tessFolder, "eng.traineddata")

        try {
            var assetSize = -1L
            try {
                val afd = context.assets.openFd("tessdata/eng.traineddata")
                assetSize = afd.length
                afd.close()
            } catch (_: Exception) {}

            if (assetSize > 0 && assetSize < MIN_ASSET_SIZE_BYTES) {
                UniTrackLogger.w("CaptchaSolver: Asset eng.traineddata is invalid (<5MB)")
                return null
            }

            if (!targetFile.exists() || (assetSize > 0 && targetFile.length() != assetSize)) {
                val tempFile = File(tessFolder, "eng.traineddata.tmp")
                val assetStream: InputStream = context.assets.open("tessdata/eng.traineddata")
                FileOutputStream(tempFile).use { outStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead = 0L
                    while (assetStream.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                        totalRead += read
                    }
                    if (totalRead < MIN_ASSET_SIZE_BYTES) {
                        UniTrackLogger.w("CaptchaSolver: Copied asset eng.traineddata is invalid (<5MB)")
                        tempFile.delete()
                        return null
                    }
                }
                assetStream.close()
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                UniTrackLogger.i("CaptchaSolver: Copied eng.traineddata to ${targetFile.absolutePath}")
            }
            return tessFolder
        } catch (e: Exception) {
            UniTrackLogger.e("CaptchaSolver: Error ensuring tessdata: ${e.message}")
            return null
        }
    }

    fun solve(context: Context, bitmap: Bitmap): Int? {
        val api = getTessApi(context) ?: return null

        if (BuildConfig.DEBUG) {
            debugBitmapSink?.invoke("captcha_last_raw.png", bitmap)
        }

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var inkCount = 0
        for (p in pixels) {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            if (b > 200 && r < 60 && g < 60) {
                inkCount++
            }
        }

        if (inkCount < MIN_INK_COUNT) {
            val fallbackBmp = processFallback(pixels, w, h)
            synchronized(this) {
                api.setImage(fallbackBmp)
                val text = api.utF8Text ?: ""
                val conf = api.meanConfidence()
                val ans = parseEquation(text)
                if (BuildConfig.DEBUG) {
                    UniTrackLogger.d("CaptchaSolver [Fallback]: pass=0, conf=$conf, text='$text', ans=$ans")
                }
                return if (conf >= CONFIDENCE_THRESHOLD && ans != null) ans else null
            }
        }

        val despeckled = despeckle(pixels, w, h)
        val pass1Bmp = processPass1(despeckled, w, h)
        val pass2Bmp = processPass2(despeckled, w, h)

        if (BuildConfig.DEBUG) {
            debugBitmapSink?.invoke("captcha_last_pass1.png", pass1Bmp)
        }

        synchronized(this) {
            api.setImage(pass1Bmp)
            val text1 = api.utF8Text ?: ""
            val conf1 = api.meanConfidence()
            val ans1 = parseEquation(text1)

            if (BuildConfig.DEBUG) {
                UniTrackLogger.d("CaptchaSolver: pass=1, conf=$conf1, text='$text1', ans=$ans1")
            }

            if (conf1 >= CONFIDENCE_THRESHOLD && ans1 != null) {
                return ans1
            }

            api.setImage(pass2Bmp)
            val text2 = api.utF8Text ?: ""
            val conf2 = api.meanConfidence()
            val ans2 = parseEquation(text2)

            if (BuildConfig.DEBUG) {
                UniTrackLogger.d("CaptchaSolver: pass=2, conf=$conf2, text='$text2', ans=$ans2")
            }

            if (ans1 != null && ans1 == ans2) {
                return ans1
            }

            if (ans2 != null && conf2 >= CONFIDENCE_THRESHOLD) {
                return ans2
            }
        }

        return null
    }

    private fun despeckle(pixels: IntArray, w: Int, h: Int): BooleanArray {
        val ink = BooleanArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            ink[i] = (b > 200 && r < 60 && g < 60)
        }

        val visited = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (ink[idx] && !visited[idx]) {
                    val component = mutableListOf<Int>()
                    val queue = mutableListOf(idx)
                    visited[idx] = true

                    var head = 0
                    while (head < queue.size) {
                        val curr = queue[head++]
                        component.add(curr)
                        val cx = curr % w
                        val cy = curr / w

                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in 0 until w && ny in 0 until h) {
                                    val nIdx = ny * w + nx
                                    if (ink[nIdx] && !visited[nIdx]) {
                                        visited[nIdx] = true
                                        queue.add(nIdx)
                                    }
                                }
                            }
                        }
                    }

                    if (component.size < MIN_BLOB_SIZE) {
                        for (cIdx in component) {
                            ink[cIdx] = false
                        }
                    }
                }
            }
        }
        return ink
    }

    private fun directionalDilate(src: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (src[y * w + x]) {
                    for (r in -radius..radius) {
                        // Horizontal
                        val hx = x + r
                        if (hx in 0 until w) out[y * w + hx] = true
                        // Vertical
                        val vy = y + r
                        if (vy in 0 until h) out[vy * w + x] = true
                        // Main diagonal
                        if (hx in 0 until w && vy in 0 until h) out[vy * w + hx] = true
                        // Anti-diagonal
                        val ay = y - r
                        if (hx in 0 until w && ay in 0 until h) out[ay * w + hx] = true
                    }
                }
            }
        }
        return out
    }

    private fun processPass1(ink: BooleanArray, w: Int, h: Int): Bitmap {
        val dilated = directionalDilate(ink, w, h, radius = 2)
        return nearestNeighborUpscale(dilated, w, h, scale = UPSCALE_SCALE)
    }

    private fun processPass2(ink: BooleanArray, w: Int, h: Int): Bitmap {
        // Plus-shaped close: dilate arm 1, then keep pixels whose 4-connected neighbors are also set
        val dilatedArm1 = directionalDilate(ink, w, h, radius = 1)
        val closed = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (dilatedArm1[idx]) {
                    val hasLeft = x > 0 && dilatedArm1[y * w + (x - 1)]
                    val hasRight = x < w - 1 && dilatedArm1[y * w + (x + 1)]
                    val hasTop = y > 0 && dilatedArm1[(y - 1) * w + x]
                    val hasBottom = y < h - 1 && dilatedArm1[(y + 1) * w + x]
                    if (hasLeft && hasRight && hasTop && hasBottom) {
                        closed[idx] = true
                    }
                }
            }
        }
        val finalDilated = directionalDilate(closed, w, h, radius = 1)
        return nearestNeighborUpscale(finalDilated, w, h, scale = UPSCALE_SCALE)
    }

    private fun processFallback(pixels: IntArray, w: Int, h: Int): Bitmap {
        val bw = BooleanArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
            bw[i] = lum <= 150
        }
        return nearestNeighborUpscale(bw, w, h, scale = UPSCALE_SCALE)
    }

    private fun nearestNeighborUpscale(src: BooleanArray, w: Int, h: Int, scale: Int): Bitmap {
        val nw = w * scale
        val nh = h * scale
        val outBmp = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(nw * nh)

        for (y in 0 until nh) {
            val sy = y / scale
            for (x in 0 until nw) {
                val sx = x / scale
                val isSet = src[sy * w + sx]
                outPixels[y * nw + x] = if (isSet) Color.BLACK else Color.WHITE
            }
        }
        outBmp.setPixels(outPixels, 0, nw, 0, 0, nw, nh)
        return outBmp
    }

    fun parseEquation(rawOcr: String): Int? {
        if (rawOcr.isBlank()) return null
        val beforeEq = rawOcr.substringBefore('=').trim()
        val cleaned = beforeEq.filter { it.isDigit() || it == '+' || it == '-' }
        if (cleaned.isBlank()) return null

        val regex = Regex("""^(\d{1,2})([+-])(\d{1,2})$""")
        val match = regex.find(cleaned) ?: return null

        val a = match.groupValues[1].toIntOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toIntOrNull() ?: return null

        val ans = when (op) {
            "+" -> a + b
            "-" -> a - b
            else -> return null
        }

        return if (ans in 0..100) ans else null
    }
}
