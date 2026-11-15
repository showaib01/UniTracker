package com.example.ui.screens.course

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.UniTrackApplication
import com.example.data.local.entity.DownloadedFileEntity
import com.example.ui.components.AppBackground
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.theme.UniTrackTheme
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CourseFolderSummary(
    val folderName: String,
    val folderDir: File,
    val fileCount: Int,
    val totalSizeBytes: Long
)

@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as UniTrackApplication
    val scope = rememberCoroutineScope()
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography
    val dimens = UniTrackTheme.dimens

    val roomFilesFlow = remember { app.database.downloadedFileDao().getAllFiles() }
    val roomFiles by roomFilesFlow.collectAsState(initial = emptyList())

    var selectedFolder by remember { mutableStateOf<CourseFolderSummary?>(null) }
    var fileToDelete by remember { mutableStateOf<DownloadedFileEntity?>(null) }

    // Reconcile disk files with Room rows on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dbFiles = app.database.downloadedFileDao().getAllFilesDirect()
            for (f in dbFiles) {
                if (!File(f.filePath).exists()) {
                    app.database.downloadedFileDao().deleteFile(f)
                }
            }
        }
    }

    // Base root dir
    val coursesBaseDir = remember {
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UniTrack/Courses")
    }

    // Group disk folders & Room files
    val folderSummaries = remember(roomFiles) {
        val dirs = coursesBaseDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        dirs.map { dir ->
            val diskFiles = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            val totalSize = diskFiles.sumOf { it.length() }
            CourseFolderSummary(
                folderName = dir.name,
                folderDir = dir,
                fileCount = diskFiles.size,
                totalSizeBytes = totalSize
            )
        }.filter { it.fileCount > 0 }
    }

    // Dialog for deleting a file
    fileToDelete?.let { fileEntity ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            containerColor = colors.surfaceSheet,
            title = { Text("Delete File?", style = typography.sectionTitle, color = colors.textPrimary) },
            text = { Text("Are you sure you want to delete '${fileEntity.fileName}'?", style = typography.body, color = colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = File(fileEntity.filePath)
                                if (file.exists()) file.delete()
                                app.database.downloadedFileDao().deleteFile(fileEntity)
                            } catch (e: Exception) {
                                // Handled
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                                fileToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusDanger, contentColor = colors.textPrimary)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    AppBackground(modifier = modifier.testTag("downloads_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.ScreenPadding, vertical = dimens.Space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selectedFolder != null) {
                            selectedFolder = null
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.size(48.dp).testTag("downloads_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(dimens.Space8))
                Column {
                    Text(
                        text = selectedFolder?.folderName ?: "My Downloads",
                        style = typography.screenTitle,
                        color = colors.textPrimary
                    )
                    if (selectedFolder != null) {
                        Text(
                            text = "Course folder",
                            style = typography.meta,
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Body
            val currentFolder = selectedFolder
            if (currentFolder == null) {
                // LEVEL 1: List of Course Folders
                if (folderSummaries.isEmpty()) {
                    EmptyState(
                        title = "No downloaded files",
                        message = "Downloaded course materials and files will appear here offline.",
                        icon = Icons.Outlined.FolderOpen,
                        modifier = Modifier.padding(dimens.ScreenPadding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = dimens.ScreenPadding,
                            vertical = dimens.Space8
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimens.Space12)
                    ) {
                        items(folderSummaries, key = { it.folderName }) { folder ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_folder_${folder.folderName}"),
                                onClick = { selectedFolder = folder }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = { selectedFolder = folder },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(dimens.BoxRadius))
                                                .background(colors.accentCyan.copy(alpha = 0.12f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Folder,
                                                contentDescription = null,
                                                tint = colors.accentCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(dimens.Space12))
                                        Column {
                                            Text(
                                                text = folder.folderName,
                                                style = typography.body,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "${folder.fileCount} files • ${formatFileSize(folder.totalSizeBytes)}",
                                                style = typography.meta,
                                                color = colors.textSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = "Open folder",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // LEVEL 2: Files in Selected Course Folder
                val folderFiles = remember(roomFiles, currentFolder) {
                    roomFiles.filter { File(it.filePath).parentFile?.name == currentFolder.folderName }
                }

                if (folderFiles.isEmpty()) {
                    EmptyState(
                        title = "Folder is empty",
                        message = "No downloaded files found in ${currentFolder.folderName}.",
                        icon = Icons.Outlined.FolderOpen,
                        modifier = Modifier.padding(dimens.ScreenPadding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = dimens.ScreenPadding,
                            vertical = dimens.Space8
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimens.Space12)
                    ) {
                        items(folderFiles, key = { it.id }) { fileEntity ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_file_${fileEntity.id}"),
                                onClick = { openFileWithIntent(context, fileEntity) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = { openFileWithIntent(context, fileEntity) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(dimens.BoxRadius))
                                                .background(colors.accentCyan.copy(alpha = 0.12f))
                                        ) {
                                            Icon(
                                                imageVector = iconForMimeType(fileEntity.mimeType),
                                                contentDescription = null,
                                                tint = colors.accentCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(dimens.Space12))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = fileEntity.fileName,
                                                style = typography.body,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${formatFileSize(fileEntity.sizeBytes)} • ${DateTimeUtils.formatDateHeadline(fileEntity.downloadedAt)}",
                                                style = typography.meta,
                                                color = colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { shareFileWithIntent(context, fileEntity) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = "Share File",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { fileToDelete = fileEntity },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Delete File",
                                                tint = colors.statusDanger,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openFileWithIntent(context: Context, entity: DownloadedFileEntity) {
    try {
        val file = File(entity.filePath)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, entity.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open File"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFileWithIntent(context: Context, entity: DownloadedFileEntity) {
    try {
        val file = File(entity.filePath)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = entity.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share file", Toast.LENGTH_SHORT).show()
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kbs = bytes / 1024.0
    if (kbs < 1024) return String.format("%.1f KB", kbs)
    val mbs = kbs / 1024.0
    return String.format("%.1f MB", mbs)
}

private fun iconForMimeType(mimeType: String): ImageVector {
    return when {
        mimeType.contains("pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
        mimeType.contains("image", ignoreCase = true) -> Icons.Outlined.Description
        mimeType.contains("word", ignoreCase = true) || mimeType.contains("document", ignoreCase = true) -> Icons.Outlined.Description
        else -> Icons.Outlined.Description
    }
}
