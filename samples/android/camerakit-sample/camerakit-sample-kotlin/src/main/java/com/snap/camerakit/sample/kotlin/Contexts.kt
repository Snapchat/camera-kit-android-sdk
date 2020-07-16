@file:JvmName("Contexts")

package com.snap.camerakit.sample.kotlin

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.UUID

/**
 * Shares the provided [uri] of expected video/image [mimeType] via an [Intent] with application chooser.
 */
internal fun Context.shareExternally(uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    val title = when (mimeType) {
        MIME_TYPE_IMAGE_JPEG -> getString(R.string.share_image)
        MIME_TYPE_VIDEO_MP4 -> getString(R.string.share_video)
        else -> throw IllegalArgumentException("Unexpected media [$uri] with type [$mimeType]")
    }
    startActivity(Intent.createChooser(intent, title))
}

/**
 * Attempts to insert the provided [file] of the specified [mimeType] to [MediaStore].
 */
internal fun Context.tryInsertToMediaStore(file: File, mimeType: String): Uri? {
    val insertedUri: Uri?
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.TITLE, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val relativePath = when (mimeType) {
            MIME_TYPE_IMAGE_JPEG -> "${Environment.DIRECTORY_PICTURES}/CameraKit"
            MIME_TYPE_VIDEO_MP4 -> "${Environment.DIRECTORY_MOVIES}/CameraKit"
            else -> throw IllegalArgumentException(
                "Cannot determine relative path for media [$file] with type [$mimeType]")
        }
        val collection = when (mimeType) {
            MIME_TYPE_IMAGE_JPEG -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MIME_TYPE_VIDEO_MP4 -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> throw IllegalArgumentException(
                "Cannot determine collection for media [$file] with type [$mimeType]")
        }
        values.apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        insertedUri = contentResolver.insert(collection, values)
    } else {
        values.apply {
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }
        insertedUri = when (mimeType) {
            MIME_TYPE_IMAGE_JPEG -> contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            MIME_TYPE_VIDEO_MP4 -> contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            else -> throw IllegalArgumentException("Cannot insert media [$file] with type [$mimeType]")
        }
    }
    insertedUri?.also { uri ->
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            values.clear()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            }
        }
    }

    return insertedUri
}

/**
 * Saves the provided [bitmap] as a jpeg file to application's cache directory.
 */
internal fun Context.cacheJpegOf(bitmap: Bitmap): File {
    return File(cacheDir, "${UUID.randomUUID()}.jpg").also {
        it.outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }
    }
}
