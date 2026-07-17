package com.micklab.pdf.ui.toimage

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micklab.pdf.R
import com.micklab.pdf.core.OperationState
import com.micklab.pdf.domain.model.ImageFormat
import com.micklab.pdf.ui.common.ChoiceChipsRow
import com.micklab.pdf.ui.common.LazyPageCell
import com.micklab.pdf.ui.common.OperationStatus
import com.micklab.pdf.ui.common.OutputFilesCard
import com.micklab.pdf.ui.common.OutputFolderSection
import com.micklab.pdf.ui.common.PrimaryActionButton
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.common.fullSpanItem
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

    ToolScaffold(title = androidx.compose.ui.res.stringResource(PdfDestination.PDF_TO_IMAGE.titleRes), onBack = onBack) { padding ->
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
                SectionCard(title = stringResource(R.string.label_input_pdf)) {
                    Text(
                        if (ui.source == null) stringResource(R.string.label_no_pdf)
                        else stringResource(R.string.label_file_pages, ui.sourceName, ui.pageCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.action_pick_pdf))
                    }
                }
            }

            if (ui.source != null && !ui.loadingThumbnails && ui.pageCount > 0) {
                fullSpanItem {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (ui.selectedPages.isEmpty()) stringResource(R.string.p2i_all_pages)
                            else stringResource(R.string.p2i_pages_selected, ui.selectedPages.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row {
                            TextButton(onClick = viewModel::selectAll) { Text(stringResource(R.string.action_select_all)) }
                            TextButton(onClick = viewModel::clearSelection) { Text(stringResource(R.string.action_clear_selection)) }
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
            } else if (ui.loadingThumbnails) {
                fullSpanItem {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.state_loading_pdf), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            fullSpanItem {
                SectionCard(title = stringResource(R.string.p2i_settings_title)) {
                    Text(stringResource(R.string.p2i_dpi, ui.dpi), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = ui.dpi.toFloat(),
                        onValueChange = { viewModel.onDpiChanged(it.toInt()) },
                        valueRange = 72f..600f,
                    )
                    ChoiceChipsRow(
                        label = stringResource(R.string.p2i_format),
                        options = ImageFormat.entries,
                        selected = ui.format,
                        optionLabel = { it.name },
                        onSelect = viewModel::onFormatChanged,
                    )
                    if (ui.format == ImageFormat.JPEG) {
                        Text(stringResource(R.string.p2i_jpeg_quality, ui.jpegQuality), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = ui.jpegQuality.toFloat(),
                            onValueChange = { viewModel.onQualityChanged(it.toInt()) },
                            valueRange = 10f..100f,
                        )
                    }
                }
            }

            fullSpanItem {
                OutputFolderSection(folderName = ui.outputFolderName, onPick = { pickTree.launch(null) })
            }

            fullSpanItem {
                PrimaryActionButton(
                    text = stringResource(R.string.p2i_run),
                    enabled = ui.source != null,
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
