package com.jmreader.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.jmreader.data.JmRepository
import com.jmreader.data.model.AlbumBrief
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.model.userMessage
import com.jmreader.ui.components.AlbumGrid
import com.jmreader.ui.common.RefreshPolicy
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosBlueSoft
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary

@Composable
internal fun RecommendTab(
    repo: JmRepository,
    state: RecommendState,
    onOpen: (Long) -> Unit,
) {
    var albums by state.albums
    var loading by state.loading
    var error by state.error
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                runCatchingCancellable { repo.randomRecommend() }
                    .onSuccess {
                        albums = it
                        state.loadedAt = System.currentTimeMillis()
                    }
                    .onFailure { error = it.userMessage() }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val stale = RefreshPolicy.isStale(
            state.loadedAt,
            System.currentTimeMillis(),
            RefreshPolicy.ListTtlMs,
        )
        if (albums.isNotEmpty() && !stale) {
            loading = false
            return@LaunchedEffect
        }
        load()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "為你推薦",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = IosLabelPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(IosBlueSoft)
                    .clickable(enabled = !loading) { load() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "換一批",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosBlue,
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading && albums.isEmpty() -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))
                error != null && albums.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { load() }, shape = CircleShape, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重試")
                    }
                }
                albums.isEmpty() -> Text(
                    text = "沒有推薦內容",
                    color = IosLabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                // 推荐是整批随机结果，没有分页
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
