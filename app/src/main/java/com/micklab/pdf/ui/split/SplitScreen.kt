package com.micklab.pdf.ui.split

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

            SectionCard(title = "抽出設定") {
                OutlinedTextField(
                    value = ui.pageSpec,
                    onValueChange = viewModel::onPageSpecChanged,
                    label = { Text("ページ指定（例: 1-3,5,8-）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceChipsRow(
                    label = "出力方法",
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

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = "実行",
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
