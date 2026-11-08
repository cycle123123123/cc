package com.jmreader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {

    private fun matcher(
        keywords: List<String> = emptyList(),
        authors: List<String> = emptyList(),
        albums: List<String> = emptyList(),
        hideAlbums: Boolean = true,
        hideAuthors: Boolean = false,
        favoritedAlbums: List<String> = emptyList(),
        favoritedAuthors: List<String> = emptyList(),
    ) = BlockRules(
        keywords = keywords,
        authors = authors,
        albums = albums,
        hideFavoritedAlbums = hideAlbums,
        hideFavoritedAuthors = hideAuthors,
        favoritedAlbums = favoritedAlbums,
        favoritedAuthors = favoritedAuthors,
    ).matcher

    @Test
    fun `empty rules match nothing`() {
        assertFalse(matcher().matches("1", "标题", "作者", null, emptyList()))
    }

    @Test
    fun `keyword matches title author category and tags`() {
        val m = matcher(keywords = listOf("ntr"))
        assertTrue(m.matches("1", "Some NTR Story", "a", null, emptyList()))
        assertTrue(m.matches("1", "标题", "NTR author", null, emptyList()))
        assertTrue(m.matches("1", "标题", "作者", "NTR", emptyList()))
        assertTrue(m.matches("1", "标题", "作者", null, listOf("NTR")))
        assertFalse(m.matches("1", "纯爱", "作者", "恋爱", emptyList()))
    }

    @Test
    fun `blocked author matches by substring`() {
        val m = matcher(authors = listOf("水龍敬"))
        assertTrue(m.matches("1", "标题", "水龍敬", null, emptyList()))
        assertFalse(m.matches("1", "标题", "别的作者", null, emptyList()))
    }

    @Test
    fun `blocked album id wins`() {
        assertTrue(matcher(albums = listOf("123")).matches("123", "标题", "", null, emptyList()))
        assertFalse(matcher(albums = listOf("123")).matches("124", "标题", "", null, emptyList()))
    }

    @Test
    fun `favorited album is hidden only when the toggle is on`() {
        assertTrue(
            matcher(favoritedAlbums = listOf("9")).matches("9", "标题", "", null, emptyList()),
        )
        assertFalse(
            matcher(hideAlbums = false, favoritedAlbums = listOf("9"))
                .matches("9", "标题", "", null, emptyList()),
        )
    }

    @Test
    fun `favorited author matching respects the toggle`() {
        val on = matcher(hideAuthors = true, favoritedAuthors = listOf("作者A"))
        assertTrue(on.matches("1", "标题", "作者A", null, emptyList()))

        val off = matcher(hideAuthors = false, favoritedAuthors = listOf("作者A"))
        assertFalse(off.matches("1", "标题", "作者A", null, emptyList()))
    }

    @Test
    fun `matcher is cached per rules instance`() {
        val rules = BlockRules(keywords = listOf("x"))
        assertTrue(rules.matcher === rules.matcher)
    }
}
