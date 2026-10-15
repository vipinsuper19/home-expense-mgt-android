package com.dhama.homefinance.util

/** Integer-paise helpers. No Double ever touches money. */
object Money {
    private val pattern = Regex("""^\d{1,13}(\.\d{0,2})?$""")

    /**
     * Rupee part with commas only as thousands separators:
     * none ("1500"), international ("1,500", "1,234,567") or Indian ("1,23,456").
     */
    private val groupedRupees = Regex("""^(\d+|\d{1,3}(,\d{3})+|\d{1,2}(,\d{2})+,\d{3})$""")

    /**
     * Parses a user-entered amount like "1,250.5" or "₹ 99" into paise.
     * Returns null if invalid, including zero ("0", "0.00"): amounts must be positive.
     * Misplaced commas ("1,5", "15,00") are invalid rather than silently dropped.
     */
    fun parseRupeesToPaise(input: String): Long? {
        val raw = input.trim().removePrefix("₹").trim()
        val rupeePart = raw.substringBefore('.')
        if (raw.substringAfter('.', "").contains(',')) return null
        if (rupeePart.contains(',') && !groupedRupees.matches(rupeePart)) return null
        val cleaned = raw.replace(",", "")
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

    /**
     * Unit rate implied by an amount and a quantity (thousandths): amount ÷ quantity,
     * rounded half-up to the nearest paisa. The stored amount stays the source of truth
     * (PRD: amount = quantity × rate; rate is derived, never the other way round).
     */
    fun unitRatePaise(amountPaise: Long, quantityMilli: Long): Long {
        require(amountPaise > 0 && quantityMilli > 0)
        return java.math.BigDecimal.valueOf(amountPaise)
            .multiply(java.math.BigDecimal.valueOf(1000))
            .divide(java.math.BigDecimal.valueOf(quantityMilli), 0, java.math.RoundingMode.HALF_UP)
            .longValueExact()
    }
}
