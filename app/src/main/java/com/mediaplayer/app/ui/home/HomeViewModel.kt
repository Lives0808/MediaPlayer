package com.mediaplayer.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mediaplayer.app.MediaPlayerApp
import com.mediaplayer.app.core.AppContainer
import com.mediaplayer.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption(val label: String) {
    DATE_ADDED("添加时间"),
    NAME("名称"),
    DURATION("时长"),
    SIZE("大小"),
}

data class HomeUiState(
    val loading: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val visibleVideos: List<VideoItem> = emptyList(),
    val folders: List<String> = emptyList(),
    val selectedFolder: String? = null,
    val query: String = "",
    val sort: SortOption = SortOption.DATE_ADDED,
    val ascending: Boolean = false,
    val gridMode: Boolean = false,
    val positions: Map<Long, Long> = emptyMap(),
    val error: String? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allVideos: List<VideoItem> = emptyList()

    init {
        viewModelScope.launch {
            container.playbackPositionStore.positions.collect { positions ->
                _uiState.update { it.copy(positions = positions) }
                recompute()
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val videos = runCatching { container.mediaRepository.loadVideos() }
            allVideos = videos.getOrDefault(emptyList())
            val folders = allVideos
                .map { it.folderName }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            _uiState.update { current ->
                current.copy(
                    loading = false,
                    videos = allVideos,
                    folders = folders,
                    selectedFolder = current.selectedFolder?.takeIf { it in folders },
                    error = videos.exceptionOrNull()?.message,
                )
            }
            recompute()
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        recompute()
    }

    fun setFolder(folder: String?) {
        _uiState.update { it.copy(selectedFolder = folder) }
        recompute()
    }

    fun setSort(sort: SortOption) {
        _uiState.update { it.copy(sort = sort) }
        recompute()
    }

    fun toggleSortOrder() {
        _uiState.update { it.copy(ascending = !it.ascending) }
        recompute()
    }

    fun toggleGridMode() {
        _uiState.update { it.copy(gridMode = !it.gridMode) }
    }

    private fun recompute() {
        _uiState.update { current ->
            val keyword = current.query.trim()
            var list = allVideos
            current.selectedFolder?.let { folder ->
                list = list.filter { it.folderName == folder }
            }
            if (keyword.isNotEmpty()) {
                list = list.filter { it.title.contains(keyword, ignoreCase = true) }
            }
            val sorted = when (current.sort) {
                SortOption.DATE_ADDED -> list.sortedBy { it.dateAdded }
                SortOption.NAME -> list.sortedBy { it.title.lowercase() }
                SortOption.DURATION -> list.sortedBy { it.duration }
                SortOption.SIZE -> list.sortedBy { it.size }
            }
            current.copy(visibleVideos = if (current.ascending) sorted else sorted.reversed())
        }
    }

    suspend fun deleteVideo(video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            container.app.contentResolver.delete(video.uri, null, null) > 0
        }.getOrDefault(false)
    }

    fun buildShareIntent(video: VideoItem): Intent = Intent(Intent.ACTION_SEND).apply {
        type = video.mimeType.ifBlank { "video/*" }
        putExtra(Intent.EXTRA_STREAM, video.uri as Uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MediaPlayerApp
                HomeViewModel(app.container)
            }
        }
    }
}
