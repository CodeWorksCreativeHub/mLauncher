package com.github.codeworkscreativehub.mlauncher.helper

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.github.codeworkscreativehub.mlauncher.data.Constants
import java.util.Locale

object ChineseSortHelper {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private val platformSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Returns a pinyin-based sort key only when the app language is Chinese, or follows a
     * Chinese system language, and the platform supports ICU transliteration.
     * Callers should fall back to the original label.
     */
    fun sortKey(label: String, language: Constants.Language): String? {
        if (!shouldUseChineseSort(language) || !label.startsWithChinese()) return null

        return transliterate(label)
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)
    }

    /**
     * Returns a pinyin-based section letter only when the app language is Chinese, or follows a
     * Chinese system language, and the platform supports ICU transliteration.
     * Callers should fall back to the label's first char.
     */
    fun sectionKey(label: String, language: Constants.Language): String? {
        if (!shouldUseChineseSort(language)) return null
        val firstChar = label.firstOrNull()?.takeIf(::isChinese) ?: return null

        return transliterate(firstChar.toString())
            ?.firstOrNull { it.isLetter() }
            ?.uppercaseChar()
            ?.takeIf { it in 'A'..'Z' }
            ?.toString()
    }

    private fun transliterate(label: String): String? {
        if (!platformSupported) return null

        return runCatching {
            Transliterator.getInstance("Han-Latin; Latin-ASCII").transliterate(label)
        }.getOrNull()
    }

    private fun shouldUseChineseSort(language: Constants.Language): Boolean =
        platformSupported && when (language) {
            Constants.Language.Chinese -> true
            Constants.Language.System -> Locale.getDefault().language == Locale.CHINESE.language
            else -> false
        }

    private fun String.startsWithChinese(): Boolean =
        firstOrNull()?.let(::isChinese) == true

    private fun isChinese(ch: Char): Boolean =
        ch in '\u4E00'..'\u9FFF'
}
