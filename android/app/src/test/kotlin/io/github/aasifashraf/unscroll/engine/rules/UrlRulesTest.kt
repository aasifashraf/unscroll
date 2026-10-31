package io.github.aasifashraf.unscroll.engine.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {
    private val sites = setOf("reddit.com", "news.example")
    private val keywords = setOf("shorts")

    @Test
    fun matchesTheSiteAndItsSubdomainsOnly() {
        assertTrue(UrlRules.isBlocked("reddit.com", sites, emptySet()))
        assertTrue(UrlRules.isBlocked("https://www.reddit.com/r/all", sites, emptySet()))
        assertTrue(UrlRules.isBlocked("old.reddit.com/top", sites, emptySet()))
        assertFalse(UrlRules.isBlocked("notreddit.com", sites, emptySet()))
        assertFalse(UrlRules.isBlocked("reddit.com.evil.example", sites, emptySet()))
    }

    @Test
    fun keywordsMatchAnywhereIgnoringCase() {
        assertTrue(UrlRules.isBlocked("m.youtube.com/SHORTS/abc", emptySet(), keywords))
        assertFalse(UrlRules.isBlocked("m.youtube.com/watch?v=1", emptySet(), keywords))
    }

    @Test
    fun searchTextAndBlankInputAreNotUrls() {
        assertFalse(UrlRules.isBlocked("how to cook rice", sites, emptySet()))
        assertFalse(UrlRules.isBlocked("", sites, keywords))
    }

    @Test
    fun hostExtractionHandlesPortsUserInfoAndCase() {
        assertEquals("reddit.com", UrlRules.hostOf("HTTPS://user@Reddit.com:443/x"))
        assertNull(UrlRules.hostOf("not a url"))
    }

    @Test
    fun normalizeSiteInputFromTheUser() {
        assertEquals("reddit.com", UrlRules.normalizeSite(" https://www.Reddit.com/r/all "))
        assertNull(UrlRules.normalizeSite("no spaces allowed.com x"))
        assertNull(UrlRules.normalizeSite("localhost"))
    }
}
