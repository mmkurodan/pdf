package com.micklab.pdf.ui.home

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
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.micklab.pdf.ui.navigation.PdfDestination

private data class ToolEntry(
    val destination: PdfDestination,
    val icon: ImageVector,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenTool: (PdfDestination) -> Unit) {
    val tools = listOf(
        ToolEntry(PdfDestination.SPLIT, Icons.Default.ContentCut, "選択したページを取り出して新しい PDF を作成"),
        ToolEntry(PdfDestination.MERGE, Icons.AutoMirrored.Filled.MergeType, "複数の PDF を 1 つに結合"),
        ToolEntry(PdfDestination.REORDER, Icons.Default.SwapVert, "ページ順序を入れ替え"),
        ToolEntry(PdfDestination.PDF_TO_IMAGE, Icons.Default.Image, "各ページを PNG / JPEG に変換（DPI 指定可）"),
        ToolEntry(PdfDestination.IMAGE_TO_PDF, Icons.Default.PictureAsPdf, "複数画像を順序指定して 1 つの PDF に"),
        ToolEntry(PdfDestination.OCR, Icons.Default.DocumentScanner, "埋め込みテキストと OCR を区別して JSON 出力"),
    )

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("PDF ツールキット")
                    Text(
                        "端末内で完結する PDF・画像・OCR 処理",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            items(tools) { tool ->
                ToolCard(tool = tool, onClick = { onOpenTool(tool.destination) })
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(tool.destination.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
