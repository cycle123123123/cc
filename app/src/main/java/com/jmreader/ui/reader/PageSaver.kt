package com.jmreader.ui.reader

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/**
 * 把已还原（分条顺序正确）的整页位图写进系统相册。
 *
 * - Android 10+：写 `Pictures/JMReader`，用 `IS_PENDING` 保证相册里不会出现半张图，无需存储权限；
 * - Android 9 及以下：走传统 `MediaStore.Images.Media.insert`，需要写外部存储权限，
 *   没授权时返回失败（调用方提示即可，不额外拉起权限弹窗）。
 */
internal suspend fun savePageToGallery(
    context: Context,
    bitmap: Bitmap,
    cid: Long,
    page: Int,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val name = "JM_${cid}_p$page.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/JMReader",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: error("無法建立相冊項目")
        try {
            val out = resolver.openOutputStream(uri) ?: error("無法開啟寫入流")
            out.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    error("圖片寫入失敗")
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }
    }
}
