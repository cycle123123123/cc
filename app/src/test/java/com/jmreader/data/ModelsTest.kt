package com.jmreader.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun `favorite total is a string`() {
        val raw = """
            {"list":[{"id":"1215913","name":"x","author":"a","category":{"id":"5","title":"韓漫"}}],
             "folder_list":[],"total":"1163","count":20}
        """.trimIndent()
        val page = json.decodeFromString(FavoritePage.serializer(), raw)
        assertEquals("1163", page.total)
        assertEquals(1, page.list.size)
        assertEquals("1215913", page.list[0].id)
    }

    @Test
    fun `watch list total is a number`() {
        val raw = """{"list":[{"id":"569509","name":"x"}],"total":19}"""
        val page = json.decodeFromString(HistoryPage.serializer(), raw)
        assertEquals(19, page.total)
        assertEquals("569509", page.list[0].id)
    }

    @Test
    fun `search content is used instead of list`() {
        val raw = """{"total":48,"content":[{"id":"1487787","name":"x"}]}"""
        val page = json.decodeFromString(SearchPage.serializer(), raw)
        assertEquals(48, page.total)
        assertEquals("1487787", page.content[0].id)
    }

    @Test
    fun `member ignores unstable coin field`() {
        val raw = """{"uid":"1","username":"u","s":"sess","jwttoken":"jwt","album_favorites":3,"level":6,"coin":"7468"}"""
        val member = json.decodeFromString(Member.serializer(), raw)
        assertEquals("jwt", member.jwttoken)
        assertEquals(3, member.totalFavorites)
    }
}
