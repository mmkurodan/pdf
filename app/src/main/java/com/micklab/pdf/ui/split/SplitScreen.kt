package com.micklab.pdf.ui.split

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
import com.micklab.pdf.domain.model.SplitMode
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
fun SplitScreen(onBack: () -> Unit, viewModel: SplitViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onOutputTreePicked(uri)
    }

    ToolScaffold(title = PdfDestination.SPLIT.title, onBack = onBack) { padding ->
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
                SectionCard(title = "抽出するページ（タップで選択）") {
                    when {
                        ui.loadingThumbnails -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("プレビューを生成中…", style = MaterialTheme.typography.bodyMedium)
                        }

                        ui.thumbnails.isEmpty() -> Text(
                            "プレビューを表示できませんでした",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        else -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${ui.selectedPages.size} ページ選択中", style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    TextButton(onClick = viewModel::selectAll) { Text("全選択") }
                                    TextButton(onClick = viewModel::clearSelection) { Text("全解除") }
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

                SectionCard(title = "出力方法") {
                    ChoiceChipsRow(
                        label = "",
                        options = SplitMode.entries,
                        selected = ui.mode,
                        optionLabel = {
                            when (it) {
                                SplitMode.SELECTED_INTO_ONE -> "1つのPDFにまとめる"
                                SplitMode.EACH_PAGE_SEPARATE -> "1ページずつ分割"
                            }
                        },
                        onSelect = viewModel::onModeChanged,
                    )
                }
            }

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = "実行",
                enabled = ui.source != null && ui.selectedPages.isNotEmpty(),
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
