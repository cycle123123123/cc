package com.jmreader.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmreader.data.JmRepository
import com.jmreader.data.model.AlbumBrief
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.WeekOptions
import com.jmreader.data.model.userMessage
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.ui.components.ErrorState
import com.jmreader.ui.components.IosFilterPill
import com.jmreader.ui.theme.IosBlue

@Composable
internal fun WeekTab(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
) {
    var options by remember { mutableStateOf<WeekOptions?>(null) }
    var type by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.week() }
            .onSuccess { week ->
                options = week
                type = week.type.firstOrNull { it.title.isNotBlank() }?.id.orEmpty()
                period = week.categories.firstOrNull()?.id.orEmpty()
            }
            .onFailure { error = it.userMessage() }
    }

    LaunchedEffect(type, period, reloadKey) {
        if (period.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            runCatchingCancellable { repo.weekList(type, period, 1) }
                .onSuccess { albums = it.list }
                .onFailure { error = it.userMessage() }
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        val week = options
        if (week != null && week.type.any { it.title.isNotBlank() }) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = week.type.filter { it.title.isNotBlank() }, key = { it.id }) { item ->
                    IosFilterPill(
                        selected = type == item.id,
                        label = item.title,
                        onClick = { type = item.id },
                    )
                }
            }
        }
        if (week != null && week.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items = week.categories, key = { _, item -> item.id }) { _, item ->
                    IosFilterPill(
                        selected = period == item.id,
                        label = item.time.ifBlank { item.title.ifBlank { "第 ${item.id} 期" } },
                        onClick = { period = item.id },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))
                error != null -> ErrorState(
                    message = error!!,
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { reloadKey++ },
                )
                // 週榜只有第一页，没有分页
                else -> AlbumGrid(
                    repo = repo,
                    albums = albums,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                    showTopBadge = false,
                )
            }
        }
    }
}
