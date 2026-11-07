package com.jmreader.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * 章节图纵向分条倒序还原。
 * 详见 docs/API.md §6，算法源自官方前端 5412 模块的 wR()。
 */
object ImageDescrambler {

    fun segmentCount(aid: Long, key: String): Int = ScrambleParams.segmentCount(aid, key)

    /**
     * @param aid        作品 id
     * @param scrambleId 接口返回的 scramble_id
     * @param key        图片文件名去掉扩展名，例如 "00001"
     */
    fun decode(src: Bitmap, aid: Long, scrambleId: Long, key: String): Bitmap {
        if (scrambleId <= 0L || aid < scrambleId) return src

        val segments = segmentCount(aid, key)
        if (segments <= 1) return src

        val width = src.width
        val height = src.height
        if (height / segments <= 0) return src

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        var dstTop = 0
        for (strip in ScrambleParams.stripPlan(height, segments)) {
            canvas.drawBitmap(
                src,
                Rect(0, strip.srcTop, width, strip.srcTop + strip.height),
                Rect(0, dstTop, width, dstTop + strip.height),
                paint,
            )
            dstTop += strip.height
        }
        return out
    }
}
