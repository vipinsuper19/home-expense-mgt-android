package com.dhama.homefinance.util

/** Integer-paise helpers. No Double ever touches money. */
object Money {
    private val pattern = Regex("""^\d{1,13}(\.\d{0,2})?$""")

    /**
     * Parses a user-entered amount like "1,250.5" or "₹ 99" into paise.
     * Returns null if invalid, including zero ("0", "0.00"): amounts must be positive.
     */
    fun parseRupeesToPaise(input: String): Long? {
        val cleaned = input.trim().removePrefix("₹").trim().replace(",", "")
        if (!pattern.matches(cleaned)) return null
        val parts = cleaned.split('.')
        val rupees = parts[0].toLong()
        val fraction = parts.getOrNull(1).orEmpty().padEnd(2, '0')
        val paise = Math.addExact(Math.multiplyExact(rupees, 100L), fraction.toLong())
        return paise.takeIf { it > 0 }
    }

    /** Formats paise in Indian digit grouping, e.g. 12345678 -> "₹1,23,456.78". */
    fun formatPaise(paise: Long, withSymbol: Boolean = true): String {
        val negative = paise < 0
        val abs = if (negative) -paise else paise
        val rupees = (abs / 100).toString()
        val fraction = (abs % 100).toString().padStart(2, '0')
        val grouped = if (rupees.length <= 3) rupees else {
            val head = rupees.dropLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + rupees.takeLast(3)
        }
        return buildString {
            if (negative) append('-')
            if (withSymbol) append('₹')
            append(grouped).append('.').append(fraction)
        }
    }
}
