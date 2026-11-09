package com.jmreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        containerColor = IosGroupedBg,
        topBar = {
            TopAppBar(
                title = { Text("屏蔽管理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = IosBlue,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = IosGroupedBg,
                    titleContentColor = IosLabelPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "對首頁、搜尋、發現皆生效，收藏列表本身不過濾。關鍵字會比對標題、作者、分類與詳情頁標籤。",
                    fontSize = 12.sp,
                    color = IosLabelSecondary,
                )
            }

            item {
                val shape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(IosCardWhite)
                        .border(0.5.dp, Color(0x12000000), shape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("隱藏已收藏的作品", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "已收錄 ${rules.favoritedAlbums.size} 部",
                                fontSize = 11.sp,
                                color = IosLabelSecondary,
                            )
                        }
                        Switch(
                            checked = rules.hideFavoritedAlbums,
                            onCheckedChange = filter::setHideFavoritedAlbums,
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759)),
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = IosSeparator)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("隱藏已收藏的作者", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "已收錄 ${rules.favoritedAuthors.size} 位（可能過寬，默認關閉）",
                                fontSize = 11.sp,
                                color = IosLabelSecondary,
                            )
                        }
                        Switch(
                            checked = rules.hideFavoritedAuthors,
                            onCheckedChange = filter::setHideFavoritedAuthors,
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759)),
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = IosSeparator)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = !syncing,
                            shape = CircleShape,
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
                                color = IosBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(start = 12.dp).size(20.dp),
                            )
                            Text(
                                text = " ${progress.first}/${progress.second}",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        } else if (rules.syncedAt > 0) {
                            Text(
                                text = "上次同步：" + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(rules.syncedAt)),
                                fontSize = 11.sp,
                                color = IosLabelSecondary,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }

            item {
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(IosCardWhite)
                        .border(0.5.dp, Color(0x12000000), shape)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("新增關鍵字或作者名") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = input.isNotBlank(),
                        shape = CircleShape,
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

            if (rules.keywords.isNotEmpty()) {
                item { SectionTitle("關鍵字（${rules.keywords.size}）") }
                items(items = rules.keywords, key = { "k_$it" }) { keyword ->
                    RuleRow(text = keyword) { filter.removeKeyword(keyword) }
                }
            }

            if (rules.authors.isNotEmpty()) {
                item { SectionTitle("作者（${rules.authors.size}）") }
                items(items = rules.authors, key = { "a_$it" }) { author ->
                    RuleRow(text = author) { filter.removeAuthor(author) }
                }
            }

            if (rules.albums.isNotEmpty()) {
                item { SectionTitle("作品（${rules.albums.size}）") }
                items(items = rules.albums, key = { "b_$it" }) { id ->
                    RuleRow(text = "作品 $id") { filter.removeAlbum(id) }
                }
            }

            if (rules.isEmpty) {
                item {
                    Text(
                        text = "目前還沒有屏蔽規則",
                        color = IosLabelSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
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
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = IosLabelSecondary,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

@Composable
private fun RuleRow(text: String, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, color = IosLabelPrimary, modifier = Modifier.weight(1f))
        TextButton(onClick = onDelete) {
            Text("刪除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
        }
    }
}
