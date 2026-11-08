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
@Composable
private fun CategoryTab(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    metas: MutableMap<String, AlbumMeta>,
    metaRequests: MutableSet<String>,
    metaGate: Semaphore,
) {
    var categories by remember { mutableStateOf<List<CategoryNode>>(emptyList()) }
    var slug by remember { mutableStateOf("") }
    var subSlug by remember { mutableStateOf("") }
    var order by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filtersOpen by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blockRules by ContentFilter.of(context).rules.collectAsState()

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.categories() }
            .onSuccess { response ->
                categories = response.categories.filter { it.slug.isNotBlank() }
                slug = categories.firstOrNull()?.slug.orEmpty()
            }
            .onFailure { error = it.userMessage() }
    }

    suspend fun load(reset: Boolean) {
        val cValue = if (subSlug.isNotBlank()) "${slug}_$subSlug" else slug
        // 刷新/切筛选允许打断上一次请求；「載入更多」才需要防重复
        if (cValue.isBlank() || (loading && !reset)) return
        loading = true
        error = null
        try {
            runCatchingCancellable { repo.categoryList(cValue, order, if (reset) 1 else page) }
                .onSuccess { result ->
                    albums = if (reset) {
                        result.content
                    } else {
                        albums + result.content.filter { item -> albums.none { it.id == item.id } }
                    }
                    total = result.total
                    page = if (reset) 2 else page + 1
                }
                .onFailure { error = it.userMessage() }
        } finally {
            // 被 LaunchedEffect 取消时也要复位，否则 loading 永远卡住，后续点标签不再请求
            loading = false
        }
    }

    LaunchedEffect(slug) {
        subSlug = ""
    }

    LaunchedEffect(slug, subSlug, order) {
        if (slug.isNotBlank()) load(true)
    }

    Column(Modifier.fillMaxSize()) {
        val subCategories = categories.firstOrNull { it.slug == slug }?.subCategories.orEmpty()
        val summary = listOfNotNull(
            categories.firstOrNull { it.slug == slug }?.name,
            subCategories.firstOrNull { it.slug == subSlug }?.name,
            SORTS.firstOrNull { it.first == order }?.second,
        ).joinToString(" · ")

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { scope.launch { load(true) } },
                enabled = !loading,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
            TextButton(onClick = { filtersOpen = !filtersOpen }) {
                Text(if (filtersOpen) "收起" else "篩選")
            }
        }

        if (error != null && albums.isNotEmpty()) {
            Text(
                text = "更新失敗：$error（點此重試）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { load(true) } }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (filtersOpen) {
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = categories, key = { it.slug }) { item ->
                        FilterChip(
                            selected = slug == item.slug,
                            onClick = { slug = item.slug },
                            label = { Text(item.name) },
                        )
                    }
                }
            }

            if (subCategories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = subSlug.isBlank(),
                            onClick = { subSlug = "" },
                            label = { Text("全部") },
                        )
                    }
                    items(items = subCategories, key = { it.cid }) { sub ->
                        FilterChip(
                            selected = subSlug == sub.slug,
                            onClick = { subSlug = sub.slug },
                            label = { Text(sub.name) },
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = SORTS, key = { it.first }) { sort ->
                    FilterChip(
                        selected = order == sort.first,
                        onClick = { order = sort.first },
                        label = { Text(sort.second) },
                    )
                }
            }
        }

        if (total > 0) {
            Text(
                text = "共 $total 部",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading && albums.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                error != null && albums.isEmpty() -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = albums.filterNot { ContentFilter.matches(it, blockRules) }, key = { it.id }) { album ->
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { album.id.toLongOrNull()?.let(onOpen) },
                            badge = metaBadge(metas[album.id]),
                            onVisible = {
                                ensureAlbumMeta(repo, scope, metaGate, metas, metaRequests, album.id)
                            },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
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

private val SORTS = listOf(
    "" to "最新",
    "mv" to "最多點閱",
    "mp" to "最多圖片",
    "tf" to "最多愛心",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagTab(repo: JmRepository, onSearchTag: (String) -> Unit) {
    var blocks by remember { mutableStateOf<List<CategoryBlock>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.categories() }
            .onSuccess { blocks = it.blocks }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = blocks, key = { it.title }) { block ->
            Column {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    block.content.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = { onSearchTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekTab(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    metas: MutableMap<String, AlbumMeta>,
    metaRequests: MutableSet<String>,
    metaGate: Semaphore,
) {
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<WeekOptions?>(null) }
    var type by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val blockRules by ContentFilter.of(context).rules.collectAsState()

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.week() }
            .onSuccess { week ->
                options = week
                // 韩漫（hanman）的内容和日漫完全一样且标题为空，不显示
                type = week.type.firstOrNull { it.title.isNotBlank() }?.id.orEmpty()
                period = week.categories.firstOrNull()?.id.orEmpty()
            }
            .onFailure { error = it.userMessage() }
    }

    LaunchedEffect(type, period) {
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = week.type.filter { it.title.isNotBlank() }, key = { it.id }) { item ->
                    FilterChip(
                        selected = type == item.id,
                        onClick = { type = item.id },
                        label = { Text(item.title) },
                    )
                }
            }
        }
        if (week != null && week.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items = week.categories, key = { _, item -> item.id }) { _, item ->
                    FilterChip(
                        selected = period == item.id,
                        onClick = { period = item.id },
                        label = { Text(item.time.ifBlank { item.title.ifBlank { "第 ${item.id} 期" } }) },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = albums.filterNot { ContentFilter.matches(it, blockRules) }, key = { it.id }) { album ->
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { album.id.toLongOrNull()?.let(onOpen) },
                            badge = metaBadge(metas[album.id]),
                            onVisible = {
                                ensureAlbumMeta(repo, scope, metaGate, metas, metaRequests, album.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendTab(
    repo: JmRepository,
    onOpen: (Long) -> Unit,
    metas: MutableMap<String, AlbumMeta>,
    metaRequests: MutableSet<String>,
    metaGate: Semaphore,
) {
    var albums by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blockRules by ContentFilter.of(context).rules.collectAsState()

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                runCatchingCancellable { repo.randomRecommend() }
                    .onSuccess { albums = it }
                    .onFailure { error = it.userMessage() }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "隨機推薦",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { load() }, enabled = !loading) { Text("換一批") }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading && albums.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && albums.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重試")
                    }
                }
                albums.isEmpty() -> Text(
                    text = "沒有推薦內容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = albums.filterNot { ContentFilter.matches(it, blockRules) }, key = { it.id }) { album ->
                        AlbumCard(
                            repo = repo,
                            album = album,
                            onClick = { album.id.toLongOrNull()?.let(onOpen) },
                            badge = metaBadge(metas[album.id]),
                            onVisible = {
                                ensureAlbumMeta(repo, scope, metaGate, metas, metaRequests, album.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
