package com.example.suiting.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    music: MusicItem,
    isPlaying: Boolean,
    progress: Float,
    volume: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLyrics by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 进度环（贴边，不检测中央拖动手势）──────────────────────────
        WaveProgressIndicator(
            progress = progress,
            isPlaying = isPlaying,
            onSeek = onSeek,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 18f,
            progressColor = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )

        // ── 歌词页 ─────────────────────────────────────────────────────
        AnimatedVisibility(visible = showLyrics, enter = fadeIn(), exit = fadeOut()) {
            LyricsView(
                musicTitle = music.title,
                onBack = { showLyrics = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 播放控制（中央区域允许左滑返回）───────────────────────────
        AnimatedVisibility(visible = !showLyrics, enter = fadeIn(), exit = fadeOut()) {
            PlayerControls(
                music = music,
                isPlaying = isPlaying,
                volume = volume,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onVolumeChange = onVolumeChange,
                onShowLyrics = { showLyrics = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PlayerControls(
    music: MusicItem,
    isPlaying: Boolean,
    volume: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 左侧、右侧各留 EdgeRing 区域（进度条所在），不监听水平拖动
    // 中间 50% 宽度可以左滑触发系统返回
    Box(modifier = modifier) {
        // 实际内容居中排列
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(20.dp))

            // 歌曲名 — Expression 风格大字
            Text(
                text = music.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            // 艺术家
            Text(
                text = music.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))

            // 上一首 / 播放暂停 / 下一首
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious, "上一首",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 大播放按钮 — Expression 核心元素
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                FilledIconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        Icons.Filled.SkipNext, "下一首",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 音量 / 歌词
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = { onVolumeChange((volume - 0.1f).coerceIn(0f, 1f)) },
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Icon(Icons.Filled.VolumeDown, "音量减",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }

                FilledIconButton(
                    onClick = onShowLyrics,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(Icons.Filled.MusicNote, "歌词",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }

                FilledIconButton(
                    onClick = { onVolumeChange((volume + 0.1f).coerceIn(0f, 1f)) },
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Icon(Icons.Filled.VolumeUp, "音量加",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LyricsView(
    musicTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 模拟歌词行（真实应用中从 LRC 文件解析）
    val lyricsLines = remember(musicTitle) {
        listOf(
            "♪  $musicTitle",
            "",
            "暂无歌词",
            "",
            "左滑返回播放器",
        )
    }

    val listState = rememberLazyListState()
    var currentLine by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            if (currentLine < lyricsLines.size - 1) {
                currentLine++
                listState.animateScrollToItem(currentLine.coerceAtMost(lyricsLines.size - 1))
            }
        }
    }

    // 左滑手势 → 关闭歌词
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 30f) onBack()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 52.dp)
        ) {
            itemsIndexed(lyricsLines) { idx, line ->
                val isCurrent = idx == currentLine
                Text(
                    text = line,
                    style = if (isCurrent)
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    else
                        MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }

        // 左上角返回按钮
        FilledIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "返回",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
