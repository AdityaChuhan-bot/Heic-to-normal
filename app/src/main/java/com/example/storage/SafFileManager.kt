package com.example.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.model.MediaSourceItem
import com.example.model.MediaType
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.DecimalFormat

object SafFileManager {

    fun queryMediaItem(context: Context, uri: Uri, forcedType: MediaType? = null): MediaSourceItem {
        val resolver = context.contentResolver
        var displayName = "file_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            displayName = name
                        }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
            // Safe fallback if cursor query fails
        }

        if (sizeBytes <= 0) {
            try {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    sizeBytes = pfd.statSize
                }
            } catch (_: Exception) {
                // Ignore fallback size read
            }
        }

        val detectedMime = resolver.getType(uri) ?: guessMimeFromName(displayName)
        val mediaType = forcedType ?: when {
            detectedMime.startsWith("video/") || displayName.endsWith(".mov", ignoreCase = true) -> MediaType.VIDEO
            else -> MediaType.PHOTO
        }

        return MediaSourceItem(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = detectedMime,
            mediaType = mediaType
        )
    }

    private fun guessMimeFromName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".heic") -> "image/heic"
            lower.endsWith(".heif") -> "image/heif"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    fun prepareOutputFile(
        context: Context,
        treeUri: Uri?,
        baseName: String,
        targetExtension: String,
        targetMimeType: String
    ): Pair<Uri, String> {
        val cleanBaseName = baseName.substringBeforeLast('.')
        val initialName = "$cleanBaseName.$targetExtension"

        if (treeUri != null) {
            try {
                val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                if (pickedDir != null && pickedDir.canWrite()) {
                    // Handle duplicate filename safely
                    val uniqueName = resolveUniqueFileName(pickedDir, cleanBaseName, targetExtension)
                    val newDoc = pickedDir.createFile(targetMimeType, uniqueName)
                    if (newDoc != null) {
                        return Pair(newDoc.uri, newDoc.name ?: uniqueName)
                    }
                }
            } catch (_: Exception) {
                // Fallback to internal/external storage on failure
            }
        }

        // Fallback to app-dedicated storage folder
        val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ConvertedMedia").apply {
            if (!exists()) mkdirs()
        }
        val uniqueFile = resolveUniqueLocalFile(outputDir, cleanBaseName, targetExtension)
        return Pair(Uri.fromFile(uniqueFile), uniqueFile.name)
    }

    private fun resolveUniqueFileName(dir: DocumentFile, base: String, ext: String): String {
        var candidate = "$base.$ext"
        var counter = 1
        while (dir.findFile(candidate) != null) {
            candidate = "$base ($counter).$ext"
            counter++
        }
        return candidate
    }

    private fun resolveUniqueLocalFile(dir: File, base: String, ext: String): File {
        var file = File(dir, "$base.$ext")
        var counter = 1
        while (file.exists()) {
            file = File(dir, "$base ($counter).$ext")
            counter++
        }
        return file
    }

    fun openOutputStream(context: Context, uri: Uri): OutputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).outputStream() }
        } else {
            context.contentResolver.openOutputStream(uri)
        }
    }

    fun openInputStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).inputStream() }
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups]
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String, fileName: String? = null) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            fileName?.let { putExtra(Intent.EXTRA_TITLE, it) }
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Converted Media"))
    }

    fun openFile(context: Context, uri: Uri, mimeType: String) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(viewIntent)
        } catch (_: Exception) {
            // If no app can open directly, fallback to share
            shareFile(context, uri, mimeType)
        }
    }
}
