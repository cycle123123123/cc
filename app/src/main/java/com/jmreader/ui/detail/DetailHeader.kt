package com.jmreader.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jmreader.core.formatCount
import com.jmreader.data.JmRepository
import com.jmreader.data.local.LastRead
import com.jmreader.data.model.AlbumBrief
import com.jmreader.data.model.AlbumDetail
import com.jmreader.data.model.ChapterEntry
import com.jmreader.ui.common.formatEpochSeconds
import com.jmreader.ui.components.AlbumCard
import com.jmreader.ui.theme.IosBlue
import com.jmreader.ui.theme.IosBlueSoft
import com.jmreader.ui.theme.IosCardWhite
import com.jmreader.ui.theme.IosFillGray
import com.jmreader.ui.theme.IosGroupedBg
import com.jmreader.ui.theme.IosLabelPrimary
import com.jmreader.ui.theme.IosLabelSecondary
import com.jmreader.ui.theme.IosPink
import com.jmreader.ui.theme.IosSeparator

/** iOS App Store 风格四栏横向数据条（上下细线 + 竖向短分割线） */
@Composable
internal fun DetailStatsRow(
    pages: String,
    views: String,
    likes: String,
    comments: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 0.5.dp, color = IosSeparator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailStatCell(top = "頁數", value = pages, sub = "頁", modifier = Modifier.weight(1f))
            VerticalShortDivider()
            DetailStatCell(top = "點閱", value = views, sub = "次", modifier = Modifier.weight(1f))
            VerticalShortDivider()
            DetailStatCell(top = "喜愛", value = likes, sub = "讚", modifier = Modifier.weight(1f))
            VerticalShortDivider()
            DetailStatCell(top = "討論", value = comments, sub = "則", modifier = Modifier.weight(1f))
        }
        HorizontalDivider(thickness = 0.5.dp, color = IosSeparator)
    }
}

@Composable
private fun VerticalShortDivider() {
    Box(
        Modifier
            .width(0.5.dp)
            .height(28.dp)
            .background(IosSeparator),
    )
}

@Composable
private fun DetailStatCell(top: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = top,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosLabelSecondary,
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = IosLabelPrimary,
            modifier = Modifier.padding(vertical = 1.dp),
        )
        Text(
            text = sub,
            fontSize = 10.sp,
            color = IosLabelSecondary,
        )
    }
}

@Composable
internal fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    DetailStatCell(top = label, value = value, sub = "", modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Header(
    repo: JmRepository,
    album: AlbumDetail,
    isFavorite: Boolean,
    isSingleChapter: Boolean = false,
    onReadFirst: () -> Unit,
    onDownloadFirst: () -> Unit = {},
    onOpenAuthor: (String) -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
    onBlockTags: () -> Unit,
    onBlockAuthor: (String) -> Unit,
    resumeChapter: LastRead? = null,
    resumeChapterNumber: Int? = null,
    onContinue: (() -> Unit)? = null,
) {
    val coverShape = RoundedCornerShape(12.dp)
    // 只有記錄落在當前目錄裡（DetailScreen 已校驗）才進入續讀態
    val resuming = resumeChapter != null && onContinue != null
    val readLabel = when {
        !resuming -> "開始閱讀"
        isSingleChapter -> "繼續閱讀"
        resumeChapterNumber != null && resumeChapterNumber > 0 -> "繼續第${resumeChapterNumber}話"
        else -> "繼續閱讀"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = repo.coverUrl(album.id.toString()),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(114.dp)
                .aspectRatio(3f / 4f)
                .shadow(4.dp, coverShape, clip = false)
                .clip(coverShape)
                .background(IosFillGray)
                .border(0.5.dp, Color(0x18000000), coverShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = album.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = IosLabelPrimary,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (album.author.isNotEmpty()) {
                val firstAuthor = album.author.joinToString(" ").trim()
                Text(
                    text = firstAuthor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = IosBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenAuthor(firstAuthor) },
                )
            }
            if (album.addTime.isNotBlank()) {
                Text(
                    text = "上架於 ${formatEpochSeconds(album.addTime)} · JM${album.id}",
                    fontSize = 11.sp,
                    color = IosLabelSecondary,
                )
            }

            Spacer(Modifier.height(4.dp))

            // iOS App Store 主操作栏：「閱讀」蓝色胶囊按钮 + 收藏/点赞圆按钮
            // 单话本会多一个「下载」，按钮总宽度可能超过封面右侧的可用宽度，
            // 用 FlowRow 让放不下的按钮换行，而不是被裁到屏幕外。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(IosBlue)
                        .clickable(onClick = onContinue ?: onReadFirst)
                        .padding(horizontal = if (resuming) 16.dp else 20.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = readLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 單話本：直接给一个「下載」入口，省得进目录
                if (isSingleChapter) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(IosBlueSoft)
                            .clickable(onClick = onDownloadFirst)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "下載",
                                modifier = Modifier.size(15.dp),
                                tint = IosBlue,
                            )
                            Text(
                                text = " 下載",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosBlue,
                            )
                        }
                    }
                }

                // 收藏按钮
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isFavorite) IosBlueSoft else IosFillGray)
                        .clickable(onClick = onFavorite)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "收藏",
                            modifier = Modifier.size(16.dp),
                            tint = if (isFavorite) IosBlue else IosLabelSecondary,
                        )
                        Text(
                            text = if (isFavorite) " 已藏" else " 收藏",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFavorite) IosBlue else IosLabelPrimary,
                        )
                    }
                }

                // 点赞按钮
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (album.liked) Color(0xFFFFE5EA) else IosFillGray)
                        .clickable(onClick = onLike),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ThumbUp,
                        contentDescription = "點讚",
                        modifier = Modifier.size(15.dp),
                        tint = if (album.liked) IosPink else IosLabelSecondary,
                    )
                }
            }

        }
    }
}

/** 轻量级屏蔽操作：整行放在封面区下方、统计条上方。 */
@Composable
internal fun BlockActions(
    album: AlbumDetail,
    onBlockAuthor: (String) -> Unit,
    onBlockTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "屏蔽作者",
            fontSize = 11.sp,
            color = IosLabelSecondary,
            modifier = Modifier
                .clickable {
                    val name = album.author.joinToString(" ").trim()
                    if (name.isNotBlank()) {
                        onBlockAuthor(name)
                    }
                }
                .padding(vertical = 2.dp),
        )
        Text(
            text = "屏蔽標籤",
            fontSize = 11.sp,
            color = IosLabelSecondary,
            modifier = Modifier
                .clickable(onClick = onBlockTags)
                .padding(vertical = 2.dp),
        )
    }
}

/** 「详情」页顶部整张卡片：封面/操作栏 + 数据条 + 内页预览。 */
@Composable
internal fun DetailHeaderCard(
    repo: JmRepository,
    album: AlbumDetail,
    isFavorite: Boolean,
    isSingleChapter: Boolean,
    firstChapter: ChapterEntry?,
    forumTotal: Int,
    resumeChapter: LastRead?,
    resumeChapterNumber: Int?,
    onReadFirst: () -> Unit,
    onDownloadFirst: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
    onBlockTags: () -> Unit,
    onBlockAuthor: (String) -> Unit,
    onContinue: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Header(
            repo = repo,
            album = album,
            isFavorite = isFavorite,
            isSingleChapter = isSingleChapter,
            onReadFirst = onReadFirst,
            onDownloadFirst = onDownloadFirst,
            onOpenAuthor = onOpenAuthor,
            onFavorite = onFavorite,
            onLike = onLike,
            onBlockTags = onBlockTags,
            onBlockAuthor = onBlockAuthor,
            resumeChapter = resumeChapter,
            resumeChapterNumber = resumeChapterNumber,
            onContinue = onContinue,
        )

        BlockActions(
            album = album,
            onBlockAuthor = onBlockAuthor,
            onBlockTags = onBlockTags,
            modifier = Modifier.padding(top = 10.dp),
        )

        DetailStatsRow(
            pages = album.totalPhotos.toString(),
            views = formatCount(album.viewCount),
            likes = formatCount(album.likes),
            comments = formatCount(forumTotal),
            modifier = Modifier.padding(top = 14.dp),
        )

        firstChapter?.let {
            PreviewStrip(
                repo = repo,
                albumId = album.id,
                chapterId = it.id,
            )
        }
    }
}

/** 「详情」页的作者 / 标签 / 简介卡片。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun DetailInfoCard(
    album: AlbumDetail,
    onOpenAuthor: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    onBlockTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (album.author.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "作者",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosLabelSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 接口会把作者名按空格拆成多项（如 ["STUDIO","J"]），这里合并回一个
                    val authorName = album.author.joinToString(" ").trim()
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(IosBlueSoft)
                            .clickable { onOpenAuthor(authorName) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = authorName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IosBlue,
                        )
                    }
                }
            }
        }
        if (album.tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "標籤",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosLabelSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    album.tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(IosGroupedBg)
                                .combinedClickable(
                                    onClick = { onSearchTag(tag) },
                                    onLongClick = { onBlockTag(tag) },
                                )
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = IosLabelPrimary,
                            )
                        }
                    }
                }
            }
        }
        if (album.description.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "簡介",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosLabelSecondary,
                )
                Text(
                    text = album.description,
                    fontSize = 13.sp,
                    color = IosLabelPrimary,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

/** 「同作者作品」横滑卡片区。 */
@Composable
internal fun SameAuthorSection(
    repo: JmRepository,
    works: List<AlbumBrief>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = "同作者作品",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = IosLabelPrimary,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(items = works, key = { it.id }) { work ->
                Box(Modifier.width(96.dp)) {
                    AlbumCard(
                        repo = repo,
                        album = work,
                        onClick = {
                            work.id.toLongOrNull()?.let(onOpen)
                        },
                        compact = true,
                    )
                }
            }
        }
    }
}

/** 批量屏蔽标签的勾选弹窗。 */
@Composable
internal fun BlockTagsDialog(
    tags: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("屏蔽標籤") },
        text = {
            Column {
                tags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected.contains(tag),
                            onCheckedChange = { checked ->
                                onSelectedChange(if (checked) selected + tag else selected - tag)
                            },
                        )
                        Text(tag)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
