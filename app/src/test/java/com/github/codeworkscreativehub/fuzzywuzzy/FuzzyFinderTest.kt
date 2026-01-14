package com.github.codeworkscreativehub.fuzzywuzzy

import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyFinderTest {

    @Test
    fun testAllFuzzyScenarios() {
        // Define your arguments here: Target, Query, and an optional Label for the printout
        val testData = listOf(
            Triple("Hello", "Hello", "Exact Match"),
            Triple("Hello", "xyz", "No Match"),
            Triple("Hello World", "Hello", "Partial Match"),
            Triple("application", "app", "Consecutive"),
            Triple("a_p_p_lication", "app", "Scattered"),
            Triple("Settings", "Set", "Prefix/Boundary"),
            Triple("Assets", "Set", "Infix"),
            Triple("mLauncher", "mL", "Acronym"),
            Triple("App Name", "App Name", "Ignore Spaces"),
            Triple("App-Name", "App Name", "Ignore Dash in Target")
        )

        println(String.format("\n%-20s | %-15s | %-10s | %s", "TEST CASE", "TARGET", "QUERY", "SCORE"))
        println("-".repeat(65))

        for ((target, query, label) in testData) {
            val score = FuzzyFinder.scoreString(target, query, 1000)
            val normalizedScore = score / 1000.0f

            // Print the result for every pair in the list
            println(String.format("%-20s | %-15s | %-10s | %.4f", label, target, query, normalizedScore))

            // Basic validation
            assertTrue("Score for $label should be >= 0", normalizedScore >= 0f)
        }
    }

    @Test
    fun testComparativeHeuristics() {
        // Keep these specific tests to ensure your logic rules stay intact
        val scoreConsecutive = FuzzyFinder.calculateFuzzyScore("application", "app")
        val scoreScattered = FuzzyFinder.calculateFuzzyScore("a_p_p_lication", "app")

        println("\nComparison Test: $scoreConsecutive (Consecutive) vs $scoreScattered (Scattered)")
        assertTrue("Consecutive match should score higher", scoreConsecutive > scoreScattered)
    }
}