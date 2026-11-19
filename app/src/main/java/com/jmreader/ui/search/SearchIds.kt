package com.jmreader.ui.search

/** 「JM12345」/「jm 12345」/「12345」都算车号；4~7 位数字。 */
private val DIRECT_RE = Regex("""^\s*(?:[jJ][mM]\s*)?(\d{4,7})\s*$""")

/** 从任意文本里找车号：优先带 JM 前缀，其次作品链接 `/album/<id>`，最后整段就是 4~7 位数字。 */
private val PREFIXED_RE = Regex("""[jJ][mM]\s*(\d{4,7})""")
private val ALBUM_URL_RE = Regex("""/album/(\d{4,7})""")
private val BARE_RE = Regex("""^\s*(\d{4,7})\s*$""")

/** 解析搜索框内容为作品 id；不是车号返回 null。 */
internal fun parseJmId(text: String): Long? {
    val value = DIRECT_RE.matchEntire(text)?.groupValues?.get(1) ?: return null
    return value.toLongOrNull()?.takeIf { it > 0 }
}

/** 从剪贴板文本里提取车号（用于「剪貼簿檢測到 JMxxxx」提示）。 */
internal fun extractJmId(text: String): Long? {
    if (text.isBlank()) return null
    val prefixed = PREFIXED_RE.find(text)?.groupValues?.get(1)?.toLongOrNull()
    if (prefixed != null && prefixed > 0) return prefixed
    val fromUrl = ALBUM_URL_RE.find(text)?.groupValues?.get(1)?.toLongOrNull()
    if (fromUrl != null && fromUrl > 0) return fromUrl
    val bare = BARE_RE.matchEntire(text)?.groupValues?.get(1)?.toLongOrNull()
    return bare?.takeIf { it > 0 }
}
