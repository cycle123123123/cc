package com.jmreader.data.local

import android.content.Context

/**
 * `jmreader_reader` 的读写封装（文件名、key、默认值、类型全部保持原样）。
 *
 * 阅读器里的进度、书签、阅读模式、亮度、翻页动画、RTL、放大点击、方向锁定都走这里，
 * UI 层不再直接持有 SharedPreferences。
 */
class ReaderPrefs private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jmreader_reader", Context.MODE_PRIVATE)

    var brightness: Float
        get() = prefs.getFloat("brightness", -1f)
        set(value) = prefs.edit().putFloat("brightness", value).apply()

    var animate: Boolean
        get() = prefs.getBoolean("animate", true)
        set(value) = prefs.edit().putBoolean("animate", value).apply()

    var rtl: Boolean
        get() = prefs.getBoolean("rtl", false)
        set(value) = prefs.edit().putBoolean("rtl", value).apply()

    var tapWhenZoomed: Boolean
        get() = prefs.getBoolean("tap_when_zoomed", false)
        set(value) = prefs.edit().putBoolean("tap_when_zoomed", value).apply()

    var lockOrientation: Boolean
        get() = prefs.getBoolean("lock_orientation", true)
        set(value) = prefs.edit().putBoolean("lock_orientation", value).apply()

    /** 阅读模式：`vertical`（默认）/ `horizontal`。 */
    var vertical: Boolean
        get() = prefs.getString("mode", "vertical") != "horizontal"
        set(value) = prefs.edit()
            .putString("mode", if (value) "vertical" else "horizontal")
            .apply()

    fun progress(cid: Long): Int = prefs.getInt("progress_$cid", 0).coerceAtLeast(0)

    fun saveProgress(cid: Long, page: Int) {
        prefs.edit().putInt("progress_$cid", page).apply()
    }

    /**
     * 书签只保留一条：`page == null` 表示移除。
     * 旧版本用 `bookmarks_$cid` 存逗号分隔的多值，读取时兼容并只取最后一个。
     */
    fun bookmark(cid: Long): Int? {
        if (prefs.contains("bookmark_$cid")) {
            val page = prefs.getInt("bookmark_$cid", -1)
            return page.takeIf { it >= 0 }
        }
        val legacy = prefs.getString("bookmarks_$cid", null) ?: return null
        return legacy.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .lastOrNull()
            ?.takeIf { it >= 0 }
    }

    fun saveBookmark(cid: Long, page: Int?) {
        val editor = prefs.edit()
        if (page == null) editor.remove("bookmark_$cid") else editor.putInt("bookmark_$cid", page)
        // 清掉舊版多值格式，避免被兼容逻辑复活
        editor.remove("bookmarks_$cid")
        editor.apply()
    }

    companion object {
        @Volatile
        private var instance: ReaderPrefs? = null

        fun of(context: Context): ReaderPrefs =
            instance ?: synchronized(this) {
                instance ?: ReaderPrefs(context.applicationContext).also { instance = it }
            }
    }
}

/** `jmreader` 里 UI 层用到的零散配置。 */
class AppPrefs private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jmreader", Context.MODE_PRIVATE)

    /** 用户点过「知道了」的协议版本号，空串表示没点过。 */
    var dismissedProtocol: String
        get() = prefs.getString("dismissed_protocol", null).orEmpty()
        set(value) = prefs.edit().putString("dismissed_protocol", value).apply()

    companion object {
        @Volatile
        private var instance: AppPrefs? = null

        fun of(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context.applicationContext).also { instance = it }
            }
    }
}
