package com.atqiyu.douyinkit.ui.downloads

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.atqiyu.douyinkit.download.SavedFile

fun openSavedFile(context: Context, file: SavedFile) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.onFailure {
        Toast.makeText(context, "没有可以打开该文件的应用", Toast.LENGTH_SHORT).show()
    }
}

fun shareSavedFile(context: Context, file: SavedFile) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, file.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享文件"))
    }.onFailure {
        Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
    }
}
