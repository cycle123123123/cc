package com.jmreader.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesParserTest {

    @Test
    fun `parses real series payload and sorts by sort`() {
        val raw = """
            [
              {"id":"598176","name":"","sort":"3"},
              {"id":"598174","name":"","sort":"1"},
              {"id":"598175","name":"","sort":"2"}
            ]
        """.trimIndent()

        val entries = SeriesParser.parse(Json.parseToJsonElement(raw))

        assertEquals(3, entries.size)
        assertEquals(listOf(598174L, 598175L, 598176L), entries.map { it.id })
        assertEquals(listOf("第 1 話", "第 2 話", "第 3 話"), entries.map { it.title })
    }

    @Test
    fun `prefers explicit name when present`() {
        val raw = """[{"id":"11","name":"序章","sort":"1"}]"""
        val entries = SeriesParser.parse(Json.parseToJsonElement(raw))
        assertEquals("序章", entries.single().title)
    }

    @Test
    fun `returns empty for non array series`() {
        assertEquals(0, SeriesParser.parse(Json.parseToJsonElement("0")).size)
        assertEquals(0, SeriesParser.parse(Json.parseToJsonElement("[]")).size)
    }
}
