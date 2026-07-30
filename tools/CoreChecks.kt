import com.tabdeck.app.data.TabExportCodec
import com.tabdeck.app.data.TabExportFormat
import com.tabdeck.app.engine.DedupeEngine
import com.tabdeck.app.engine.UrlExtractor
import com.tabdeck.app.engine.UrlNormalizer
import com.tabdeck.app.model.BrowserId
import com.tabdeck.app.model.DedupeMode
import com.tabdeck.app.model.KeepPolicy
import com.tabdeck.app.model.TabItem

private fun checkThat(condition: Boolean, message: String) {
    if (!condition) error(message)
}

fun main() {
    checkThat(UrlNormalizer.sanitizeWebUrl("https://example.com/a") != null, "valid HTTPS rejected")
    checkThat(UrlNormalizer.sanitizeWebUrl("www.example.com/a") == "https://www.example.com/a", "www promotion failed")
    checkThat(UrlNormalizer.sanitizeWebUrl("https://user:pass@example.com") == null, "credential URL accepted")
    checkThat(UrlNormalizer.sanitizeWebUrl("https://example.com:70000") == null, "out-of-range port accepted")
    checkThat(UrlNormalizer.sanitizeWebUrl("javascript:alert(1)") == null, "non-web scheme accepted")
    checkThat(UrlNormalizer.sanitizeWebUrl("https://example.com/\u0000x") == null, "control character accepted")

    val normalized = UrlNormalizer.normalized(
        "HTTPS://WWW.Example.com:443/a//b/?utm_source=x&b=2&a=hello+world#fragment",
    )
    checkThat(normalized == "https://example.com/a/b?a=hello%20world&b=2", "unexpected normalized URL: $normalized")

    val trackingPreserved = UrlNormalizer.normalized(
        "https://example.com/?utm_source=x&b=2",
        stripTrackingParameters = false,
    )
    checkThat(trackingPreserved.contains("utm_source=x"), "tracking parameter was not preserved")
    checkThat(UrlNormalizer.host("https://bücher.example/path") == "xn--bcher-kva.example", "IDN canonicalization failed")
    checkThat(UrlNormalizer.hostAndPath("https://www.example.com/a/?x=1") == "example.com/a", "host/path key failed")

    val extracted = UrlExtractor.extract(
        "Read [one](https://example.com/a_(b)). Then https://two.example/x?y=1&amp;z=2, and www.three.example/end."
    )
    checkThat(extracted.size == 3, "expected three extracted URLs, got $extracted")
    checkThat(extracted[0] == "https://example.com/a_(b)", "balanced parenthesis cleanup failed: ${extracted[0]}")
    checkThat(extracted[1] == "https://two.example/x?y=1&z=2", "HTML entity cleanup failed: ${extracted[1]}")
    checkThat(extracted[2] == "https://www.three.example/end", "terminal punctuation cleanup failed: ${extracted[2]}")

    val tabs = listOf(
        TabItem(
            id = "old",
            url = "https://www.example.com/page?utm_source=newsletter&a=1",
            title = "",
            importedAtEpochMs = 10,
            createdAtEpochMs = 5,
            sourceDevice = "phone-a",
            sourceTabId = "10",
        ),
        TabItem(
            id = "rich",
            url = "https://example.com/page?a=1#section",
            title = "Useful title",
            browser = BrowserId.FIREFOX_NIGHTLY,
            assignedGroup = "Reading",
            notes = "Keep this note",
            tags = setOf("research"),
            pinned = true,
            importedAtEpochMs = 20,
            createdAtEpochMs = 8,
            sourceDevice = "phone-a",
            sourceTabId = "11",
        ),
        TabItem(id = "other", url = "https://other.example/", importedAtEpochMs = 30),
    )
    val plan = DedupeEngine.plan(tabs, DedupeMode.NORMALIZED_URL, KeepPolicy.PINNED_FIRST, mergeMetadata = true)
    checkThat(plan.clusters.size == 1, "expected one duplicate cluster")
    checkThat(plan.duplicateIds == setOf("old"), "wrong duplicate selected: ${plan.duplicateIds}")
    val survivor = plan.mergedTabs.getValue("rich")
    checkThat(survivor.pinned && survivor.assignedGroup == "Reading", "survivor metadata lost")
    checkThat(survivor.createdAtEpochMs == 5L, "earliest creation time was not merged")
    checkThat(survivor.tags == setOf("research"), "tags were not preserved")

    val exact = DedupeEngine.clusters(tabs, DedupeMode.EXACT_URL)
    checkThat(exact.isEmpty(), "exact dedupe collapsed non-identical URLs")

    checkThat(BrowserId.fromWireName("Firefox Nightly") == BrowserId.FIREFOX_NIGHTLY, "browser wire mapping failed")
    checkThat(BrowserId.fromWireName("Brave Beta") == BrowserId.BRAVE_BETA, "browser beta mapping failed")

    val exportTabs = tabs + TabItem(id = "formula", url = "https://safe.example", title = "=HYPERLINK(\"bad\")", notes = "<private>")
    val csv = TabExportCodec.encode(exportTabs, TabExportFormat.CSV)
    checkThat(csv.contains("\"'=HYPERLINK"), "CSV formula injection was not neutralized")
    val html = TabExportCodec.encode(exportTabs, TabExportFormat.NETSCAPE_BOOKMARKS)
    checkThat(html.contains("&lt;private&gt;"), "bookmark HTML did not escape notes")
    checkThat(!html.contains("<private>"), "bookmark HTML contains unescaped markup")
    val markdown = TabExportCodec.encode(exportTabs, TabExportFormat.MARKDOWN)
    checkThat(markdown.contains("## Reading"), "Markdown groups were not preserved")

    println("TabDeck core checks passed")
}
