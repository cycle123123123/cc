package com.jmreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.CreatorWork
import com.jmreader.data.JmRepository
import com.jmreader.data.userMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(
    repo: JmRepository,
    authorId: String,
    authorName: String,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    var works by remember { mutableStateOf<List<CreatorWork>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authorId) {
        loading = true
        runCatchingCancellable { repo.creatorWorks(authorId, 1) }
            .onSuccess { works = it }
            .onFailure { error = it.userMessage() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(authorName.ifBlank { "作者" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                works.isEmpty() -> Text(
                    text = "沒有作品",
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
                    items(items = works, key = { it.id }) { work ->
                        val album = AlbumBrief(
                            id = work.id,
                            name = work.title,
                            author = work.authorName,
                        )
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { work.id.toLongOrNull()?.let(onOpen) },
                        )
                    }
                }
            }
        }
    }
}
