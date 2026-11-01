package com.example.util

import java.util.Locale

/** Converts API enum-like values into readable labels without changing API values. */
object DisplayFormatter {
    fun label(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return ""

        return when (raw.uppercase(Locale.ENGLISH)) {
            "IN_PERSON" -> "In Person"
            "ONLINE" -> "Online"
            "HYBRID", "IN_PERSON + ONLINE", "IN_PERSON+ONLINE" -> "In Person + Online"
            "MAKE_UP_LECTURE" -> "Make-up Lecture"
            "MAKE_UP_PRACTICAL" -> "Make-up Practical"
            "GROUP_ALL" -> "All Groups"
            else -> raw
                .replace('_', ' ')
                .split(Regex("\\s+"))
                .joinToString(" ") { word ->
                    word.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
                }
        }
    }

    fun group(value: String?): String {
        val formatted = label(value)
        return if (formatted.equals("All", ignoreCase = true)) "All Groups" else formatted
    }
}
