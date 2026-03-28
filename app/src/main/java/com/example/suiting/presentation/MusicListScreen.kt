package com.example.suiting.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard

@Composable
fun MusicListScreen(
    musicList: List<MusicItem>,
    isLoading: Boolean,
    onMusicClick: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onRefresh,
                buttonSize = EdgeButtonSize.Medium,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新歌曲列表",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        if (isLoading) {
            // 加载中：居中显示转圈
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        } else {
            ScalingLazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize()
            ) {
                item {
                    ListHeader {
                        Text(
                            text = "随听",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (musicList.isEmpty()) {
                    item {
                        Text(
                            text = "未找到音乐\n请检查存储权限\n或点击底部刷新",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    musicListItems(musicList, onMusicClick)
                }
            }
        }
    }
}

private fun ScalingLazyListScope.musicListItems(
    musicList: List<MusicItem>,
    onMusicClick: (Int) -> Unit
) {
    items(
        count = musicList.size,
        key = { i -> musicList[i].id }
    ) { index ->
        val music = musicList[index]
        TitleCard(
            title = {
                Text(
                    text = music.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            onClick = { onMusicClick(index) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = music.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
