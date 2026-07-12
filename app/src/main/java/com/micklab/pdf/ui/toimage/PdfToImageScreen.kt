package com.micklab.pdf.ui.toimage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.model.ImageFormat
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.OutputFilesCard
import com.micklab.pdf.ui.common.OutputFolderSection
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.SelectablePageGrid
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.common.openOutput
import com.micklab.pdf.ui.common.shareOutputs
import com.micklab.pdf.ui.navigation.PdfDestination

@Composable
fun PdfToImageScreen(onBack: () -> Unit, viewModel: PdfToImageViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onOutputTreePicked(uri)
    }

    ToolScaffold(title = PdfDestination.PDF_TO_IMAGE.title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "入力 PDF") {
                Text(
                    if (ui.source == null) "PDF が選択されていません"
                    else "${ui.sourceName}（${ui.pageCount} ページ）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
                    Text("PDF を選択")
                }
            }

            if (ui.source != null) {
                SectionCard(title = "対象ページ（未選択で全ページ）") {
                    when {
                        ui.loadingThumbnails -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("プレビューを生成中…", style = MaterialTheme.typography.bodyMedium)
                        }

                        ui.thumbnails.isEmpty() -> Text(
                            "プレビューなし（全ページを画像化します）",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        else -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (ui.selectedPages.isEmpty()) "全ページ" else "${ui.selectedPages.size} ページ選択中",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row {
                                    TextButton(onClick = viewModel::selectAll) { Text("全選択") }
                                    TextButton(onClick = viewModel::clearSelection) { Text("解除") }
                                }
                            }
                            SelectablePageGrid(
                                pages = ui.thumbnails,
                                selected = ui.selectedPages,
                                onToggle = viewModel::togglePage,
                            )
                        }
                    }
                }
            }

            SectionCard(title = "画像化設定") {
                Text("解像度: ${ui.dpi} DPI", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = ui.dpi.toFloat(),
                    onValueChange = { viewModel.onDpiChanged(it.toInt()) },
                    valueRange = 72f..600f,
                )
                ChoiceChipsRow(
                    label = "形式",
                    options = ImageFormat.entries,
                    selected = ui.format,
                    optionLabel = { it.name },
                    onSelect = viewModel::onFormatChanged,
                )
                if (ui.format == ImageFormat.JPEG) {
                    Text("JPEG 品質: ${ui.jpegQuality}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = ui.jpegQuality.toFloat(),
                        onValueChange = { viewModel.onQualityChanged(it.toInt()) },
                        valueRange = 10f..100f,
                    )
                }
            }

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = "画像化する",
                enabled = ui.source != null,
                loading = op is OperationState.Running,
                onClick = viewModel::run,
            )

            OperationStatus(op)
            (op as? OperationState.Success)?.data?.let { files ->
                OutputFilesCard(
                    files = files,
                    onShareAll = { context.shareOutputs(files) },
                    onOpen = { context.openOutput(it) },
                )
            }
        }
    }
}
