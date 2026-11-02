package com.shapeshed.kiosk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteFilterTest {

    private fun story(title: String, url: String?) =
        Story(1, title, url, "u", 0, 0, 0, emptyList(), null)

    @Test
    fun domainInputIsNormalized() {
        assertEquals("example.com", normalizeMutedDomain(" https://www.Example.com/post/1 "))
        assertEquals("example.com", normalizeMutedDomain("example.com"))
        assertNull(normalizeMutedDomain("   "))
        assertNull(normalizeMutedDomain("not a domain"))
    }

    @Test
    fun mutedDomainCoversSubdomains() {
        val muted = setOf("example.com")
        assertTrue(isMuted(story("t", "https://blog.example.com/a"), muted, emptySet()))
        assertTrue(isMuted(story("t", "https://www.example.com/a"), muted, emptySet()))
        assertFalse(isMuted(story("t", "https://notexample.com/a"), muted, emptySet()))
    }

    @Test
    fun keywordsMatchWholeWordsIgnoringCase() {
        val muted = setOf("go")
        assertTrue(isMuted(story("Go 1.24 released", null), emptySet(), muted))
        assertTrue(isMuted(story("Why I left go.", null), emptySet(), muted))
        assertFalse(isMuted(story("Google ships a thing", null), emptySet(), muted))
    }

    @Test
    fun keywordsWithSymbolsStillMatch() {
        assertTrue(isMuted(story("Modern C++ tips", null), emptySet(), setOf("c++")))
    }

    @Test
    fun textPostsWithoutUrlOnlyCheckKeywords() {
        assertFalse(isMuted(story("Ask HN: anything", null), setOf("example.com"), emptySet()))
    }
}
