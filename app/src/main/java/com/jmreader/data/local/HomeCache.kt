package com.jmreader.data.local

import com.jmreader.data.model.AlbumBrief
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 首页「最新上架」的本地快照：冷启动先垫一份旧数据，避免一直转圈。 */
internal class HomeCache(private val dir: File, private val json: Json) {

    private val file: File get() = File(dir, FILE_NAME)

    fun load(): List<AlbumBrief>? = runCatching {
        val f = file
        if (!f.exists()) return null
        json.decodeFromString(ListSerializer(AlbumBrief.serializer()), f.readText())
    }.getOrNull()

    fun save(list: List<AlbumBrief>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(AlbumBrief.serializer()), list))
        }
    }

    private companion object {
        const val FILE_NAME = "home_cache.json"
    }
}
