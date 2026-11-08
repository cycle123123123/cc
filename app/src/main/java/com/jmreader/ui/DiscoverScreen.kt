package com.jmreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmreader.data.userMessage
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.CategoryBlock
import com.jmreader.data.CategoryNode
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.WeekOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    // 发现页共用一份补充信息（点击量/爱心/日文），切 tab 不用重新拉
    val metas = remember { mutableStateMapOf<String, AlbumMeta>() }
    val metaRequests = remember { mutableSetOf<String>() }
    val metaGate = remember { Semaphore(2) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("發現") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("分類") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("標籤") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("週榜") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("推薦") })
            }
            when (tab) {
                0 -> CategoryTab(repo, onOpen, metas, metaRequests, metaGate)
                1 -> TagTab(repo, onSearchTag)
                2 -> WeekTab(repo, onOpen, metas, metaRequests, metaGate)
                else -> RecommendTab(repo, onOpen, metas, metaRequests, metaGate)
            }
        }
    }
}

/** 分类浏览：/categories/filter?c=<slug>&o=<排序>&page= */
