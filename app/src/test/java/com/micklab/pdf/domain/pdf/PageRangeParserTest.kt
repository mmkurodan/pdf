package com.micklab.pdf.domain.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun blankSelectsAllPages() {
        assertThat(PageRangeParser.parse("", pageCount = 3))
            .containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun keywordAllSelectsEverything() {
        assertThat(PageRangeParser.parse("all", pageCount = 2))
            .containsExactly(0, 1).inOrder()
    }

    @Test
    fun mixedSingleAndRange() {
        // 1-based "1-3,5" over a 10-page doc -> 0-based [0,1,2,4]
        assertThat(PageRangeParser.parse("1-3,5", pageCount = 10))
            .containsExactly(0, 1, 2, 4).inOrder()
    }

    @Test
    fun openEndedRangeGoesToLastPage() {
        assertThat(PageRangeParser.parse("8-", pageCount = 10))
            .containsExactly(7, 8, 9).inOrder()
    }

    @Test
    fun outOfRangeValuesAreDropped() {
        // "0" (invalid), "5" -> index 4, "99" (invalid)
        assertThat(PageRangeParser.parse("0,5,99", pageCount = 10))
            .containsExactly(4)
    }

    @Test
    fun descendingRangeIsHonored() {
        assertThat(PageRangeParser.parse("3-1", pageCount = 5))
            .containsExactly(2, 1, 0).inOrder()
    }

    @Test
    fun duplicatesAreRemovedPreservingFirstSeenOrder() {
        assertThat(PageRangeParser.parse("2,2,1", pageCount = 5))
            .containsExactly(1, 0).inOrder()
    }

    @Test
    fun emptyWhenNoPages() {
        assertThat(PageRangeParser.parse("1-3", pageCount = 0)).isEmpty()
    }
}
