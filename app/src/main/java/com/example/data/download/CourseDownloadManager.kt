package com.example.data.download

import android.content.Context
import android.os.Environment
import android.webkit.CookieManager
import com.example.UniTrackApplication
import com.example.data.auth.PortalAuth
import com.example.data.local.entity.DownloadedFileEntity
import com.example.util.UniTrackLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class DownloadProgressInfo(
    val url: String,
    val fileName: String,
    val progressPercent: Int,
    val isComplete: Boolean = false,
    val error: String? = null
)

object CourseDownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = PortalAuth.baseClient

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgressInfo>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgressInfo>> = _activeDownloads.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<Pair<String, String?>>()
    val snackbarEvents: SharedFlow<Pair<String, String?>> = _snackbarEvents.asSharedFlow()

    fun enqueueDownload(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        courseKey: String,
        courseCode: String,
        courseName: String
    ) {
        scope.launch {
            try {
                performDownload(
                    context = context,
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    courseKey = courseKey,
                    courseCode = courseCode,
                    courseName = courseName
                )
            } catch (e: Exception) {
                UniTrackLogger.e("CourseDownloadManager error: ${e.message}", e)
                _snackbarEvents.emit(Pair("Download failed: ${e.message}", null))
            }
        }
    }

    private suspend fun performDownload(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        courseKey: String,
        courseCode: String,
        courseName: String
    ) = withContext(Dispatchers.IO) {
        val rawFileName = parseFileName(url, contentDisposition)
        val sanitizedFileName = sanitizeFileName(rawFileName)

        val folderName = sanitizeFileName("${courseCode.ifBlank { "GENERAL" }} - ${courseName.ifBlank { "General" }}")
        val baseDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UniTrack/Courses/$folderName")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val targetFile = resolveUniqueFile(baseDir, sanitizedFileName)
        val app = context.applicationContext as UniTrackApplication

        val cookies = CookieManager.getInstance().getCookie(url) ?: ""

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent ?: PortalAuth.DESKTOP_USER_AGENT)
            .header("Cookie", cookies)

        UniTrackLogger.i("CourseDownloadManager: Starting download of $url to ${targetFile.name}")
        _activeDownloads.value = _activeDownloads.value + (url to DownloadProgressInfo(url, targetFile.name, 0))

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val err = "Server returned ${response.code}"
            _activeDownloads.value = _activeDownloads.value + (url to DownloadProgressInfo(url, targetFile.name, 0, false, err))
            _snackbarEvents.emit(Pair("Download failed: $err", null))
            return@withContext
        }

        val body = response.body ?: run {
            _snackbarEvents.emit(Pair("Download failed: Empty response body", null))
            return@withContext
        }

        val totalLength = body.contentLength()
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                val progress = if (totalLength > 0) ((totalRead * 100) / totalLength).toInt() else 50
                _activeDownloads.value = _activeDownloads.value + (url to DownloadProgressInfo(url, targetFile.name, progress))
            }

            outputStream.flush()

            val finalMime = mimeType?.ifBlank { null } ?: response.header("Content-Type") ?: inferMimeType(targetFile.name)

            val entity = DownloadedFileEntity(
                courseKey = courseKey,
                courseCode = courseCode,
                courseName = courseName,
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                mimeType = finalMime,
                sizeBytes = targetFile.length(),
                sourceUrl = url,
                downloadedAt = System.currentTimeMillis()
            )
            app.database.downloadedFileDao().insertFile(entity)

            _activeDownloads.value = _activeDownloads.value + (url to DownloadProgressInfo(url, targetFile.name, 100, true))
            UniTrackLogger.i("CourseDownloadManager: Successfully saved ${targetFile.name} (${targetFile.length()} bytes)")
            _snackbarEvents.emit(Pair("Downloaded '${targetFile.name}'", targetFile.absolutePath))
        } finally {
            inputStream?.close()
            outputStream?.close()
            response.close()
        }
    }

    private fun parseFileName(url: String, contentDisposition: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val filenameMatch = Regex("""filename\*?=(?:UTF-8''|")?([^";]+)""").find(contentDisposition)
            if (filenameMatch != null && filenameMatch.groupValues.size > 1) {
                return java.net.URLDecoder.decode(filenameMatch.groupValues[1].trim(), "UTF-8")
            }
        }
        val uriPath = try { java.net.URI(url).path } catch (e: Exception) { "" }
        val nameFromPath = uriPath.substringAfterLast('/')
        return if (nameFromPath.isNotBlank() && nameFromPath.contains('.')) {
            java.net.URLDecoder.decode(nameFromPath, "UTF-8")
        } else {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("""[/\\:*?"<>|]"""), "_").trim()
        return if (sanitized.length > 100) sanitized.take(100) else sanitized.ifBlank { "file" }
    }

    private fun resolveUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = fileName.substringBeforeLast('.')
        val ext = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""

        var count = 1
        while (file.exists()) {
            file = File(dir, "$nameWithoutExt ($count)$ext")
            count++
        }
        return file
    }

    private fun inferMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
