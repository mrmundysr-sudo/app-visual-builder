package com.personal.appvisualbuilder.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Share the zip via the Android share sheet (Drive, email, etc.). */
fun shareZip(context: Context, zip: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share visual assets"))
}

/** Save the zip into the public Downloads folder (Android 10+). Older versions: use Share. */
fun saveZipToDownloads(context: Context, zip: File) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Toast.makeText(context, "Use Share on this Android version", Toast.LENGTH_LONG).show()
        return
    }
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, zip.name)
        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        Toast.makeText(context, "Couldn't save to Downloads", Toast.LENGTH_LONG).show()
        return
    }
    resolver.openOutputStream(uri)?.use { out -> zip.inputStream().use { it.copyTo(out) } }
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    Toast.makeText(context, "Saved ${zip.name} to Downloads", Toast.LENGTH_LONG).show()
}
