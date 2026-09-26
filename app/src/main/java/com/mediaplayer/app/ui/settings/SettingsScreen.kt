@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(ExperimentalMaterial3Api::class)

package com.mediaplayer.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.mediaplayer.app.MediaPlayerApp
import com.mediaplayer.app.core.AppContainer
import com.mediaplayer.app.data.PlayerSettings
import com.mediaplayer.app.util.formatSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: PlayerSettings = PlayerSettings(),
    val historyCount: Int = 0,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            container.playbackPositionStore.positions.collect { positions ->
                _uiState.update { it.copy(historyCount = positions.size) }
            }
        }
    }

    fun setAutoLandscape(value: Boolean) = save { container.settingsRepository.setAutoLandscape(value) }
    fun setRememberPosition(value: Boolean) = save { container.settingsRepository.setRememberPosition(value) }
    fun setBackgroundPlay(value: Boolean) = save { container.settingsRepository.setBackgroundPlay(value) }
    fun setAutoPip(value: Boolean) = save { container.settingsRepository.setAutoPip(value) }
    fun setDefaultSpeed(value: Float) = save { container.settingsRepository.setDefaultSpeed(value) }
    fun setResizeMode(value: Int) = save { container.settingsRepository.setDefaultResizeMode(value) }
    fun clearHistory() = save { container.playbackPositionStore.clear() }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MediaPlayerApp
                SettingsViewModel(app.container)
            }
        }
    }
}

private val SPEED_CHOICES = listOf(1.0f, 1.25f, 1.5f, 2.0f)

private val RESIZE_CHOICES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "适应屏幕",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "拉伸铺满",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "裁剪铺满",
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("播放")
            SwitchRow(
                title = "自动横屏",
                description = "进入播放页时自动切换到横屏",
                checked = state.settings.autoLandscape,
                onCheckedChange = viewModel::setAutoLandscape,
            )
            SwitchRow(
                title = "记忆播放进度",
                description = "再次打开视频时从上次的位置继续播放",
                checked = state.settings.rememberPosition,
                onCheckedChange = viewModel::setRememberPosition,
            )
            SwitchRow(
                title = "后台播放",
                description = "离开播放页后继续播放（只有声音）",
                checked = state.settings.backgroundPlay,
                onCheckedChange = viewModel::setBackgroundPlay,
            )
            SwitchRow(
                title = "自动画中画",
                description = "Android 12 及以上，按 Home 键自动进入小窗",
                checked = state.settings.autoPip,
                onCheckedChange = viewModel::setAutoPip,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("默认值")
            ChoiceRow(
                title = "默认倍速",
                value = formatSpeed(state.settings.defaultSpeed),
                options = SPEED_CHOICES.map { it to formatSpeed(it) },
                onSelect = viewModel::setDefaultSpeed,
            )
            ChoiceRow(
                title = "默认画面比例",
                value = RESIZE_CHOICES.firstOrNull { it.first == state.settings.defaultResizeMode }?.second
                    ?: "适应屏幕",
                options = RESIZE_CHOICES,
                onSelect = viewModel::setResizeMode,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("数据")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showClearDialog = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("清除播放记录", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "已记录 ${state.historyCount} 个视频的播放进度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "MediaPlayer 1.0.0 · 基于 Media3 / ExoPlayer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除播放记录") },
            text = { Text("所有视频的播放进度都会被清除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (option, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(option)
                                    expanded = false
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                            if (label == value) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text("关闭") }
            },
        )
    }
}
