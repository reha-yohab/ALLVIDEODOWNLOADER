package com.allvideodownloader.app.ui

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allvideodownloader.app.R
import com.allvideodownloader.app.data.VideoLibraryRepository
import com.allvideodownloader.app.data.model.LibraryVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val videos: List<LibraryVideo> = emptyList(),
        val isLoading: Boolean = true,
        val message: String? = null,
        val pendingConsent: IntentSender? = null
    )

    private val repository = VideoLibraryRepository(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Kept so the delete can be retried once the user grants consent on Android 11+. */
    private var awaitingConsentFor: LibraryVideo? = null

    init {
        refresh()
        viewModelScope.launch {
            // MediaStore fires several notifications per write; collectLatest + delay debounces
            // them without needing an opt-in flow operator.
            repository.observeChanges().collectLatest {
                delay(400)
                refresh(showSpinner = false)
            }
        }
    }

    fun refresh(showSpinner: Boolean = true) {
        viewModelScope.launch {
            if (showSpinner) _state.update { it.copy(isLoading = true) }
            val videos = repository.load()
            _state.update { it.copy(videos = videos, isLoading = false) }
        }
    }

    fun delete(video: LibraryVideo) {
        viewModelScope.launch {
            when (val result = repository.delete(video.uri)) {
                is VideoLibraryRepository.DeleteResult.Deleted -> {
                    ThumbnailLoader.evict(video.id)
                    _state.update {
                        it.copy(
                            videos = it.videos.filterNot { item -> item.id == video.id },
                            message = getApplication<Application>().getString(R.string.video_deleted)
                        )
                    }
                }

                is VideoLibraryRepository.DeleteResult.NeedsConsent -> {
                    awaitingConsentFor = video
                    _state.update { it.copy(pendingConsent = result.intentSender) }
                }

                is VideoLibraryRepository.DeleteResult.Failed ->
                    _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun onConsentResult(granted: Boolean) {
        val video = awaitingConsentFor
        awaitingConsentFor = null
        _state.update { it.copy(pendingConsent = null) }
        if (granted && video != null) {
            ThumbnailLoader.evict(video.id)
            refresh(showSpinner = false)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
}
