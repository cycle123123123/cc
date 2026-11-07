package com.jmreader.core

/**
 * 章节图分条参数。纯 JVM 实现，便于单元测试。
 * 算法见 docs/API.md §6.2。
 */
object ScrambleParams {

    private val SEGMENT_TABLE = intArrayOf(2, 4, 6, 8, 10, 12, 14, 16, 18, 20)

    /** 目标图从上到下的一条：从源图 srcTop 开始，取 height 高。 */
    data class Strip(val srcTop: Int, val height: Int)

    /**
     * @param aid 作品 id（数值）
     * @param key 图片文件名去掉扩展名，例如 "00001"
     */
    fun segmentCount(aid: Long, key: String): Int {
        val lastChar = JmCrypto.md5(aid.toString() + key).last()
        var r = lastChar.code
        when {
            aid in JmConfig.SCRAMBLE_RANGE_START..JmConfig.SCRAMBLE_RANGE_END -> r %= 10
            aid >= JmConfig.SCRAMBLE_LARGE_AID -> r %= 8
        }
        return if (r in 0..9) SEGMENT_TABLE[r] else 10
    }

    /**
     * 目标图的分条顺序：源图从下往上取。
     * 源图最后一条多出 remainder 像素，倒序后成为目标图的第一条。
     */
    fun stripPlan(height: Int, segments: Int): List<Strip> {
        if (segments <= 1 || height <= 0) return listOf(Strip(0, height))
        val strip = height / segments
        if (strip <= 0) return listOf(Strip(0, height))
        val remainder = height % segments
        return (0 until segments).map { i ->
            val srcIndex = segments - 1 - i
            val srcTop = srcIndex * strip
            val h = if (srcIndex == segments - 1) strip + remainder else strip
            Strip(srcTop, h)
        }
    }
}
