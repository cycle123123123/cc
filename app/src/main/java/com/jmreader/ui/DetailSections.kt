package com.jmreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.AlbumDetail
import com.jmreader.data.ChapterEntry
import com.jmreader.data.ComicRead
import com.jmreader.data.CommentItem
import com.jmreader.data.ContentFilter
import com.jmreader.data.JmRepository
import com.jmreader.data.PageImage

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
            DetailStatCell(top = "頁數", value = pages, sub = "P", modifier = Modifier.weight(1f))
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

@Composable
internal fun ChapterRow(
    chapter: ChapterEntry,
    onRead: () -> Unit,
    onDownload: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .clickable(onClick = onRead)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapter.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = IosLabelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .background(IosBlueSoft)
                .clickable(onClick = onRead)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                text = "閱讀",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = IosBlue,
            )
        }
        IconButton(onClick = onDownload) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "下載",
                tint = IosBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun CommentRow(
    comment: CommentItem,
    canDelete: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReply = comment.parentCid != "0" && comment.parentCid.isNotBlank()
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 20.dp else 0.dp)
            .clip(shape)
            .background(IosCardWhite)
            .border(0.5.dp, Color(0x12000000), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = comment.nickname.ifBlank { comment.username },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = IosBlue,
            )
            Text(
                text = comment.addtime,
                fontSize = 11.sp,
                color = IosLabelSecondary,
            )
        }
        Text(
            text = stripHtml(comment.content),
            fontSize = 14.sp,
            color = IosLabelPrimary,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onLike)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = null,
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " ${comment.likes}",
                    fontSize = 12.sp,
                    color = IosLabelSecondary,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onReply)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Reply,
                    contentDescription = null,
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " 回覆",
                    fontSize = 12.sp,
                    color = IosLabelSecondary,
                )
            }
            if (canDelete) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDelete)
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = " 刪除",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
internal fun Header(
    repo: JmRepository,
    album: AlbumDetail,
    isFavorite: Boolean,
    onReadFirst: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
    onBlockTags: () -> Unit,
) {
    val context = LocalContext.current
    val coverShape = RoundedCornerShape(12.dp)
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
                val firstAuthor = album.author.first()
                Text(
                    text = album.author.joinToString(" · "),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(IosBlue)
                        .clickable(onClick = onReadFirst)
                        .padding(horizontal = 20.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "開始閱讀",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
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

            // 轻量级屏蔽操作
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val filter = ContentFilter.of(context)
                Text(
                    text = "屏蔽作者",
                    fontSize = 11.sp,
                    color = IosLabelSecondary,
                    modifier = Modifier
                        .clickable {
                            album.author.firstOrNull()?.let {
                                filter.addAuthor(it)
                                android.widget.Toast.makeText(context, "已屏蔽作者 $it", android.widget.Toast.LENGTH_SHORT).show()
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
    }
}

/** 详情页顶部预览：从第一话随机取几页，还原后等比裁切；点开是全屏看图。 */
@Composable
internal fun PreviewStrip(
    repo: JmRepository,
    albumId: Long,
    chapterId: Long,
) {
    var comic by remember(chapterId) { mutableStateOf<ComicRead?>(null) }
    var thumbs by remember(chapterId) { mutableStateOf<List<Pair<PageImage, Bitmap>>>(emptyList()) }
    var loading by remember(chapterId) { mutableStateOf(true) }
    var viewerIndex by remember(chapterId) { mutableStateOf<Int?>(null) }

    LaunchedEffect(chapterId) {
        loading = true
        val data = runCatchingCancellable { repo.comicRead(chapterId) }.getOrNull()
        if (data != null) {
            val picked = data.images.shuffled().take(PREVIEW_COUNT).sortedBy { it.page }
            thumbs = picked.mapNotNull { page ->
                runCatchingCancellable {
                    repo.loadPage(
                        cid = chapterId,
                        page = page.page,
                        url = page.image,
                        aid = albumId,
                        scrambleId = data.scrambleId,
                        targetWidth = PREVIEW_WIDTH_PX,
                    )
                }.getOrNull()?.let { page to it }
            }
            comic = data
        }
        loading = false
    }

    if (thumbs.isEmpty() && !loading) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = "內頁預覽",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = IosLabelPrimary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shape = RoundedCornerShape(10.dp)
            if (thumbs.isEmpty()) {
                repeat(PREVIEW_COUNT) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(124.dp)
                            .clip(shape)
                            .background(IosFillGray),
                    )
                }
            } else {
                thumbs.forEachIndexed { index, (_, bitmap) ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "預覽",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(124.dp)
                            .clip(shape)
                            .border(0.5.dp, Color(0x16000000), shape)
                            .clickable { viewerIndex = index },
                    )
                }
            }
        }
    }

    val data = comic
    val index = viewerIndex
    if (data != null && index != null && thumbs.isNotEmpty()) {
        PreviewViewer(
            repo = repo,
            albumId = albumId,
            chapterId = chapterId,
            scrambleId = data.scrambleId,
            pages = thumbs.map { it.first },
            initialIndex = index.coerceIn(0, thumbs.lastIndex),
            onDismiss = { viewerIndex = null },
        )
    }
}

/** 全屏看图：左右滑动切页，按屏幕宽度重新解码，保证清晰度。 */
@Composable
internal fun PreviewViewer(
    repo: JmRepository,
    albumId: Long,
    chapterId: Long,
    scrambleId: Long,
    pages: List<PageImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val screenWidth = remember { context.resources.displayMetrics.widthPixels }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pager = rememberPagerState(initialPage = initialIndex, pageCount = { pages.size })
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { pageIndex ->
                val page = pages[pageIndex]
                var bitmap by remember(page.page) { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(page.page) {
                    bitmap = runCatchingCancellable {
                        repo.loadPage(
                            cid = chapterId,
                            page = page.page,
                            url = page.image,
                            aid = albumId,
                            scrambleId = scrambleId,
                            targetWidth = screenWidth,
                        )
                    }.getOrNull()
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image != null) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "第 ${page.page} 頁",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 ${pages[pager.currentPage].page} 頁",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "關閉", tint = Color.White)
                }
            }
        }
    }
}

internal const val PREVIEW_COUNT = 4
internal const val PREVIEW_WIDTH_PX = 220

/** 评论内容带 HTML（<div>、<br> 等），展示前转成纯文本。 */
internal fun stripHtml(raw: String): String =
    runCatchingCancellable {
        android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }.getOrDefault(raw)
