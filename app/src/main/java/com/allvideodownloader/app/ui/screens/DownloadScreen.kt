package com.allvideodownloader.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.allvideodownloader.app.R
import com.allvideodownloader.app.data.model.ActiveDownload
import com.allvideodownloader.app.data.model.DownloadState
import com.allvideodownloader.app.ui.DownloadViewModel
import com.allvideodownloader.app.util.Formatters

@Composable
fun DownloadScreen(
    state: DownloadViewModel.UiState,
    contentPadding: PaddingValues,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onCancel: (Long) -> Unit,
    onDismissPrompt: () -> Unit,
    onForceDownload: (DownloadViewModel.PendingRequest) -> Unit,
    onClipboardEmpty: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.paste_link_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = state.url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.link_field_label)) },
                    placeholder = { Text(stringResource(R.string.link_field_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                    trailingIcon = {
                        if (state.url.isBlank()) {
                            IconButton(
                                onClick = {
                                    val text = clipboard.getText()?.text?.trim()
                                    if (text.isNullOrBlank()) onClipboardEmpty() else onUrlChange(text)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = stringResource(R.string.action_paste)
                                )
                            }
                        } else {
                            IconButton(onClick = onClear) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                    },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            onSubmit()
                        }
                    )
                )

                PrimaryActionButton(
                    isBusy = state.isChecking,
                    onClick = {
                        focusManager.clearFocus()
                        onSubmit()
                    }
                )

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.supported_links_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.active_downloads),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (state.activeDownloads.isEmpty()) {
            Text(
                text = stringResource(R.string.no_active_downloads),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.activeDownloads.forEach { download ->
                    ActiveDownloadCard(download = download, onCancel = { onCancel(download.id) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    state.prompt?.let { prompt ->
        when (prompt) {
            is DownloadViewModel.Prompt.Blocked -> AlertDialog(
                onDismissRequest = onDismissPrompt,
                title = { Text(stringResource(R.string.app_name)) },
                text = { Text(prompt.message) },
                confirmButton = {
                    TextButton(onClick = onDismissPrompt) { Text("OK") }
                }
            )

            is DownloadViewModel.Prompt.Retryable -> AlertDialog(
                onDismissRequest = onDismissPrompt,
                title = { Text(stringResource(R.string.app_name)) },
                text = { Text(prompt.message) },
                confirmButton = {
                    TextButton(onClick = { onForceDownload(prompt.request) }) {
                        Text(stringResource(R.string.error_download_anyway))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissPrompt) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(isBusy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.checking_link))
        } else {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.action_search_download),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun ActiveDownloadCard(download: ActiveDownload, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = download.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (download.state == DownloadState.FAILED) {
                    Text(
                        text = download.statusDetail ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    if (download.hasKnownSize) {
                        LinearProgressIndicator(
                            progress = { download.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Text(
                        text = listOfNotNull(
                            download.statusDetail,
                            Formatters.progressLabel(download.bytesDownloaded, download.totalBytes)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = if (download.state == DownloadState.FAILED) {
                        Icons.Filled.Clear
                    } else {
                        Icons.Filled.Close
                    },
                    contentDescription = stringResource(R.string.action_cancel_download)
                )
            }
        }
    }
}
