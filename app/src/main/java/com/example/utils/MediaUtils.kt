package com.example.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class MediaFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String
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

    val formatted = when {
        size >= 1024 * 1024 -> String.format("%.2f MB", size.toDouble() / (1024 * 1024))
        size >= 1024 -> String.format("%.1f KB", size.toDouble() / 1024)
        size > 0 -> "$size B"
        else -> "Ready to upload"
    }

    return MediaFileInfo(uri, name, size, formatted)
}
