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
import com.jmreader.ui.common.RefreshPolicy
import com.jmreader.ui.theme.IosBlue

@Composable
internal fun WeekTab(
    repo: JmRepository,
    state: WeekState,
    onOpen: (Long) -> Unit,
) {
    var options by state.options
    var type by state.type
    var period by state.period
    var albums by state.albums
    var loading by state.loading
    var error by state.error
    var reloadKey by state.reloadKey

    LaunchedEffect(Unit) {
        val stale = RefreshPolicy.isStale(
            state.optionsAt,
            System.currentTimeMillis(),
            RefreshPolicy.ListTtlMs,
        )
        if (options != null && !stale) return@LaunchedEffect
        runCatchingCancellable { repo.week() }
            .onSuccess { week ->
                options = week
                state.optionsAt = System.currentTimeMillis()
                if (type.isBlank()) {
                    type = week.type.firstOrNull { it.title.isNotBlank() }?.id.orEmpty()
                }
                if (period.isBlank()) period = week.categories.firstOrNull()?.id.orEmpty()
            }
            .onFailure { if (options == null) error = it.userMessage() }
    }

    // 期数/类型没变、数据也没过期时，切回 tab 不重复请求
    LaunchedEffect(type, period, reloadKey) {
        if (period.isBlank()) return@LaunchedEffect
        val key = "$type|$period|$reloadKey"
        val stale = RefreshPolicy.isStale(
            state.loadedAt,
            System.currentTimeMillis(),
            RefreshPolicy.ListTtlMs,
        )
        if (state.lastRequestKey == key && !stale) return@LaunchedEffect
        state.lastRequestKey = key
        loading = true
        error = null
        try {
            runCatchingCancellable { repo.weekList(type, period, 1) }
                .onSuccess {
                    albums = it.list
                    state.loadedAt = System.currentTimeMillis()
                }
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
                loading && albums.isEmpty() -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))
                error != null && albums.isEmpty() -> ErrorState(
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
