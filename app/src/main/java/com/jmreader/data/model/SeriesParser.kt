package com.jmreader.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * /album 与 /chapter 返回的 series 字段：
 * [{"id":"598174","name":"","sort":"1"}, ...]
 *
 * 多话作品里每个元素本身就是一个独立 album id；
 * name 通常为空，标题要用 sort 生成。
 */
object SeriesParser {

    fun parse(series: JsonElement): List<ChapterEntry> {
        val array = series as? JsonArray ?: return emptyList()

        val entries = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val sort = obj["sort"]?.jsonPrimitive?.intOrNull
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ChapterEntry(id = id, title = name.orEmpty(), sort = sort)
        }.sortedBy { it.sort ?: Int.MAX_VALUE }

        // 服务端 sort 可能重复（实测前两话都是 1），标题按顺序编号更可靠
        return entries.mapIndexed { index, entry ->
            if (entry.title.isNotBlank()) entry else entry.copy(title = "第 ${index + 1} 話")
        }
    }
}
