@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mediaplayer.app.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaplayer.app.data.VideoItem
import com.mediaplayer.app.util.hasPartialVideoAccess
import com.mediaplayer.app.util.hasVideoPermission
import com.mediaplayer.app.util.notificationPermission
import com.mediaplayer.app.util.requiredVideoPermissions
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenVideo: (VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember { mutableStateOf(hasVideoPermission(context)) }
    var searchActive by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<VideoItem?>(null) }
    var detailsTarget by remember { mutableStateOf<VideoItem?>(null) }
    var pendingDelete by remember { mutableStateOf<VideoItem?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = hasVideoPermission(context)
        viewModel.refresh()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val target = pendingDelete
        pendingDelete = null
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refresh()
            scope.launch { snackbarHostState.showSnackbar("已删除「${target?.title.orEmpty()}」") }
        } else {
            scope.launch { snackbarHostState.showSnackbar("已取消删除") }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val permissions = requiredVideoPermissions().toMutableList()
            notificationPermission()?.let { permissions += it }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    fun requestDelete(video: VideoItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(video.uri),
                )
                pendingDelete = video
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                )
            }.onFailure {
                scope.launch { snackbarHostState.showSnackbar("无法删除该视频") }
            }
        } else {
            scope.launch {
                val deleted = viewModel.deleteVideo(video)
                if (deleted) {
                    viewModel.refresh()
                    snackbarHostState.showSnackbar("已删除「${video.title}」")
                } else {
                    snackbarHostState.showSnackbar("删除失败")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    if (searchActive) {
                        TextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text("搜索视频") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text("MediaPlayer", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = when {
                                    !hasPermission -> "等待授权"
                                    state.loading -> "正在扫描本地视频…"
                                    else -> "${state.visibleVideos.size} 个视频 · ${state.videos.size} 个文件"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            viewModel.setQuery("")
                            searchActive = false
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = viewModel::toggleGridMode) {
                            Icon(
                                imageVector = if (state.gridMode) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                                contentDescription = "切换列表 / 网格",
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = state.sort == option,
                                                onClick = { viewModel.setSort(option) },
                                            )
                                        },
                                        onClick = { viewModel.setSort(option) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (state.ascending) "正序（升序）" else "倒序（降序）") },
                                    onClick = { viewModel.toggleSortOrder() },
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !hasPermission -> PermissionState(
                    partial = hasPartialVideoAccess(context),
                    onRequest = {
                        val permissions = requiredVideoPermissions().toMutableList()
                        notificationPermission()?.let { permissions += it }
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                )

                state.loading -> LoadingState()

                state.videos.isEmpty() -> MessageState(
                    title = "没有找到视频",
                    description = "把视频文件放进手机存储后点击刷新试试",
                    onRefresh = viewModel::refresh,
                )

                else -> Column(Modifier.fillMaxSize()) {
                    if (state.folders.size > 1) {
                        FolderFilterRow(
                            folders = state.folders,
                            selected = state.selectedFolder,
                            onSelect = viewModel::setFolder,
                        )
                    }
                    if (state.visibleVideos.isEmpty()) {
                        MessageState(
                            title = "没有匹配的视频",
                            description = "换个关键词或文件夹试试",
                            onRefresh = viewModel::refresh,
                        )
                    } else if (state.gridMode) {
                        VideoGrid(
                            videos = state.visibleVideos,
                            positions = state.positions,
                            onOpen = onOpenVideo,
                            onLongPress = { actionTarget = it },
                        )
                    } else {
                        VideoList(
                            videos = state.visibleVideos,
                            positions = state.positions,
                            onOpen = onOpenVideo,
                            onLongPress = { actionTarget = it },
                        )
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        VideoActionSheet(
            video = target,
            onDismiss = { actionTarget = null },
            onPlay = {
                actionTarget = null
                onOpenVideo(target)
            },
            onShare = {
                actionTarget = null
                runCatching { context.startActivity(Intent.createChooser(viewModel.buildShareIntent(target), "分享视频")) }
            },
            onDetails = {
                actionTarget = null
                detailsTarget = target
            },
            onDelete = {
                actionTarget = null
                requestDelete(target)
            },
        )
    }

    detailsTarget?.let { target ->
        VideoDetailsDialog(video = target, onDismiss = { detailsTarget = null })
    }
}

@Composable
private fun FolderFilterRow(
    folders: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部") },
            )
        }
        items(folders, key = { it }) { folder ->
            FilterChip(
                selected = selected == folder,
                onClick = { onSelect(folder) },
                label = { Text(folder) },
            )
        }
    }
}

@Composable
private fun VideoList(
    videos: List<VideoItem>,
    positions: Map<Long, Long>,
    onOpen: (VideoItem) -> Unit,
    onLongPress: (VideoItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoListItem(
                video = video,
                resumePosition = positions[video.id] ?: 0L,
                onClick = { onOpen(video) },
                onLongClick = { onLongPress(video) },
            )
        }
    }
}

@Composable
private fun VideoGrid(
    videos: List<VideoItem>,
    positions: Map<Long, Long>,
    onOpen: (VideoItem) -> Unit,
    onLongPress: (VideoItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoGridItem(
                video = video,
                resumePosition = positions[video.id] ?: 0L,
                onClick = { onOpen(video) },
                onLongClick = { onLongPress(video) },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageState(title: String, description: String, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("刷新")
            }
        }
    }
}

@Composable
private fun PermissionState(partial: Boolean, onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(64.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (partial) "当前只能访问部分视频" else "需要访问视频的权限",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (partial) {
                    "你之前只授权了部分视频，重新授权可以选择更多视频"
                } else {
                    "MediaPlayer 需要读取本机视频，才能浏览和播放。所有数据都只保存在本机。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text(if (partial) "重新选择视频" else "授予权限")
            }
        }
    }
}
