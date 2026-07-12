package com.micklab.pdf.domain.pdf

/**
 * Parses human page selections like `"1-3, 5, 8-"` into **0-based** page indices.
 *
 * Rules:
 *  - Blank / "all" / "*" selects every page.
 *  - Ranges are inclusive; `"8-"` means page 8 to the end.
 *  - Values are 1-based in the input, clamped to `1..pageCount`.
 *  - Result is de-duplicated but preserves first-seen order (so it can also
 *    express a reordering when used for that purpose).
 */
object PageRangeParser {

    fun parse(spec: String, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        val trimmed = spec.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true) || trimmed == "*") {
            return (0 until pageCount).toList()
        }

        val result = LinkedHashSet<Int>()
        for (rawToken in trimmed.split(',')) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue

            if (token.contains('-')) {
                val parts = token.split('-', limit = 2)
                val start = parts[0].trim().toIntOrNull() ?: 1
                val end = parts[1].trim().toIntOrNull() ?: pageCount
                val lo = start.coerceIn(1, pageCount)
                val hi = end.coerceIn(1, pageCount)
                val step = if (lo <= hi) 1 else -1
                var p = lo
                while (true) {
                    result.add(p - 1)
                    if (p == hi) break
                    p += step
                }
            } else {
                val single = token.toIntOrNull() ?: continue
                if (single in 1..pageCount) result.add(single - 1)
            }
        }
        return result.toList()
    }
}
