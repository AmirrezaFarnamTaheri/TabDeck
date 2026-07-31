package com.tabdeck.app.engine

import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.RegexTarget
import com.tabdeck.app.model.TabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexCategorizerTest {
    @Test
    fun firstPriorityMatchWins() {
        val rules = listOf(
            RegexRule(name = "Docs", pattern = "developer\\.android\\.com", destinationGroup = "Android", priority = 1),
            RegexRule(name = "Development", pattern = "developer", destinationGroup = "Development", priority = 2),
        )
        val result = RegexCategorizer.categorize(
            TabItem(url = "https://developer.android.com/guide"),
            rules,
        )
        assertEquals("Android", result.assignedGroup)
    }

    @Test
    fun validatesPatternsAndTargetsHost() {
        val valid = RegexRule(name = "GitHub", pattern = "(^|\\.)github\\.com$", target = RegexTarget.HOST, destinationGroup = "Code")
        val invalid = valid.copy(pattern = "[")
        assertTrue(RegexCategorizer.validate(valid).valid)
        assertFalse(RegexCategorizer.validate(invalid).valid)
        assertTrue(RegexCategorizer.matches(TabItem(url = "https://github.com/openai"), valid))
    }
    @Test
    fun evaluatesRulesBeyondTheFormerRuleCeiling() {
        val rules = (0 until 250).map { index ->
            RegexRule(name = "Miss $index", pattern = "never-match-$index", destinationGroup = "Miss", priority = index)
        } + RegexRule(name = "Final", pattern = "example\\.com", destinationGroup = "Found", priority = 250)

        val result = RegexCategorizer.categorize(TabItem(url = "https://example.com"), rules)

        assertEquals("Found", result.assignedGroup)
    }

    @Test
    fun rejectsOversizedPatternsWithoutLimitingRuleCount() {
        val oversized = RegexRule(
            name = "Oversized",
            pattern = "a".repeat(4_097),
            destinationGroup = "Rejected",
        )

        assertFalse(RegexCategorizer.validate(oversized).valid)
    }

}
