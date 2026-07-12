package com.micklab.pdf.ui.common

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable

/** A grid item that spans the whole row (used for headers/footers in a page grid). */
fun LazyGridScope.fullSpanItem(content: @Composable LazyGridItemScope.() -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }, content = content)
}
