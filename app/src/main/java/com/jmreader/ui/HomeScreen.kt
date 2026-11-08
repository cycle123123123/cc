package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jmreader.core.JmConfig
import com.jmreader.data.JmRepository
import com.jmreader.data.LastRead
import com.jmreader.data.ReadingHistory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: JmRepository,
    state: HomeState,
    onOpen: (Long) -> Unit,
    onContinue: (com.jmreader.data.LastRead) -> Unit,
    onSearch: () -> Unit,
    onMe: () -> Unit,
    onDiscover: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jmreader", android.content.Context.MODE_PRIVATE) }
    val history = remember { ReadingHistory.of(context) }
    val lastRead by history.last.collectAsState()

    val albums = state.albums
    val loading = state.loading
    val error = state.error
    val update = state.update

    suspend fun load(reset: Boolean) {
        if (state.loading) return
        state.loading = true
        state.error = null
        runCatching { repo.latest(if (reset) 1 else state.page) }
            .onSuccess { list ->
                state.albums = if (reset) {
                    list
                } else {
                    state.albums + list.filter { new -> state.albums.none { it.id == new.id } }
                }
                state.page = if (reset) 2 else state.page + 1
                state.loaded = true
            }
            .onFailure {
                state.error = it.message ?: "載入失敗"
                state.loaded = true
            }
        state.loading = false
    }

    LaunchedEffect(Unit) {
        if (!state.loaded) load(true)

        runCatching { repo.settingInfo() }
            .onSuccess { setting ->
                val latest = setting.jm3Version
                val dismissed = prefs.getString("dismissed_protocol", null)

                if (latest.isNotBlank() && latest != JmConfig.PROTOCOL_VERSION && latest != dismissed) {
                    state.update = setting
                }
            }
            .onFailure { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JMReader") },
                actions = {
                    IconButton(onClick = onDiscover) {
                        Icon(Icons.Filled.Explore, contentDescription = "發現")
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onMe) {
                        Icon(Icons.Filled.Person, contentDescription = "我的")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                albums.isEmpty() && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                albums.isEmpty() && error != null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { scope.launch { load(true) } }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重試")
                    }
                }

                else -> Column(Modifier.fillMaxSize()) {
                    update?.let { setting ->
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            UpdateBanner(
                                version = setting.jm3Version,
                                onDismiss = {
                                    prefs.edit().putString("dismissed_protocol", setting.jm3Version).apply()
                                    state.update = null
                                },
                            )
                        }
                    }

                    lastRead?.let { item ->
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            ContinueCard(repo = repo, item = item, onClick = { onContinue(item) })
                        }
                    }

                    LazyVerticalGrid(
                        state = state.gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(items = albums, key = { it.id }) { album ->
                            AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
                        }

                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (loading) {
                                    CircularProgressIndicator()
                                } else {
                                    Button(onClick = { scope.launch { load(false) } }) { Text("載入更多") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(repo: JmRepository, item: LastRead, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = repo.coverUrl(item.cid.toString(), tall = true),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = "繼續閱讀",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "第 ${item.page + 1} 頁",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateBanner(version: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "官方協議已更新至 v$version",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "當前客戶端適配 v${JmConfig.PROTOCOL_VERSION}，如遇異常請更新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDismiss) { Text("知道了") }
    }
}
