@file:JvmName("Contexts")

package com.snap.camerakit.sample.kotlin

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * Inserts the provided [bitmap] to media store of external images and opens a sharing intent to the result image URI.
 */
internal fun Context.shareImageExternally(bitmap: Bitmap) {
    val url = MediaStore.Images.Media.insertImage(contentResolver, bitmap, null, null)
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
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.TITLE, file.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.DATA, file.absolutePath)
    }
    val uri: Uri? = this.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    if (uri != null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = values.getAsString(MediaStore.Video.Media.MIME_TYPE)
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(Intent.createChooser(intent, "Share video"))
    }
}
