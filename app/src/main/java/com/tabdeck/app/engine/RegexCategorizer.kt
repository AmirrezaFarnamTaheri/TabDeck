package com.tabdeck.app.engine

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import com.tabdeck.app.model.RegexRule
import com.tabdeck.app.model.RegexTarget
import com.tabdeck.app.model.TabItem

/** Linear-time regular-expression categorization backed by RE2/J. */
object RegexCategorizer {
    private const val MAX_PATTERN_LENGTH = 512
    private const val MAX_RULES = 250

    data class RuleValidation(val valid: Boolean, val error: String = "")
    data class CompiledRule(val rule: RegexRule, val pattern: Pattern)

    fun validate(rule: RegexRule): RuleValidation {
        if (rule.name.isBlank()) return RuleValidation(false, "Rule name is required")
        if (rule.destinationGroup.isBlank()) return RuleValidation(false, "Destination group is required")
        if (rule.pattern.isBlank()) return RuleValidation(false, "Pattern is required")
        if (rule.pattern.length > MAX_PATTERN_LENGTH) {
            return RuleValidation(false, "Pattern exceeds $MAX_PATTERN_LENGTH characters")
        }
        return try {
            compile(rule)
            RuleValidation(true)
        } catch (error: PatternSyntaxException) {
            RuleValidation(false, error.message.orEmpty().ifBlank { "Unsupported or invalid RE2 expression" })
        }
    }

    fun compileEnabled(rules: List<RegexRule>): List<CompiledRule> = rules.asSequence()
        .filter { it.enabled }
        .sortedBy { it.priority }
        .take(MAX_RULES)
        .mapNotNull { rule -> runCatching { CompiledRule(rule, compile(rule)) }.getOrNull() }
        .toList()

    fun categorize(tab: TabItem, rules: List<RegexRule>): TabItem = categorizeCompiled(tab, compileEnabled(rules))

    internal fun categorizeCompiled(tab: TabItem, rules: List<CompiledRule>): TabItem {
        var result = tab
        for (compiled in rules) {
            if (!matches(result, compiled)) continue
            result = result.copy(
                assignedGroup = compiled.rule.destinationGroup.ifBlank { result.assignedGroup },
                tags = result.tags + compiled.rule.addTags.map(String::trim).filter(String::isNotBlank),
            )
            if (compiled.rule.stopAfterMatch) break
        }
        return result
    }

    fun categorizeAll(tabs: List<TabItem>, rules: List<RegexRule>): List<TabItem> {
        val compiled = compileEnabled(rules)
        return tabs.map { categorizeCompiled(it, compiled) }
    }

    fun matches(tab: TabItem, rule: RegexRule): Boolean =
        runCatching { matches(tab, CompiledRule(rule, compile(rule))) }.getOrDefault(false)

    private fun matches(tab: TabItem, compiled: CompiledRule): Boolean {
        fun contains(value: String): Boolean = compiled.pattern.matcher(value).find()
        return when (compiled.rule.target) {
            RegexTarget.ANY -> contains(tab.url) || contains(tab.title) || contains(UrlNormalizer.host(tab.url)) || contains(tab.sourceGroup)
            RegexTarget.URL -> contains(tab.url)
            RegexTarget.TITLE -> contains(tab.title)
            RegexTarget.HOST -> contains(UrlNormalizer.host(tab.url))
            RegexTarget.SOURCE_GROUP -> contains(tab.sourceGroup)
        }
    }

    private fun compile(rule: RegexRule): Pattern {
        val flags = if (rule.ignoreCase) Pattern.CASE_INSENSITIVE else 0
        return Pattern.compile(rule.pattern, flags)
    }
}
