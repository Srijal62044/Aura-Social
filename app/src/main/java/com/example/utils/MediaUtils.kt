package com.example.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class MediaFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val persistentPath: String = uri.toString()
)

fun getMediaFileInfo(context: Context, uri: Uri): MediaFileInfo {
    var name = "Selected_Media"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: "Selected_Media"
                }
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val persistentPath = copyUriToInternalStorage(context, uri)

    val formatted = when {
        size >= 1024 * 1024 -> String.format("%.2f MB", size.toDouble() / (1024 * 1024))
        size >= 1024 -> String.format("%.1f KB", size.toDouble() / 1024)
        size > 0 -> "$size B"
        else -> "Ready to upload"
    }

    return MediaFileInfo(uri, name, size, formatted, persistentPath)
}

fun copyUriToInternalStorage(context: Context, uri: Uri): String {
    return try {
        val extension = context.contentResolver.getType(uri)?.let { type ->
            if (type.contains("video")) "mp4" else "jpg"
        } ?: "jpg"
        val file = File(context.filesDir, "media_${System.currentTimeMillis()}_${(1000..9999).random()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (file.exists() && file.length() > 0) {
            file.absolutePath
        } else {
            uri.toString()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        uri.toString()
    }
}
