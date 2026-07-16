package com.micklab.pdf.ui.merge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
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
fun MergeScreen(onBack: () -> Unit, viewModel: MergeViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val op by viewModel.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onPdfsPicked(uris)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onOutputTreePicked(uri)
    }

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.MERGE.titleRes), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.merge_list_title)) {
                if (ui.items.isEmpty()) {
                    Text(stringResource(R.string.label_no_pdf), style = MaterialTheme.typography.bodyMedium)
                }
                ui.items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", style = MaterialTheme.typography.labelLarge)
                        item.thumbnail?.let { thumb ->
                            val image = remember(thumb) { thumb.bitmap.asImageBitmap() }
                            val ratio = (thumb.bitmap.width.toFloat() / thumb.bitmap.height.coerceAtLeast(1))
                                .coerceIn(0.2f, 5f)
                            Image(
                                bitmap = image,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .height(40.dp)
                                    .aspectRatio(ratio)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        }
                        Text(
                            item.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, stringResource(R.string.action_move_up))
                        }
                        IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < ui.items.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, stringResource(R.string.action_move_down))
                        }
                        IconButton(onClick = { viewModel.remove(index) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_delete))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { pickPdfs.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.merge_add)) }
            }

            OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })

            PrimaryActionButton(
                text = stringResource(R.string.merge_run),
                enabled = ui.items.size >= 2,
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
