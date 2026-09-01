package com.allvideodownloader.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allvideodownloader.app.R
import com.allvideodownloader.app.data.DownloadManagerSource
import com.allvideodownloader.app.data.LinkProbe
import com.allvideodownloader.app.data.LinkValidator
import com.allvideodownloader.app.data.model.ActiveDownload
import com.allvideodownloader.app.util.AppEvents
import com.allvideodownloader.app.util.FileNames
import com.allvideodownloader.app.util.Formatters
import com.allvideodownloader.app.work.PublishDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    /** Everything needed to hand a link to DownloadManager. */
    data class PendingRequest(val url: String, val fileName: String, val mimeType: String)

    sealed interface Prompt {
        /** Policy or validation failure. There is no override for these. */
        data class Blocked(val message: String) : Prompt

        /** Unclear, not forbidden — the user may choose to continue. */
        data class Retryable(val message: String, val request: PendingRequest) : Prompt
    }

    data class UiState(
        val url: String = "",
        val isChecking: Boolean = false,
        val activeDownloads: List<ActiveDownload> = emptyList(),
        val prompt: Prompt? = null,
        val message: String? = null
    )

    private val downloads = DownloadManagerSource(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        reconcileUnpublishedDownloads()
        viewModelScope.launch {
            downloads.observe().collect { list ->
                _state.update { it.copy(activeDownloads = list) }
            }
        }
        viewModelScope.launch {
            // Messages posted by the background publish worker.
            AppEvents.messages.collect { text -> _state.update { it.copy(message = text) } }
        }
    }

    /**
     * ACTION_DOWNLOAD_COMPLETE never arrives if the app was force-stopped while a transfer was
     * running, which would leave the finished file staged and invisible. One cheap query at
     * start-up hands any such download back to the publish worker.
     */
    private fun reconcileUnpublishedDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val application = getApplication<Application>()
            downloads.finishedIds().forEach { id ->
                PublishDownloadWorker.enqueue(application, id)
            }
        }
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, prompt = null) }
    }

    fun clearUrl() = _state.update { it.copy(url = "", prompt = null) }

    fun dismissPrompt() = _state.update { it.copy(prompt = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun cancel(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { downloads.cancel(id) }
    }

    fun submit() {
        if (_state.value.isChecking) return
        when (val result = LinkValidator.validate(_state.value.url)) {
            is LinkValidator.Result.Rejected -> showBlocked(messageFor(result))
            is LinkValidator.Result.Accepted -> inspectThenDownload(result)
        }
    }

    fun forceDownload(request: PendingRequest) {
        _state.update { it.copy(prompt = null) }
        enqueue(request)
    }

    private fun inspectThenDownload(accepted: LinkValidator.Result.Accepted) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _state.update { it.copy(isChecking = true, prompt = null) }
            val probe = LinkProbe.probe(accepted.url)
            _state.update { it.copy(isChecking = false) }

            probe.fold(
                onSuccess = { info -> onProbeSuccess(accepted, info) },
                onFailure = { error ->
                    // Some CDNs refuse every metadata request. Let the user decide.
                    _state.update {
                        it.copy(
                            prompt = Prompt.Retryable(
                                message = getString(
                                    R.string.error_unreachable,
                                    error.message ?: "no response"
                                ),
                                request = fallbackRequest(accepted)
                            )
                        )
                    }
                }
            )
        }
    }

    private fun onProbeSuccess(
        accepted: LinkValidator.Result.Accepted,
        info: LinkProbe.Info
    ) {
        // A redirect chain can end somewhere we are not allowed to download from.
        val finalHost = Uri.parse(info.finalUrl).host
        val blockedHost = finalHost?.let(LinkValidator::blockedHostFor)
        if (blockedHost != null) {
            showBlocked(getString(R.string.error_blocked_host, blockedHost))
            return
        }

        val fileName = FileNames.buildFileName(
            serverSuggestion = info.suggestedFileName,
            urlHint = accepted.fileNameHint,
            contentType = info.contentType
        )
        val request = PendingRequest(
            url = accepted.url,
            fileName = fileName,
            mimeType = FileNames.mimeTypeFor(fileName)
        )

        val looksLikeVideo = info.isVideoContentType ||
            LinkValidator.looksLikeVideoExtension(accepted.extension)

        if (!looksLikeVideo) {
            val detail = info.contentType?.let { " (the server reported $it)" }.orEmpty()
            _state.update {
                it.copy(
                    prompt = Prompt.Retryable(
                        message = getString(R.string.error_not_video, detail),
                        request = request
                    )
                )
            }
            return
        }

        if (info.contentLength > 0) {
            enqueue(request, Formatters.bytes(info.contentLength))
        } else {
            enqueue(request)
        }
    }

    /**
     * [sizeLabel] is folded into the one success message rather than posted separately: two
     * snackbar messages in quick succession means the first one is replaced before it is read.
     */
    private fun enqueue(request: PendingRequest, sizeLabel: String? = null) {
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    downloads.enqueue(request.url, request.fileName, request.mimeType)
                }
            }
            outcome.fold(
                onSuccess = {
                    val started = getString(R.string.download_started)
                    _state.update {
                        it.copy(
                            url = "",
                            prompt = null,
                            message = if (sizeLabel == null) started else "$started · $sizeLabel"
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            message = error.message?.takeIf { m -> m.isNotBlank() }
                                ?: getString(R.string.error_no_external_storage)
                        )
                    }
                }
            )
        }
    }

    private fun fallbackRequest(accepted: LinkValidator.Result.Accepted): PendingRequest {
        val fileName = FileNames.buildFileName(null, accepted.fileNameHint, null)
        return PendingRequest(accepted.url, fileName, FileNames.mimeTypeFor(fileName))
    }

    private fun showBlocked(message: String) =
        _state.update { it.copy(prompt = Prompt.Blocked(message)) }

    private fun messageFor(rejected: LinkValidator.Result.Rejected): String = when (rejected.reason) {
        LinkValidator.RejectReason.EMPTY -> getString(R.string.error_empty_link)
        LinkValidator.RejectReason.MALFORMED -> getString(R.string.error_malformed_link)
        LinkValidator.RejectReason.UNSUPPORTED_SCHEME -> getString(R.string.error_scheme)
        LinkValidator.RejectReason.BLOCKED_HOST ->
            getString(R.string.error_blocked_host, rejected.detail ?: "this service")

        LinkValidator.RejectReason.STREAM_PLAYLIST -> getString(R.string.error_playlist)
    }

    private fun getString(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) {
            getApplication<Application>().getString(resId)
        } else {
            getApplication<Application>().getString(resId, *args)
        }
}
