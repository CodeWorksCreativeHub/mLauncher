package com.github.codeworkscreativehub.fuzzywuzzy

import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.ContactListItem
import com.github.codeworkscreativehub.mlauncher.helper.emptyString
import java.text.Normalizer
import java.util.Locale

object FuzzyFinder {

    fun scoreApp(app: AppListItem, searchChars: String, topScore: Int): Int {
        val appLabel = app.label
        val normalizedAppLabel = normalizeString(appLabel)
        val normalizedSearchChars = normalizeString(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedAppLabel, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    fun scoreContact(contact: ContactListItem, searchChars: String, topScore: Int): Int {
        val contactLabel = contact.displayName
        val normalizedContactLabel = normalizeString(contactLabel)
        val normalizedSearchChars = normalizeString(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedContactLabel, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    fun scoreString(appLabel: String, searchChars: String, topScore: Int): Int {
        val normalizedAppLabel = normalizeString(appLabel)
        val normalizedSearchChars = normalizeString(searchChars)

        val fuzzyScore = calculateFuzzyScore(normalizedAppLabel, normalizedSearchChars)
        return (fuzzyScore * topScore).toInt()
    }

    // Simplified normalization for app label and search string
    private fun normalizeString(input: String): String {
        return input
            .uppercase(Locale.getDefault())
            .let { normalizeDiacritics(it) }
            .replace(Regex("[-_+,. ]"), emptyString())
    }

    // Remove diacritics from a string
    private fun normalizeDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), emptyString())
    }

    // Function to check if normalized strings match
    fun isMatch(appLabel: String, searchChars: String): Boolean {
        val normalizedAppLabel = normalizeString(appLabel)
        val normalizedSearchChars = normalizeString(searchChars)

        return normalizedAppLabel.contains(normalizedSearchChars, ignoreCase = true)
    }

    // Fuzzy matching logic (kept as it is)
    private fun calculateFuzzyScore(s1: String, s2: String): Float {
        val m = s1.length
        val n = s2.length
        var matchCount = 0
        var s1Index = 0

        // Iterate over each character in s2 and check if it exists in s1
        for (c2 in s2) {
            var found = false

            // Start searching for c2 from the current s1Index
            for (j in s1Index until m) {
                if (s1[j] == c2) {
                    found = true
                    s1Index = j + 1  // Move to the next position in s1
                    break
                }
            }

            // If the current character in s2 is not found in s1, return a score of 0
            if (!found) return 0f

            matchCount++
        }

        // Return score based on the ratio of matched characters to the longer string length
        return matchCount.toFloat() / maxOf(m, n)
    }
}