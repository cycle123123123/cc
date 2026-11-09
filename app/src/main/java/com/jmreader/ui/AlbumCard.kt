package com.jmreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jmreader.data.AlbumBrief
import com.jmreader.data.JmRepository

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    repo: JmRepository,
    album: AlbumBrief,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    badge: String? = null,
    onVisible: (() -> Unit)? = null,
) {
    if (onVisible != null) {
        LaunchedEffect(album.id) { onVisible() }
    }
    val coverShape = RoundedCornerShape(10.dp)
    val clickModifier = if (onLongClick != null) {
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    }

    Column(modifier = clickModifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .shadow(2.dp, coverShape, clip = false)
                .clip(coverShape)
                .background(IosFillGray)
                .border(0.5.dp, Color(0x16000000), coverShape),
        ) {
            AsyncImage(
                model = repo.coverUrl(album.id, tall = true),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xBF000000)),
                            ),
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Text(
            text = album.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosLabelPrimary,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 1.dp, end = 1.dp),
        )

        val sub = album.author.ifBlank { album.category?.title.orEmpty() }
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                fontSize = 11.sp,
                color = IosLabelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp, start = 1.dp, end = 1.dp),
            )
        }
    }
}
