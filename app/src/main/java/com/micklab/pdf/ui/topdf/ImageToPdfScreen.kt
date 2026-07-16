package com.micklab.pdf.ui.topdf

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.usecase.PagePreset
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
fun ImageToPdfScreen(onBack: () -> Unit, viewModel: ImageToPdfViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onImagesPicked(uris)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onOutputTreePicked(uri)
    }

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.IMAGE_TO_PDF.titleRes), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "画像（上から順に PDF 化）") {
                if (ui.items.isEmpty()) {
                    Text("画像が選択されていません", style = MaterialTheme.typography.bodyMedium)
                }
                ui.items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            "${index + 1}. ${item.name}",
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, "上へ")
                        }
                        IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < ui.items.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, "下へ")
                        }
                        IconButton(onClick = { viewModel.remove(index) }) {
                            Icon(Icons.Default.Close, "削除")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { pickImages.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("画像を追加") }
            }

            SectionCard(title = "ページ設定") {
                ChoiceChipsRow(
                    label = "ページサイズ",
                    options = PagePreset.entries,
                    selected = ui.preset,
                    optionLabel = {
                        when (it) {
                            PagePreset.FIT_A4 -> "A4 に合わせる"
                            PagePreset.MATCH_IMAGE -> "画像サイズ"
                        }
                    },
                    onSelect = viewModel::onPresetChanged,
                )
            }

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = "PDF を作成",
                enabled = ui.items.isNotEmpty(),
                loading = op is OperationState.Running,
                onClick = viewModel::run,
            )

            OperationStatus(op)
            (op as? OperationState.Success)?.data?.let { file ->
                OutputFilesCard(
                    files = listOf(file),
                    onShareAll = { context.shareOutputs(listOf(file)) },
                    onOpen = { context.openOutput(it) },
                )
            }
        }
    }
}
