package com.jmreader.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jmreader.core.runCatchingCancellable
import com.jmreader.data.JmRepository
import com.jmreader.data.model.ComicRead
import com.jmreader.data.model.PageImage
import com.jmreader.ui.theme.IosFillGray
import com.jmreader.ui.theme.IosLabelPrimary

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
