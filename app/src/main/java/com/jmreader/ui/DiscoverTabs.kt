package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.CategoryBlock
import com.jmreader.data.CategoryNode
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.WeekOptions
import com.jmreader.data.userMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

@Composable
internal fun CategoryTab(
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
        val summary = buildList {
            categories.firstOrNull { it.slug == slug }?.name?.let(::add)
            subCategories.firstOrNull { it.slug == subSlug }?.name?.let(::add)
            SORTS.firstOrNull { it.first == order }?.second?.let(::add)
            if (total > 0) add("共 $total 部")
        }.joinToString(" · ")

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                fontSize = 12.sp,
                color = IosLabelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { scope.launch { load(true) } },
                enabled = !loading,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = IosBlue, modifier = Modifier.size(18.dp))
            }
            TextButton(onClick = { filtersOpen = !filtersOpen }) {
                Text(
                    text = if (filtersOpen) "收起篩選" else "展開篩選",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IosBlue,
                )
            }
        }

        if (error != null && albums.isNotEmpty()) {
            Text(
                text = "更新失敗：$error（點此重試）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { load(true) } }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (filtersOpen) {
            Column(
                modifier = Modifier.padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = categories, key = { it.slug }) { item ->
                            IosFilterPill(
                                selected = slug == item.slug,
                                label = item.name,
                                onClick = { slug = item.slug },
                            )
                        }
                    }
                }

                if (subCategories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            IosFilterPill(
                                selected = subSlug.isBlank(),
                                label = "全部",
                                onClick = { subSlug = "" },
                            )
                        }
                        items(items = subCategories, key = { it.cid }) { sub ->
                            IosFilterPill(
                                selected = subSlug == sub.slug,
                                label = sub.name,
                                onClick = { subSlug = sub.slug },
                            )
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = SORTS, key = { it.first }) { sort ->
                        IosFilterPill(
                            selected = order == sort.first,
                            label = sort.second,
                            onClick = { order = sort.first },
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading && albums.isEmpty() -> CircularProgressIndicator(color = IosBlue, modifier = Modifier.align(Alignment.Center))

                error != null && albums.isEmpty() -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                CircularProgressIndicator(color = IosBlue, strokeWidth = 2.5.dp)
                            } else {
                                Button(
                                    onClick = { scope.launch { load(false) } },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = IosCardWhite,
                                        contentColor = IosBlue,
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                                ) {
                                    Text("載入更多", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal val SORTS = listOf(
    "" to "最新",
    "mv" to "最多點閱",
    "mp" to "最多圖片",
    "tf" to "最多愛心",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagTab(repo: JmRepository, onSearchTag: (String) -> Unit) {
    var blocks by remember { mutableStateOf<List<CategoryBlock>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.categories() }
            .onSuccess { blocks = it.blocks }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IosBlue)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = blocks, key = { it.title }) { block ->
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(IosCardWhite)
                    .border(0.5.dp, Color(0x12000000), shape)
                    .padding(14.dp),
            ) {
                Text(
                    text = block.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosLabelPrimary,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    block.content.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(IosGroupedBg)
                                .clickable { onSearchTag(tag) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = tag,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = IosLabelPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeekTab(
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
                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
internal fun RecommendTab(
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
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
