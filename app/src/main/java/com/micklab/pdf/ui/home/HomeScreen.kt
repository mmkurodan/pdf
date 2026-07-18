package com.micklab.pdf.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.micklab.pdf.R
import com.micklab.pdf.ui.common.AdmobBanner
import com.micklab.pdf.ui.navigation.PdfDestination

private data class ToolEntry(
    val destination: PdfDestination,
    val icon: ImageVector,
    @StringRes val descRes: Int,
)

private data class ToolCategory(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    @StringRes val descRes: Int,
    val tools: List<ToolEntry>,
)

private val CATEGORIES = listOf(
    ToolCategory(
        R.string.cat_create, Icons.Default.Edit, R.string.cat_create_desc,
        listOf(ToolEntry(PdfDestination.EDIT, Icons.Default.Edit, R.string.tool_edit_desc)),
    ),
    ToolCategory(
        R.string.cat_convert, Icons.Default.Transform, R.string.cat_convert_desc,
        listOf(
            ToolEntry(PdfDestination.SPLIT, Icons.Default.ContentCut, R.string.tool_split_desc),
            ToolEntry(PdfDestination.MERGE, Icons.AutoMirrored.Filled.MergeType, R.string.tool_merge_desc),
            ToolEntry(PdfDestination.REORDER, Icons.Default.SwapVert, R.string.tool_reorder_desc),
            ToolEntry(PdfDestination.PDF_TO_IMAGE, Icons.Default.Image, R.string.tool_pdf_to_image_desc),
            ToolEntry(PdfDestination.IMAGE_TO_PDF, Icons.Default.PictureAsPdf, R.string.tool_image_to_pdf_desc),
        ),
    ),
    ToolCategory(
        R.string.cat_ocr, Icons.Default.DocumentScanner, R.string.cat_ocr_desc,
        listOf(
            ToolEntry(PdfDestination.OCR, Icons.Default.DocumentScanner, R.string.tool_ocr_desc),
            ToolEntry(PdfDestination.SUMMARY, Icons.Default.Summarize, R.string.tool_summary_desc),
            ToolEntry(PdfDestination.PROMPT, Icons.Default.AutoAwesome, R.string.tool_prompt_desc),
        ),
    ),
    ToolCategory(
        R.string.cat_settings, Icons.Default.Tune, R.string.cat_settings_desc,
        listOf(
            ToolEntry(PdfDestination.OCR_SETTINGS, Icons.Default.Settings, R.string.tool_ocr_settings_desc),
            ToolEntry(PdfDestination.LANGUAGE, Icons.Default.Language, R.string.tool_language_desc),
            ToolEntry(PdfDestination.MANUAL, Icons.AutoMirrored.Filled.MenuBook, R.string.tool_manual_desc),
            ToolEntry(PdfDestination.PRIVACY, Icons.Default.PrivacyTip, R.string.tool_privacy_desc),
            ToolEntry(PdfDestination.LICENSES, Icons.Default.Copyright, R.string.tool_licenses_desc),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenTool: (PdfDestination) -> Unit) {
    var selected by remember { mutableStateOf<ToolCategory?>(null) }
    val current = selected
    if (current != null) BackHandler { selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (current != null) stringResource(current.titleRes) else stringResource(R.string.home_title))
                        Text(
                            if (current != null) stringResource(current.descRes) else stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { selected = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.home_back_categories),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = { AdmobBanner() },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            if (current == null) {
                items(CATEGORIES, key = { it.titleRes }) { category ->
                    HomeTile(
                        icon = category.icon,
                        title = stringResource(category.titleRes),
                        description = stringResource(category.descRes),
                        onClick = { selected = category },
                    )
                }
            } else {
                items(current.tools, key = { it.destination.route }) { tool ->
                    HomeTile(
                        icon = tool.icon,
                        title = stringResource(tool.destination.titleRes),
                        description = stringResource(tool.descRes),
                        onClick = { onOpenTool(tool.destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTile(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
