package com.tabdeck.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {
    @Test
    fun removesTrackingFragmentsAndTrivialDifferences() {
        val a = "https://www.Example.com/path/?utm_source=newsletter&b=2&a=1#section"
        val b = "https://example.com/path?a=1&b=2"
        assertEquals(UrlNormalizer.normalized(b), UrlNormalizer.normalized(a))
    }

    @Test
    fun canPreserveTrackingParametersWhenRequested() {
        val tracked = UrlNormalizer.normalized(
            "https://example.com/path?utm_source=newsletter&id=7",
            stripTrackingParameters = false,
        )
        assertTrue("utm_source" in tracked)
        assertFalse("#" in tracked)
    }

    @Test
    fun hostAndPathIgnoresQuery() {
        assertEquals(
            "example.com/article",
            UrlNormalizer.hostAndPath("https://www.example.com/article/?variant=a"),
        )
    }

    @Test
    fun validatesOnlyCompleteHttpUrls() {
        assertEquals("https://www.example.com/a", UrlNormalizer.sanitizeWebUrl("www.example.com/a"))
        assertNull(UrlNormalizer.sanitizeWebUrl("javascript:alert(1)"))
        assertNull(UrlNormalizer.sanitizeWebUrl("https://"))
        assertNull(UrlNormalizer.sanitizeWebUrl("https://exa mple.com"))
    }

    @Test
    fun rejectsCredentialsAndMalformedPorts() {
        assertNull(UrlNormalizer.sanitizeWebUrl("https://user:password@example.com/private"))
        assertNull(UrlNormalizer.sanitizeWebUrl("https://example.com:notaport/path"))
        assertNull(UrlNormalizer.sanitizeWebUrl("https://example.com:70000/path"))
        assertNull(UrlNormalizer.sanitizeWebUrl("https://exa_mple.com/path"))
    }

    @Test
    fun acceptsValidCustomAndIpv6Ports() {
        assertEquals("https://example.com:8443/path", UrlNormalizer.sanitizeWebUrl("https://example.com:8443/path"))
        assertEquals("http://[::1]:9222/json", UrlNormalizer.sanitizeWebUrl("http://[::1]:9222/json"))
    }
}
