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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jmreader.ui.common.rememberToaster
import com.jmreader.ui.reader.ZoomablePage
import com.jmreader.ui.reader.savePageToGallery
import com.jmreader.ui.theme.IosFillGray
import com.jmreader.ui.theme.IosHairline
import com.jmreader.ui.theme.IosLabelPrimary

import kotlinx.coroutines.launch
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
                            .border(0.5.dp, IosHairline, shape)
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

/** 全屏看图：左右滑动切页 + 双指/双击缩放 + 一键保存。 */
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
    val toast = rememberToaster()
    val scope = rememberCoroutineScope()
    val screenWidth = remember { context.resources.displayMetrics.widthPixels }
    val loadedBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var zoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pager = rememberPagerState(initialPage = initialIndex, pageCount = { pages.size })
        val currentPage = pages.getOrNull(pager.currentPage)

        fun saveCurrent(bmp: Bitmap?, pageNumber: Int) {
            if (bmp == null) return
            scope.launch {
                val res = savePageToGallery(context, bmp, chapterId, pageNumber)
                toast(
                    res.fold(
                        onSuccess = { "已保存到相冊 Pictures/JMReader" },
                        onFailure = { "保存失敗：${it.message ?: "未知錯誤"}" },
                    ),
                )
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                val page = pages[pageIndex]
                var bitmap by remember(page.page) { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(page.page) {
                    val loaded = runCatchingCancellable {
                        repo.loadPage(
                            cid = chapterId,
                            page = page.page,
                            url = page.image,
                            aid = albumId,
                            scrambleId = scrambleId,
                            targetWidth = screenWidth,
                        )
                    }.getOrNull()
                    bitmap = loaded
                    if (loaded != null) loadedBitmaps[page.page] = loaded
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image != null) {
                        ZoomablePage(
                            bitmap = image,
                            description = "第 ${page.page} 頁",
                            onTap = {},
                            onZoomChanged = { zoomed = it },
                            onLongPress = { bmp -> saveCurrent(bmp, page.page) },
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
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC1C1C1E))
                    .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 ${currentPage?.page ?: 1} 頁",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                val currentBitmap = currentPage?.let { loadedBitmaps[it.page] }
                if (currentBitmap != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x330A84FF))
                            .clickable { saveCurrent(currentBitmap, currentPage.page) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text("保存", color = Color(0xFF0A84FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "關閉", tint = Color.White)
                }
            }
        }
    }
}

internal const val PREVIEW_COUNT = 4
internal const val PREVIEW_WIDTH_PX = 220
