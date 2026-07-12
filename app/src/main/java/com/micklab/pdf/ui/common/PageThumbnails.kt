package com.micklab.pdf.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.micklab.pdf.domain.usecase.PageBitmap

private val DefaultThumbWidth = 92.dp

/**
 * A single page preview: the rendered image, its page number, an optional
 * selection ring/checkmark, and an optional corner [badge] (e.g. order position).
 */
@Composable
fun PageThumb(
    page: PageBitmap,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = DefaultThumbWidth,
    badge: String? = null,
) {
    val image = remember(page) { page.bitmap.asImageBitmap() }
    val ratio = (page.bitmap.width.toFloat() / page.bitmap.height.coerceAtLeast(1)).coerceIn(0.2f, 5f)
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Image(
                bitmap = image,
                contentDescription = "ページ ${page.index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(width)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(6.dp))
                    .border(if (selected) 2.dp else 1.dp, ringColor, RoundedCornerShape(6.dp))
                    .clickable { onClick() },
            )
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "選択済み",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp),
                )
            }
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(bottomEnd = 6.dp, topStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        badge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(
            "${page.index + 1}",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

/** A wrapping grid of tap-to-select page thumbnails (non-lazy; safe inside a scroll column). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectablePageGrid(
    pages: List<PageBitmap>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pages.forEach { page ->
            PageThumb(
                page = page,
                selected = page.index in selected,
                onClick = { onToggle(page.index) },
            )
        }
    }
}
