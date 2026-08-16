package com.micklab.pdf.domain.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppFontTest {

    @Test
    fun `matches PostScript names we embed`() {
        assertThat(AppFont.byEmbeddedName("NotoSansJP-Regular")).isEqualTo(AppFont.NOTO_SANS_JP)
        assertThat(AppFont.byEmbeddedName("NotoSerifJP-Regular")).isEqualTo(AppFont.NOTO_SERIF_JP)
        assertThat(AppFont.byEmbeddedName("MPLUSRounded1c-Regular")).isEqualTo(AppFont.MPLUS_ROUNDED_1C)
        assertThat(AppFont.byEmbeddedName("ZenKakuGothicNew-Regular")).isEqualTo(AppFont.ZEN_KAKU_GOTHIC_NEW)
        assertThat(AppFont.byEmbeddedName("KleeOne-Regular")).isEqualTo(AppFont.KLEE_ONE)
    }

    @Test
    fun `matches through the 6-char subset prefix`() {
        assertThat(AppFont.byEmbeddedName("ABCDEF+NotoSerifJP-Regular")).isEqualTo(AppFont.NOTO_SERIF_JP)
        assertThat(AppFont.byEmbeddedName("BCDEFG+MPLUSRounded1c")).isEqualTo(AppFont.MPLUS_ROUNDED_1C)
    }

    @Test
    fun `matches variable-font name variants`() {
        // Variable fonts may report the family without the -Regular style suffix.
        assertThat(AppFont.byEmbeddedName("NotoSansJP")).isEqualTo(AppFont.NOTO_SANS_JP)
        assertThat(AppFont.byEmbeddedName("NotoSansJP[wght]")).isEqualTo(AppFont.NOTO_SANS_JP)
    }

    @Test
    fun `does not confuse the two Noto families`() {
        assertThat(AppFont.byEmbeddedName("NotoSansJP-Bold")).isEqualTo(AppFont.NOTO_SANS_JP)
        assertThat(AppFont.byEmbeddedName("NotoSerifJP-Bold")).isEqualTo(AppFont.NOTO_SERIF_JP)
    }

    @Test
    fun `broad Noto Sans does not shadow the regional or script families`() {
        // "Noto Sans" (key "notosans") is a prefix of every "Noto Sans X" key, so the
        // longest-match rule must keep the specific families winning.
        assertThat(AppFont.byEmbeddedName("NotoSans")).isEqualTo(AppFont.NOTO_SANS)
        assertThat(AppFont.byEmbeddedName("NotoSans[wdth,wght]")).isEqualTo(AppFont.NOTO_SANS)
        assertThat(AppFont.byEmbeddedName("NotoSansJP")).isEqualTo(AppFont.NOTO_SANS_JP)
        assertThat(AppFont.byEmbeddedName("NotoSansSC-Regular")).isEqualTo(AppFont.NOTO_SANS_SC)
        assertThat(AppFont.byEmbeddedName("NotoSansTC")).isEqualTo(AppFont.NOTO_SANS_TC)
        assertThat(AppFont.byEmbeddedName("NotoSansKR")).isEqualTo(AppFont.NOTO_SANS_KR)
        assertThat(AppFont.byEmbeddedName("NotoSansArabic")).isEqualTo(AppFont.NOTO_SANS_ARABIC)
        assertThat(AppFont.byEmbeddedName("NotoSansHebrew")).isEqualTo(AppFont.NOTO_SANS_HEBREW)
        assertThat(AppFont.byEmbeddedName("ABCDEF+NotoSansMath-Regular")).isEqualTo(AppFont.NOTO_SANS_MATH)
    }

    @Test
    fun `returns null for foreign or blank fonts`() {
        assertThat(AppFont.byEmbeddedName("Helvetica")).isNull()
        assertThat(AppFont.byEmbeddedName("ABCDEF+Times-Roman")).isNull()
        assertThat(AppFont.byEmbeddedName("")).isNull()
        assertThat(AppFont.byEmbeddedName(null)).isNull()
    }
}
