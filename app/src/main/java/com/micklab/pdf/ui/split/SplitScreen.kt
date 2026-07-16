package com.micklab.pdf.ui.split

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.micklab.pdf.ui.common.LazyPageCell
import com.micklab.pdf.ui.common.fullSpanItem
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.OutputFilesCard
import com.micklab.pdf.ui.common.OutputFolderSection
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
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

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.SPLIT.titleRes), onBack = onBack) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fullSpanItem {
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
            }

            if (ui.source != null) {
                if (ui.loadingThumbnails) {
                    fullSpanItem {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("PDF を読み込み中…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (ui.pageCount > 0) {
                    fullSpanItem {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${ui.selectedPages.size} / ${ui.pageCount} ページ選択", style = MaterialTheme.typography.titleSmall)
                            Row {
                                TextButton(onClick = viewModel::selectAll) { Text("全選択") }
                                TextButton(onClick = viewModel::clearSelection) { Text("全解除") }
                            }
                        }
                    }
                    items(ui.pageCount) { index ->
                        LazyPageCell(
                            index = index,
                            selected = index in ui.selectedPages,
                            load = viewModel::thumbnail,
                            onClick = { viewModel.togglePage(index) },
                        )
                    }
                    fullSpanItem {
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
                } else {
                    fullSpanItem {
                        Text("プレビューを表示できませんでした", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            fullSpanItem {
                OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })
            }

            fullSpanItem {
                PrimaryActionButton(
                    text = "実行",
                    enabled = ui.source != null && ui.selectedPages.isNotEmpty(),
                    loading = op is OperationState.Running,
                    onClick = viewModel::run,
                )
            }

            fullSpanItem { OperationStatus(op) }

            (op as? OperationState.Success)?.data?.let { files ->
                fullSpanItem {
                    OutputFilesCard(
                        files = files,
                        onShareAll = { context.shareOutputs(files) },
                        onOpen = { context.openOutput(it) },
                    )
                }
            }
        }
    }
}
