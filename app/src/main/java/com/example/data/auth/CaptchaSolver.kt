package com.example.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.util.UniTrackLogger
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CaptchaSolver {

    private var tessApi: TessBaseAPI? = null
    @Synchronized
    private fun getTessApi(context: Context): TessBaseAPI? {
        if (tessApi != null) return tessApi
        try {
            val tessDir = ensureTessData(context) ?: return null
            val api = TessBaseAPI()
            val success = api.init(tessDir.absolutePath, "eng")
            if (!success) {
                api.recycle()
                return null
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789+-=?")
            tessApi = api
            return api
        } catch (e: Exception) {
            UniTrackLogger.e("CaptchaSolver: Failed to initialize Tesseract: ${e.message}")
            return null
        }
    }

    private fun ensureTessData(context: Context): File? {
        val tessFolder = File(context.filesDir, "tessdata")
        if (!tessFolder.exists()) tessFolder.mkdirs()
        val targetFile = File(tessFolder, "eng.traineddata")

        try {
            val assetStream: InputStream = context.assets.open("tessdata/eng.traineddata")
            val assetBytes = assetStream.readBytes()
            assetStream.close()

            if (assetBytes.size < 5 * 1024 * 1024) {
                UniTrackLogger.w("CaptchaSolver: Asset eng.traineddata is invalid (<5MB)")
                return null
            }

            if (!targetFile.exists() || targetFile.length() < assetBytes.size.toLong()) {
                FileOutputStream(targetFile).use { it.write(assetBytes) }
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

        val pass1Bmp = if (inkCount >= 100) {
            val despeckled = despeckle(pixels, w, h)
            processPass1(despeckled, w, h)
        } else {
            processFallback(pixels, w, h)
        }

        val pass2Bmp = if (inkCount >= 100) {
            val despeckled = despeckle(pixels, w, h)
            processPass2(despeckled, w, h)
        } else {
            null
        }

        synchronized(this) {
            api.setImage(pass1Bmp)
            val text1 = api.utF8Text ?: ""
            val conf1 = api.meanConfidence()
            val ans1 = parseEquation(text1)

            if (conf1 >= 85 && ans1 != null) {
                return ans1
            }

            if (pass2Bmp != null) {
                api.setImage(pass2Bmp)
                val text2 = api.utF8Text ?: ""
                val ans2 = parseEquation(text2)
                if (ans1 != null && ans1 == ans2) {
                    return ans1
                }
            } else if (ans1 != null) {
                return ans1
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

        // Remove 8-connected blobs smaller than 5 pixels
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

                    if (component.size < 5) {
                        for (cIdx in component) {
                            ink[cIdx] = false
                        }
                    }
                }
            }
        }
        return ink
    }

    private fun processPass1(ink: BooleanArray, w: Int, h: Int): Bitmap {
        // Directional dilation arm 2
        val dilated = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (ink[y * w + x]) {
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until w && ny in 0 until h) {
                                dilated[ny * w + nx] = true
                            }
                        }
                    }
                }
            }
        }
        return nearestNeighborUpscale(dilated, w, h, scale = 4)
    }

    private fun processPass2(ink: BooleanArray, w: Int, h: Int): Bitmap {
        // Morphological close + dilate arm 1
        val closed = BooleanArray(w * h)
        // Simple plus-shape close
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (ink[idx]) {
                    closed[idx] = true
                    if (x > 0) closed[y * w + (x - 1)] = true
                    if (x < w - 1) closed[y * w + (x + 1)] = true
                    if (y > 0) closed[(y - 1) * w + x] = true
                    if (y < h - 1) closed[(y + 1) * w + x] = true
                }
            }
        }
        return nearestNeighborUpscale(closed, w, h, scale = 4)
    }

    private fun processFallback(pixels: IntArray, w: Int, h: Int): Bitmap {
        val bw = BooleanArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
            bw[i] = lum <= 150
        }
        return nearestNeighborUpscale(bw, w, h, scale = 4)
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
