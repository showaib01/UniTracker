package com.example.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.BuildConfig
import com.example.util.UniTrackLogger
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                UniTrackLogger.e("CaptchaSolver: ensureTessData returned null")
                return null
            }
            val api = TessBaseAPI()
            val success = api.init(context.filesDir.absolutePath, "eng")
            if (!success) {
                UniTrackLogger.e("CaptchaSolver: Failed to initialize Tesseract with path: ${context.filesDir.absolutePath}")
                api.recycle()
                initFailed = true
                return null
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789+-=?")
            tessApi = api
            return api
        } catch (e: Throwable) {
            UniTrackLogger.e("CaptchaSolver: Failed to initialize Tesseract (likely JVM/Robolectric test environment): ${e.message}")
            initFailed = true
            return null
        }
    }

    fun isReady(context: Context): Boolean {
        return getTessApi(context) != null
    }

    suspend fun warmUp(context: Context) = withContext(Dispatchers.Default) {
        getTessApi(context)
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

    private fun isBlueInk(p: Int): Boolean {
        val r = Color.red(p)
        val g = Color.green(p)
        val b = Color.blue(p)
        return b > 200 && r < 60 && g < 60
    }

    // Reusable buffers for despeckle
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedVisited: BooleanArray? = null
    private var cachedQueue: IntArray? = null
    private var cachedComponent: IntArray? = null

    private fun getBuffers(w: Int, h: Int): Triple<BooleanArray, IntArray, IntArray> {
        val size = w * h
        if (cachedWidth != w || cachedHeight != h || cachedVisited == null || cachedVisited!!.size < size) {
            cachedWidth = w
            cachedHeight = h
            cachedVisited = BooleanArray(size)
            cachedQueue = IntArray(size)
            cachedComponent = IntArray(size)
        }
        return Triple(cachedVisited!!, cachedQueue!!, cachedComponent!!)
    }

    data class SolverResult(
        val decision: Int?,
        val decodeTimeMs: Long,
        val inkCount: Int,
        val pipeline: String,
        val preTimeMs: Long,
        val ocr1TimeMs: Long,
        val conf1: Int,
        val conf2: Int,
        val text1: String,
        val ocr2TimeMs: Long,
        val text2: String?,
        val ans2: Int?
    )

    fun solveWithMetrics(context: Context, bitmap: Bitmap): SolverResult {
        val startTime = System.nanoTime()
        val api = getTessApi(context) ?: return SolverResult(null, 0L, 0, "error", 0L, 0L, 0, 0, "", 0L, null, null)

        val decodeTime = (System.nanoTime() - startTime) / 1_000_000L

        if (BuildConfig.DEBUG) {
            debugBitmapSink?.invoke("captcha_last_raw.png", bitmap)
        }

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var inkCount = 0
        for (p in pixels) {
            if (isBlueInk(p)) {
                inkCount++
            }
        }

        val preStartTime = System.nanoTime()
        if (inkCount < MIN_INK_COUNT) {
            val fallbackBmp = processFallback(pixels, w, h)
            val preTime = (System.nanoTime() - preStartTime) / 1_000_000L
            val ocrStartTime = System.nanoTime()
            var text = ""
            var conf = 0
            synchronized(this) {
                api.setImage(fallbackBmp)
                text = api.utF8Text ?: ""
                conf = api.meanConfidence()
            }
            val ocrTime = (System.nanoTime() - ocrStartTime) / 1_000_000L
            val ans = parseEquation(text)
            if (BuildConfig.DEBUG) {
                debugBitmapSink?.invoke("captcha_last_pass1.png", fallbackBmp)
                UniTrackLogger.d("CaptchaSolver [Fallback]: conf=$conf, text='$text', ans=$ans")
            }
            val decision = if (conf >= CONFIDENCE_THRESHOLD && ans != null) ans else null
            return SolverResult(decision, decodeTime, inkCount, "luminance", preTime, ocrTime, conf, 0, text, 0L, null, null)
        }

        val ink = BooleanArray(w * h)
        for (i in pixels.indices) {
            ink[i] = isBlueInk(pixels[i])
        }

        val despeckled = despeckle(ink, w, h, minBlobSize = MIN_BLOB_SIZE)
        val pass1Bmp = processPass1(despeckled, w, h)
        val preTime = (System.nanoTime() - preStartTime) / 1_000_000L

        if (BuildConfig.DEBUG) {
            debugBitmapSink?.invoke("captcha_last_pass1.png", pass1Bmp)
        }

        val ocr1StartTime = System.nanoTime()
        var text1 = ""
        var conf1 = 0
        var ans1: Int? = null
        synchronized(this) {
            api.setImage(pass1Bmp)
            text1 = api.utF8Text ?: ""
            conf1 = api.meanConfidence()
            ans1 = parseEquation(text1)
        }
        val ocr1Time = (System.nanoTime() - ocr1StartTime) / 1_000_000L

        // Lazy pass 2 evaluation (run only when pass 1 is not accepted: a1 != null && c1 >= 85 -> return immediately)
        if (ans1 != null && conf1 >= 85) {
            return SolverResult(ans1, decodeTime, inkCount, "blue", preTime, ocr1Time, conf1, 0, text1, 0L, null, null)
        }

        val pass2Bmp = processPass2(despeckled, w, h)
        if (BuildConfig.DEBUG) {
            debugBitmapSink?.invoke("captcha_last_pass2.png", pass2Bmp)
        }

        val ocr2StartTime = System.nanoTime()
        var text2 = ""
        var conf2 = 0
        var ans2: Int? = null
        synchronized(this) {
            api.setImage(pass2Bmp)
            text2 = api.utF8Text ?: ""
            conf2 = api.meanConfidence()
            ans2 = parseEquation(text2)
        }
        val ocr2Time = (System.nanoTime() - ocr2StartTime) / 1_000_000L

        val decision = decideAnswer(ans1, conf1, ans2)
        return SolverResult(decision, decodeTime, inkCount, "blue", preTime, ocr1Time, conf1, conf2, text1, ocr2Time, text2, ans2)
    }

    fun solve(context: Context, bitmap: Bitmap): Int? {
        return solveWithMetrics(context, bitmap).decision
    }

    fun decideAnswer(a1: Int?, c1: Int, a2: Int?): Int? {
        if (a1 != null && c1 >= 85) return a1
        if (a1 != null && a1 == a2) return a1
        return null
    }

    internal fun despeckle(ink: BooleanArray, w: Int, h: Int, minBlobSize: Int): BooleanArray {
        val (visited, queue, component) = getBuffers(w, h)
        visited.fill(false, 0, w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (ink[idx] && !visited[idx]) {
                    var head = 0
                    var tail = 0
                    queue[tail++] = idx
                    visited[idx] = true

                    var compSize = 0
                    component[compSize++] = idx

                    while (head < tail) {
                        val curr = queue[head++]
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
                                        queue[tail++] = nIdx
                                        component[compSize++] = nIdx
                                    }
                                }
                            }
                        }
                    }

                    if (compSize < minBlobSize) {
                        for (i in 0 until compSize) {
                            ink[component[i]] = false
                        }
                    }
                }
            }
        }
        return ink
    }

    internal fun directionalDilate(src: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (src[y * w + x]) {
                    for (r in -radius..radius) {
                        val hx = x + r
                        if (hx in 0 until w) out[y * w + hx] = true
                        val vy = y + r
                        if (vy in 0 until h) out[vy * w + x] = true
                        if (hx in 0 until w && vy in 0 until h) out[vy * w + hx] = true
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
        val despeckled = despeckle(bw, w, h, minBlobSize = 50)
        val dilated = directionalDilate(despeckled, w, h, radius = 8)
        return nearestNeighborUpscale(dilated, w, h, scale = 4)
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
