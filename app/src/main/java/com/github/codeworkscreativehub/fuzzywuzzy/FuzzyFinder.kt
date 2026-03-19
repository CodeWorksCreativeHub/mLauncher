package com.github.codeworkscreativehub.fuzzywuzzy

import android.content.Context
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.ContactListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.emptyString
import java.text.Normalizer
import java.util.Locale

object FuzzyFinder {

    /**
     * Scores an AppListItem based on its activity label.
     */
    fun scoreApp(context: Context, app: AppListItem, searchChars: String, topScore: Int): Int {
        val prefs = Prefs(context)
        val appLabel = prefs.getAppAlias(app.activityPackage)
            .takeIf { it.isNotBlank() }
            ?: app.activityLabel
        val normalizedAppLabel = normalizeTarget(appLabel)
        val normalizedSearchChars = normalizeSearch(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedAppLabel, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    /**
     * Scores a ContactListItem based on its display name.
     */
    fun scoreContact(contact: ContactListItem, searchChars: String, topScore: Int): Int {
        val contactLabel = contact.displayName
        val normalizedContactLabel = normalizeTarget(contactLabel)
        val normalizedSearchChars = normalizeSearch(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedContactLabel, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    /**
     * Scores a raw string against a search query.
     */
    fun scoreString(target: String, searchChars: String, topScore: Int): Int {
        val normalizedTarget = normalizeTarget(target)
        val normalizedSearchChars = normalizeSearch(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedTarget, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    /**
     * Advanced Fuzzy Matching Score.
     * Returns a float between 0.0 and 1.0.
     * 1.0 represents a perfect prefix match.
     */
    internal fun calculateFuzzyScore(haystack: String, needle: String): Float {
        if (needle.isEmpty() || haystack.isEmpty()) return 0f

        val n = needle.length
        val m = haystack.length
        if (n > m) return 0f

        val scoreMatch = 100
        val scoreConsecutive = 90
        val scoreWordStart = 80
        val scoreStartIdxBonus = 15

        var currentScore = 0
        var needleIdx = 0
        var prevHaystackIdx = -1

        // 1. Actual Score Loop
        for (i in 0 until m) {
            if (needleIdx >= n) break
            if (haystack[i] == needle[needleIdx]) {
                var charScore = scoreMatch
                if (prevHaystackIdx != -1 && i == prevHaystackIdx + 1) charScore += scoreConsecutive

                val isWordStart = if (i == 0) true else {
                    val prevChar = haystack[i - 1]
                    prevChar == ' ' || prevChar == '.' || prevChar == '_' || prevChar == '-' || prevChar == ','
                }
                if (isWordStart) charScore += scoreWordStart
                if (i < 3) charScore += scoreStartIdxBonus

                currentScore += charScore
                prevHaystackIdx = i
                needleIdx++
            }
        }

        if (needleIdx < n) return 0f

        // 2. Corrected Perfect Max Score
        // We only calculate the potential score for characters that ARE NOT spaces.
        var perfectMaxScore = 0
        for (i in 0 until m) {
            if (haystack[i] == ' ') continue // <--- SKIP spaces in the denominator!

            var potential = scoreMatch
            val isWordStart = if (i == 0) true else {
                val prevChar = haystack[i - 1]
                prevChar == ' ' || prevChar == '.' || prevChar == '_' || prevChar == '-' || prevChar == ','
            }

            potential += if (isWordStart) scoreWordStart else scoreConsecutive
            if (i < 3) potential += scoreStartIdxBonus

            perfectMaxScore += potential
        }

        return if (perfectMaxScore == 0) 0f else currentScore.toFloat() / perfectMaxScore
    }

    /**
     * Simple boolean match for legacy support or low-power filtering.
     */
    fun isMatch(target: String, searchChars: String): Boolean {
        val normalizedTarget = normalizeString(target)
        val normalizedSearch = normalizeString(searchChars)
        return normalizedTarget.contains(normalizedSearch)
    }

    // --- Normalization Helpers ---
    private fun normalizeString(input: String): String {
        return normalizeDiacritics(input.uppercase(Locale.getDefault()))
            .replace(Regex("[-_+,. ]"), emptyString())
    }

    private fun normalizeSearch(input: String): String {
        return normalizeDiacritics(input.uppercase(Locale.getDefault()))
            .replace(Regex("[-_+,. ]"), emptyString())
    }

    private fun normalizeTarget(input: String): String {
        return normalizeDiacritics(input.uppercase(Locale.getDefault()))
            .replace(Regex("[-_+,.]"), " ") // Keep spaces to detect word boundaries
    }

    private fun normalizeDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), emptyString())
    }

    /**
     * A generic helper to filter lists based on fuzzy scoring or simple matching.
     * T is the type of item (AppListItem or ContactListItem)
     */
    fun <T> filterItems(
        itemsList: List<T>,
        query: String,
        prefs: Prefs,
        scoreProvider: (T, String) -> Int,
        labelProvider: (T) -> String,
        loggerTag: String
    ): MutableList<T> {
        if (query.isEmpty()) return itemsList.toMutableList()

        return if (prefs.enableFilterStrength) {
            // 1. Calculate scores and filter by threshold
            itemsList.mapNotNull { item ->
                val score = scoreProvider(item, query)
                AppLogger.d(loggerTag, "item: ${labelProvider(item)} | score: $score")
                if (score > prefs.filterStrength) item else null
            }.toMutableList()
        } else {
            // 2. Simple Boolean matching
            itemsList.filter { item ->
                val target = labelProvider(item).lowercase()
                if (prefs.searchFromStart) {
                    target.startsWith(query)
                } else {
                    isMatch(target, query)
                }
            }.toMutableList()
        }
    }
}