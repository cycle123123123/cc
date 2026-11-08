package com.jmreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(repo: JmRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val filter = remember { ContentFilter.of(context) }
    val rules by filter.rules.collectAsState()
    var input by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏蔽管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "对首页、搜索、发现都生效，收藏列表本身不过滤。关键词会匹配标题、作者、分类和详情页标签。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("隱藏已收藏的作品", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "已收錄 ${rules.favoritedAlbums.size} 部",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = rules.hideFavoritedAlbums,
                        onCheckedChange = filter::setHideFavoritedAlbums,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("隱藏已收藏的作者", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "已收錄 ${rules.favoritedAuthors.size} 位（可能过宽，默认关闭）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = rules.hideFavoritedAuthors,
                        onCheckedChange = filter::setHideFavoritedAuthors,
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !syncing,
                        onClick = {
                            syncing = true
                            progress = 0 to 0
                            scope.launch {
                                runCatching {
                                    filter.syncFromFavorites(repo) { loaded, total ->
                                        progress = loaded to total
                                    }
                                }
                                syncing = false
                            }
                        },
                    ) {
                        Text(if (syncing) "同步中…" else "同步收藏")
                    }
                    if (syncing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(start = 12.dp).size(20.dp),
                        )
                        Text(
                            text = " ${progress.first}/${progress.second}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (rules.syncedAt > 0) {
                    Text(
                        text = "上次同步：" + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(rules.syncedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("关键词 / 作者名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = input.isNotBlank(),
                        onClick = {
                            filter.addKeyword(input)
                            input = ""
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("添加")
                    }
                }
            }

            item { SectionTitle("关键词（${rules.keywords.size}）") }
            items(items = rules.keywords, key = { "k_$it" }) { keyword ->
                RuleRow(text = keyword) { filter.removeKeyword(keyword) }
            }

            item { SectionTitle("作者（${rules.authors.size}）") }
            items(items = rules.authors, key = { "a_$it" }) { author ->
                RuleRow(text = author) { filter.removeAuthor(author) }
            }

            item { SectionTitle("作品（${rules.albums.size}）") }
            items(items = rules.albums, key = { "b_$it" }) { id ->
                RuleRow(text = "作品 $id") { filter.removeAlbum(id) }
            }

            if (rules.isEmpty) {
                item {
                    Text(
                        text = "还没有屏蔽规则",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun RuleRow(text: String, onDelete: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text("删除") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}
