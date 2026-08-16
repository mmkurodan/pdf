package com.micklab.pdf.domain.edit

/**
 * Minimal Arabic contextual shaper. pdfbox-android applies no OpenType shaping, so
 * Arabic must be converted to its presentation forms before drawing: each base letter
 * (U+0621–U+064A) is mapped to its isolated / initial / medial / final form according
 * to how it joins its neighbours, and the mandatory lam-alef pairs become ligatures.
 * Combining marks (harakat) are transparent for joining and passed through unchanged.
 *
 * The chosen font must carry the Arabic presentation forms (Noto Sans Arabic does).
 * Fully covers the standard Arabic block; letters outside it (some Persian/Urdu
 * extensions) simply pass through in their base form. Bidi reordering is separate —
 * see [RtlText].
 */
object ArabicShaper {

    private const val TATWEEL = 0x0640
    private const val LAM = 0x0644

    /** base code point -> [isolated, final, initial, medial]; 0 = no such form (right-joining). */
    private val FORMS: Map<Int, IntArray> = buildMap {
        put(0x0621, intArrayOf(0xFE80, 0, 0, 0))                 // HAMZA (non-joining)
        put(0x0622, intArrayOf(0xFE81, 0xFE82, 0, 0))            // ALEF MADDA (right-joining)
        put(0x0623, intArrayOf(0xFE83, 0xFE84, 0, 0))            // ALEF HAMZA ABOVE
        put(0x0624, intArrayOf(0xFE85, 0xFE86, 0, 0))            // WAW HAMZA
        put(0x0625, intArrayOf(0xFE87, 0xFE88, 0, 0))            // ALEF HAMZA BELOW
        put(0x0626, intArrayOf(0xFE89, 0xFE8A, 0xFE8B, 0xFE8C))  // YEH HAMZA (dual)
        put(0x0627, intArrayOf(0xFE8D, 0xFE8E, 0, 0))            // ALEF
        put(0x0628, intArrayOf(0xFE8F, 0xFE90, 0xFE91, 0xFE92))  // BEH
        put(0x0629, intArrayOf(0xFE93, 0xFE94, 0, 0))            // TEH MARBUTA
        put(0x062A, intArrayOf(0xFE95, 0xFE96, 0xFE97, 0xFE98))  // TEH
        put(0x062B, intArrayOf(0xFE99, 0xFE9A, 0xFE9B, 0xFE9C))  // THEH
        put(0x062C, intArrayOf(0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0))  // JEEM
        put(0x062D, intArrayOf(0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4))  // HAH
        put(0x062E, intArrayOf(0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8))  // KHAH
        put(0x062F, intArrayOf(0xFEA9, 0xFEAA, 0, 0))            // DAL
        put(0x0630, intArrayOf(0xFEAB, 0xFEAC, 0, 0))            // THAL
        put(0x0631, intArrayOf(0xFEAD, 0xFEAE, 0, 0))            // REH
        put(0x0632, intArrayOf(0xFEAF, 0xFEB0, 0, 0))            // ZAIN
        put(0x0633, intArrayOf(0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4))  // SEEN
        put(0x0634, intArrayOf(0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8))  // SHEEN
        put(0x0635, intArrayOf(0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC))  // SAD
        put(0x0636, intArrayOf(0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0))  // DAD
        put(0x0637, intArrayOf(0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4))  // TAH
        put(0x0638, intArrayOf(0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8))  // ZAH
        put(0x0639, intArrayOf(0xFEC9, 0xFECA, 0xFECB, 0xFECC))  // AIN
        put(0x063A, intArrayOf(0xFECD, 0xFECE, 0xFECF, 0xFED0))  // GHAIN
        put(TATWEEL, intArrayOf(0x0640, 0x0640, 0x0640, 0x0640)) // TATWEEL (join-causing)
        put(0x0641, intArrayOf(0xFED1, 0xFED2, 0xFED3, 0xFED4))  // FEH
        put(0x0642, intArrayOf(0xFED5, 0xFED6, 0xFED7, 0xFED8))  // QAF
        put(0x0643, intArrayOf(0xFED9, 0xFEDA, 0xFEDB, 0xFEDC))  // KAF
        put(0x0644, intArrayOf(0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0))  // LAM
        put(0x0645, intArrayOf(0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4))  // MEEM
        put(0x0646, intArrayOf(0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8))  // NOON
        put(0x0647, intArrayOf(0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC))  // HEH
        put(0x0648, intArrayOf(0xFEED, 0xFEEE, 0, 0))            // WAW
        put(0x0649, intArrayOf(0xFEEF, 0xFEF0, 0, 0))            // ALEF MAKSURA
        put(0x064A, intArrayOf(0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4))  // YEH
    }

    /** lam + alef variant -> [isolated ligature, final ligature]. */
    private val LAM_ALEF: Map<Int, IntArray> = mapOf(
        0x0622 to intArrayOf(0xFEF5, 0xFEF6),
        0x0623 to intArrayOf(0xFEF7, 0xFEF8),
        0x0625 to intArrayOf(0xFEF9, 0xFEFA),
        0x0627 to intArrayOf(0xFEFB, 0xFEFC),
    )

    /** Combining marks that don't participate in joining (skipped when finding neighbours). */
    private fun isTransparent(c: Int): Boolean =
        c in 0x0610..0x061A || c in 0x064B..0x065F || c == 0x0670 ||
            c in 0x06D6..0x06DC || c in 0x06DF..0x06E4 || c in 0x06E7..0x06E8 ||
            c in 0x06EA..0x06ED

    private fun canConnectRight(c: Int): Boolean = (FORMS[c]?.get(1) ?: 0) != 0 // has a final form
    private fun canConnectLeft(c: Int): Boolean = (FORMS[c]?.get(2) ?: 0) != 0  // has an initial form

    /** Returns [input] with Arabic letters replaced by their contextual presentation forms. */
    fun shape(input: String): String {
        if (input.isEmpty()) return input
        val cs = input.toCharArray()
        val sb = StringBuilder(cs.size)
        var i = 0
        while (i < cs.size) {
            if (cs[i].code == LAM) {
                val j = nextLetter(cs, i + 1)
                if (j != -1 && cs[j].code in LAM_ALEF) {
                    val lig = LAM_ALEF.getValue(cs[j].code)
                    sb.append((if (joinsPrev(cs, i)) lig[1] else lig[0]).toChar())
                    for (k in i + 1 until j) if (isTransparent(cs[k].code)) sb.append(cs[k])
                    i = j + 1
                    continue
                }
            }
            sb.append(formOf(cs, i).toChar())
            i++
        }
        return sb.toString()
    }

    private fun formOf(cs: CharArray, i: Int): Int {
        val forms = FORMS[cs[i].code] ?: return cs[i].code
        val jp = joinsPrev(cs, i)
        val jn = joinsNext(cs, i)
        return when {
            jp && jn && forms[3] != 0 -> forms[3] // medial
            jn && forms[2] != 0 -> forms[2]        // initial
            jp && forms[1] != 0 -> forms[1]        // final
            else -> forms[0]                        // isolated
        }
    }

    private fun joinsPrev(cs: CharArray, i: Int): Boolean {
        if (!canConnectRight(cs[i].code)) return false
        val p = prevLetter(cs, i - 1)
        return p != -1 && canConnectLeft(cs[p].code)
    }

    private fun joinsNext(cs: CharArray, i: Int): Boolean {
        if (!canConnectLeft(cs[i].code)) return false
        val q = nextLetter(cs, i + 1)
        return q != -1 && canConnectRight(cs[q].code)
    }

    private fun prevLetter(cs: CharArray, from: Int): Int {
        for (k in from downTo 0) if (!isTransparent(cs[k].code)) return k
        return -1
    }

    private fun nextLetter(cs: CharArray, from: Int): Int {
        for (k in from until cs.size) if (!isTransparent(cs[k].code)) return k
        return -1
    }
}
