package com.tabdeck.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractorTest {
    @Test
    fun trimsSentenceAndMarkdownPunctuationWithoutBreakingBalancedPaths() {
        val text = "Read [one](https://example.com/article). Keep https://example.com/wiki/Foo_(bar) too!"
        assertEquals(
            listOf("https://example.com/article", "https://example.com/wiki/Foo_(bar)"),
            UrlExtractor.extract(text),
        )
    }

    @Test
    fun decodesHtmlAmpersandsAndAddsSchemeForWww() {
        assertEquals(
            listOf("https://example.com/?a=1&b=2", "https://www.example.org/path"),
            UrlExtractor.extract("https://example.com/?a=1&amp;b=2 www.example.org/path"),
        )
    }
}
