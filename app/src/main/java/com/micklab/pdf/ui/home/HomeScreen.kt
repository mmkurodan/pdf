package com.micklab.pdf.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import com.micklab.pdf.api.OcrApiState
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

// The OCR tools come first: they are the on-screen ("direct run") counterpart of the API.
private val CATEGORIES = listOf(
    ToolCategory(
        R.string.cat_ocr, Icons.Default.DocumentScanner, R.string.cat_ocr_desc,
        listOf(
            ToolEntry(PdfDestination.OCR, Icons.Default.DocumentScanner, R.string.tool_ocr_desc),
            ToolEntry(PdfDestination.SUMMARY, Icons.Default.Summarize, R.string.tool_summary_desc),
            ToolEntry(PdfDestination.PROMPT, Icons.Default.AutoAwesome, R.string.tool_prompt_desc),
        ),
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
        R.string.cat_create, Icons.Default.Edit, R.string.cat_create_desc,
        listOf(ToolEntry(PdfDestination.EDIT, Icons.Default.Edit, R.string.tool_edit_desc)),
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

/**
 * Home = the server console, laid out like the companion llama app's main screen:
 * ① API status + start/stop + settings/exit, ② the tools as collapsible sections,
 * ③ the processing log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTool: (PdfDestination) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // Expanded sections survive navigating into a tool and back. OCR starts open.
    var expanded by rememberSaveable { mutableStateOf(setOf(0)) }
    var logExpanded by rememberSaveable { mutableStateOf(true) }
    val showModelPrompt by viewModel.showModelPrompt.collectAsStateWithLifecycle()
    val status by viewModel.apiStatus.collectAsStateWithLifecycle()
    val card by viewModel.apiCard.collectAsStateWithLifecycle()
    val log by viewModel.apiLog.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (showModelPrompt) {
        ModelPromptDialog(
            onGoSettings = { onOpenTool(PdfDestination.OCR_SETTINGS) },
            onLater = viewModel::dismissPrompt,
            onDontShowAgain = viewModel::dontShowAgain,
        )
    }

    // POST_NOTIFICATIONS (Android 13+) is asked before the first start; if denied the
    // API is not started, as before.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.setApiEnabled(true) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshBatteryExemption() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_title))
                        Text(
                            stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = { AdmobBanner() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ApiServerCard(
                status = status,
                card = card,
                onToggle = {
                    if (status.enabled) {
                        viewModel.setApiEnabled(false)
                    } else {
                        val needsPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPerm) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else viewModel.setApiEnabled(true)
                    }
                },
                onPortChanged = viewModel::onPortInputChanged,
                onSettings = { onOpenTool(PdfDestination.OCR_SETTINGS) },
                onExit = {
                    viewModel.setApiEnabled(false)
                    context.findActivity()?.finishAndRemoveTask()
                },
                onBatteryExempt = {
                    @SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + context.packageName))
                    runCatching { batteryLauncher.launch(intent) }
                },
            )

            CATEGORIES.forEachIndexed { index, category ->
                val open = index in expanded
                ExpandableCard(
                    icon = category.icon,
                    title = stringResource(category.titleRes),
                    description = stringResource(category.descRes),
                    expanded = open,
                    onToggle = { expanded = if (open) expanded - index else expanded + index },
                ) {
                    category.tools.forEach { tool ->
                        ToolRow(
                            icon = tool.icon,
                            title = stringResource(tool.destination.titleRes),
                            description = stringResource(tool.descRes),
                            onClick = { onOpenTool(tool.destination) },
                        )
                    }
                }
            }

            ExpandableCard(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = stringResource(R.string.home_log_title),
                description = null,
                expanded = logExpanded,
                onToggle = { logExpanded = !logExpanded },
            ) {
                LogPanel(lines = log, onClear = viewModel::clearLog)
            }
        }
    }
}

/** ① Status line, start/stop, port + endpoints, then Settings / Exit. */
@Composable
private fun ApiServerCard(
    status: OcrApiState.Status,
    card: ApiCardUiState,
    onToggle: () -> Unit,
    onPortChanged: (String) -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onBatteryExempt: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val statusText = when {
                status.enabled && status.running -> stringResource(R.string.expert_notif_running, status.port)
                status.enabled && status.error != null -> stringResource(R.string.expert_notif_error, status.error)
                status.enabled -> stringResource(R.string.expert_notif_starting)
                else -> stringResource(R.string.set_expert_status_stopped)
            }
            Text(
                (if (status.enabled && status.running) "▶ " else "■ ") +
                    stringResource(R.string.expert_notif_title) + ": " + statusText,
                style = MaterialTheme.typography.titleMedium,
                color = if (status.enabled && status.running) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.set_expert_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onToggle,
                enabled = status.enabled || !card.portError,
                colors = if (status.enabled) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (status.enabled) R.string.home_api_stop else R.string.home_api_start))
            }

            OutlinedTextField(
                value = card.portInput,
                onValueChange = onPortChanged,
                // The port is fixed while serving; stop the API to change it.
                enabled = !status.enabled,
                label = { Text(stringResource(R.string.set_expert_port)) },
                isError = card.portError,
                supportingText = if (card.portError) {
                    { Text(stringResource(R.string.set_expert_port_invalid)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (status.enabled) {
                Text(stringResource(R.string.set_expert_endpoints), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.set_expert_endpoints_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Localhost always available, then LAN addresses (carrier IPs are filtered out).
                EndpointRow("http://127.0.0.1:${status.port}/ocr")
                card.localIps.forEach { ip -> EndpointRow("http://$ip:${status.port}/ocr") }
            }

            if (!card.batteryExempt) {
                OutlinedButton(onClick = onBatteryExempt, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_battery_exempt))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cat_settings))
                }
                OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_exit))
                }
            }
        }
    }
}

/** A monospaced endpoint URL that copies itself to the clipboard when tapped. */
@Composable
private fun EndpointRow(url: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.set_expert_endpoint_copied)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                clipboard.setText(AnnotatedString(url))
                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            url,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.set_expert_endpoint_copy),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A card whose header toggles its body (▶ collapsed / ▼ expanded). */
@Composable
private fun ExpandableCard(
    icon: ImageVector,
    title: String,
    description: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** ③ Server activity (start/stop, requests, errors); selectable so it can be copied. */
@Composable
private fun LogPanel(lines: List<String>, onClear: () -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClear, enabled = lines.isNotEmpty()) {
            Text(stringResource(R.string.home_log_clear))
        }
        SelectionContainer {
            Text(
                if (lines.isEmpty()) stringResource(R.string.home_log_empty) else lines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 240.dp)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** One-time prompt shown when no OCR model is downloaded yet. "今後表示しない" persists the opt-out. */
@Composable
private fun ModelPromptDialog(
    onGoSettings: () -> Unit,
    onLater: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    var dontShow by remember { mutableStateOf(false) }
    val close = { if (dontShow) onDontShowAgain() else onLater() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.home_models_title)) },
        text = {
            Column {
                Text(stringResource(R.string.home_models_message))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { dontShow = !dontShow },
                ) {
                    Checkbox(checked = dontShow, onCheckedChange = { dontShow = it })
                    Text(stringResource(R.string.home_models_dont_show))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { close(); onGoSettings() }) {
                Text(stringResource(R.string.home_models_go_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = close) { Text(stringResource(R.string.home_models_later)) }
        },
    )
}
