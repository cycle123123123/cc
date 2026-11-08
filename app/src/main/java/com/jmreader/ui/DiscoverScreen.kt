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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jmreader.data.userMessage
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumBrief
import com.jmreader.data.CategoryBlock
import com.jmreader.data.CategoryNode
import com.jmreader.data.CreatorAuthor
import com.jmreader.data.JmRepository
import com.jmreader.data.PromoteSection
import com.jmreader.data.WeekOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    repo: JmRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenAuthor: (String, String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("發現") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("作者") })
            }
            when (tab) {
                0 -> CategoryTab(repo, onOpen)
                1 -> TagTab(repo, onSearchTag)
                2 -> WeekTab(repo, onOpen)
                3 -> RecommendTab(repo, onOpen)
                else -> AuthorTab(repo, onOpenAuthor)
            }
        }
    }
}

/** 分类浏览：/categories/filter?c=<slug>&o=<排序>&page= */
@Composable
private fun CategoryTab(repo: JmRepository, onOpen: (Long) -> Unit) {
    var categories by remember { mutableStateOf<List<CategoryNode>>(emptyList()) }
    var slug by remember { mutableStateOf("") }
    var subSlug by remember { mutableStateOf("") }
    var order by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        if (cValue.isBlank() || loading) return
        loading = true
        error = null
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
        loading = false
    }

    LaunchedEffect(slug) {
        subSlug = ""
    }

    LaunchedEffect(slug, subSlug, order) {
        if (slug.isNotBlank()) load(true)
    }

    Column(Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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

        val subCategories = categories.firstOrNull { it.slug == slug }?.subCategories.orEmpty()
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
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = albums, key = { it.id }) { album ->
                        AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
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
private fun WeekTab(repo: JmRepository, onOpen: (Long) -> Unit) {
    var options by remember { mutableStateOf<WeekOptions?>(null) }
    var type by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.week() }
            .onSuccess { week ->
                options = week
                type = week.type.firstOrNull()?.id.orEmpty()
                category = week.categories.firstOrNull()?.id.orEmpty()
            }
            .onFailure { error = it.message }
    }

    LaunchedEffect(type, category) {
        if (category.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        runCatchingCancellable { repo.weekList(type, category, 1) }
            .onSuccess { albums = it.list }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        val week = options
        if (week != null && week.type.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = week.type, key = { it.id }) { item ->
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
                        selected = category == item.id,
                        onClick = { category = item.id },
                        label = { Text(item.time.ifBlank { item.title }) },
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
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = albums, key = { it.id }) { album ->
                        AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendTab(repo: JmRepository, onOpen: (Long) -> Unit) {
    var sections by remember { mutableStateOf<List<PromoteSection>>(emptyList()) }
    var random by remember { mutableStateOf<List<AlbumBrief>>(emptyList()) }
    var showRandom by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.promote() }
            .onSuccess { sections = it }
            .onFailure { error = it.userMessage() }
    }

    fun loadRandom() {
        loading = true
        error = null
        scope.launch {
            runCatchingCancellable { repo.randomRecommend() }
                .onSuccess {
                    random = it
                    showRandom = true
                }
                .onFailure { error = it.userMessage() }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { loadRandom() }) { Text("隨機推薦") }
            if (showRandom) {
                TextButton(onClick = { showRandom = false }) { Text("返回榜單") }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && sections.isEmpty() -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                showRandom -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = random, key = { it.id }) { album ->
                        AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items = sections, key = { it.id }) { section ->
                        Column(Modifier.padding(bottom = 12.dp)) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(items = section.content, key = { it.id }) { album ->
                                    Box(Modifier.width(120.dp)) {
                                        AlbumCard(repo = repo, album = album, onClick = { album.id.toLongOrNull()?.let(onOpen) })
                                    }
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
private fun AuthorTab(repo: JmRepository, onOpenAuthor: (String, String) -> Unit) {
    var authors by remember { mutableStateOf<List<CreatorAuthor>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { repo.creatorAuthors(1) }
            .onSuccess { authors = it }
            .onFailure { error = it.userMessage() }
        loading = false
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null -> Text(
            text = error!!,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(items = authors, key = { it.id }) { author ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAuthor(author.id, author.name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = repo.assetUrl(author.avatar),
                        contentDescription = author.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(author.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = author.updateDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
