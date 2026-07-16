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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
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

private data class ToolCategory(val title: String, val tools: List<ToolEntry>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenTool: (PdfDestination) -> Unit) {
    val categories = listOf(
        ToolCategory(
            "PDF 作成と編集",
            listOf(
                ToolEntry(PdfDestination.EDIT, Icons.Default.Edit, "PDF を選ぶか白紙から新規作成し、テキスト・画像を配置／既存の文字を編集"),
            ),
        ),
        ToolCategory(
            "PDF 変換と構成",
            listOf(
                ToolEntry(PdfDestination.SPLIT, Icons.Default.ContentCut, "選択したページを取り出して新しい PDF を作成"),
                ToolEntry(PdfDestination.MERGE, Icons.AutoMirrored.Filled.MergeType, "複数の PDF を 1 つに結合"),
                ToolEntry(PdfDestination.REORDER, Icons.Default.SwapVert, "ページ順序を入れ替え"),
                ToolEntry(PdfDestination.PDF_TO_IMAGE, Icons.Default.Image, "各ページを PNG / JPEG に変換（DPI 指定可）"),
                ToolEntry(PdfDestination.IMAGE_TO_PDF, Icons.Default.PictureAsPdf, "複数画像を順序指定して 1 つの PDF に"),
            ),
        ),
        ToolCategory(
            "OCR / AI-OCR",
            listOf(
                ToolEntry(PdfDestination.OCR, Icons.Default.DocumentScanner, "埋め込みテキストと OCR を区別して JSON 出力"),
                ToolEntry(PdfDestination.SUMMARY, Icons.Default.Summarize, "ファイル全体・ページごとを LLM で要約（OCR→LLM / Vision）"),
            ),
        ),
        ToolCategory(
            "環境設定",
            listOf(
                ToolEntry(PdfDestination.OCR_SETTINGS, Icons.Default.Settings, "OCR モデル取得（Tesseract / PaddleOCR）と LLM 接続の設定"),
                ToolEntry(PdfDestination.MANUAL, Icons.AutoMirrored.Filled.MenuBook, "各機能の使い方"),
                ToolEntry(PdfDestination.PRIVACY, Icons.Default.PrivacyTip, "データの扱いと通信について"),
                ToolEntry(PdfDestination.LICENSES, Icons.Default.Copyright, "オープンソース・フォントの権利表記"),
            ),
        ),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            categories.forEach { category ->
                item(key = category.title) {
                    Text(
                        category.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(category.tools, key = { it.destination.route }) { tool ->
                    ToolCard(tool = tool, onClick = { onOpenTool(tool.destination) })
                }
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
