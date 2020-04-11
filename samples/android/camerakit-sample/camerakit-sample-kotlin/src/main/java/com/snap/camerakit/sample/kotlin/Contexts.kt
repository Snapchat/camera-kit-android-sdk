@file:JvmName("Contexts")

package com.snap.camerakit.sample.kotlin

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.util.UUID

private const val MIME_TYPE_VIDEO_MP4 = "video/mp4"

/**
 * Inserts the provided [bitmap] to media store of external images and opens a sharing intent to the result image URI.
 */
internal fun Context.shareImageExternally(bitmap: Bitmap, title: String = UUID.randomUUID().toString()) {
    // Passing non-null title is necessary on Android 10 otherwise the following call will fail with:
    // `Failed to build unique file: /storage/emulated/0/Pictures Image image/jpeg`.
    val url = MediaStore.Images.Media.insertImage(contentResolver, bitmap, title, null)
    if (url != null) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(Intent.createChooser(intent, "Share image"))
    }
}

/**
 * Inserts the provided [file] to media store of external videos and opens a sharing intent to the result video URI.
 */
internal fun Context.shareVideoExternally(file: File) {
    val videoUri: Uri?
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.TITLE, file.name)
        put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE_VIDEO_MP4)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.apply {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraKit/")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        videoUri = contentResolver.insert(collection, values)?.also { uri ->
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 1)
                contentResolver.update(uri, values, null, null)
            }
        }
    } else {
        values.apply {
            put(MediaStore.Video.Media.DATA, file.absolutePath)
        }
        videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }
    if (videoUri != null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_VIDEO_MP4
            putExtra(Intent.EXTRA_STREAM, videoUri)
        }
        startActivity(Intent.createChooser(intent, "Share video"))
    }
}
