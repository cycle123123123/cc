package com.jmreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jmreader.data.AlbumBrief
import com.jmreader.data.JmRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repo: JmRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun runSearch(reset: Boolean) {
        val keyword = query.trim()
        if (keyword.isEmpty() || loading) return
        loading = true
        error = null
        runCatching { repo.search(keyword, if (reset) 1 else page) }
            .onSuccess { result ->
                results = if (reset) {
                    result.content
                } else {
                    results + result.content.filter { new -> results.none { it.id == new.id } }
                }
                total = result.total
                page = if (reset) 2 else page + 1
                searched = true
            }
            .onFailure { error = it.message ?: "搜尋失敗" }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜尋作品 / 作者") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { scope.launch { runSearch(true) } }),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { runSearch(true) } }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜尋")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && results.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                !searched -> Text(
                    text = "輸入關鍵字開始搜尋",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                results.isEmpty() -> Text(
                    text = "沒有結果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "共 $total 個結果",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(items = results, key = { it.id }) { album ->
                        AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator()
                            } else {
                                Button(onClick = { scope.launch { runSearch(false) } }) { Text("載入更多") }
                            }
                        }
                    }
                }
            }
        }
    }
}
