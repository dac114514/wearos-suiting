package com.faster.suiting.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

@Composable
fun PlayerScreen(
    music: MusicItem,
    isPlaying: Boolean,
    progress: Float,
    progressMs: Int,
    durationMs: Int,
    systemVolume: Int,
    maxVolume: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onSeek: (Float) -> Unit,
    onNavigateBack: () -> Unit,       // 右滑返回列表
    modifier: Modifier = Modifier
) {
    var lyricsLines   by remember { mutableStateOf<List<LyricsLine>>(emptyList()) }
    var lyricsLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(music.data) {
        lyricsLines   = emptyList()
        lyricsLoading = true
        lyricsLines   = withContext(Dispatchers.IO) {
            LyricsParser.loadLyrics(context, music.data)
        }
        lyricsLoading = false
    }

    // 当前页面索引：0 = 控制页，1 = 歌词页
    var pageIndex by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // 平滑动画：目标偏移 = -pageIndex * screenWidth
    val animPage by animateFloatAsState(
        targetValue   = pageIndex.toFloat(),
        animationSpec = tween(260),
        label         = "page"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val threshold     = screenWidthPx * 0.22f

        // 外层手势区域（进度环的 pointerInput 优先消费环上事件，中央区域透传到这里）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageIndex) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                // 控制页右滑 → 返回列表
                                pageIndex == 0 && dragOffset >  threshold -> {
                                    onNavigateBack()
                                }
                                // 控制页左滑 → 歌词页
                                pageIndex == 0 && dragOffset < -threshold -> pageIndex = 1
                                // 歌词页右滑 → 控制页
                                pageIndex == 1 && dragOffset >  threshold -> pageIndex = 0
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragOffset = when (pageIndex) {
                                // 控制页：允许左滑（负）和右滑（正，准备返回列表）
                                0 -> (dragOffset + amount).coerceIn(-screenWidthPx, screenWidthPx)
                                // 歌词页：只允许右滑（正）回到控制页
                                else -> (dragOffset + amount).coerceIn(0f, screenWidthPx)
                            }
                        }
                    )
                }
        ) {
            // ── 进度环（固定在底层，不参与页面滑动）──────────────────────
            WaveProgressIndicator(
                progress      = progress,
                isPlaying     = isPlaying,
                onSeek        = onSeek,
                modifier      = Modifier.fillMaxSize(),
                strokeWidth   = 18f,
                progressColor = MaterialTheme.colorScheme.primary,
                trackColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                glowColor     = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            )

            // ── 双页面容器：水平并排，通过 offset 滑动 ────────────────────
            // 总宽度 = 2 × screenWidth；translationX 在 0 到 -screenWidth 之间
            val offsetPx = (-(animPage * screenWidthPx) + dragOffset)
                .coerceIn(-screenWidthPx, screenWidthPx)

            // 控制页
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = offsetPx }
            ) {
                PlayerControls(
                    music        = music,
                    isPlaying    = isPlaying,
                    systemVolume = systemVolume,
                    maxVolume    = maxVolume,
                    onPlayPause  = onPlayPause,
                    onNext       = onNext,
                    onPrevious   = onPrevious,
                    onVolumeUp   = onVolumeUp,
                    onVolumeDown = onVolumeDown,
                    modifier     = Modifier.fillMaxSize()
                )
            }

            // 歌词页（始终在控制页右边一个屏幕宽的位置）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = offsetPx + screenWidthPx }
            ) {
                LyricsView(
                    music       = music,
                    lyricsLines = lyricsLines,
                    isLoading   = lyricsLoading,
                    progressMs  = progressMs,
                    durationMs  = durationMs,
                    modifier    = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── Expression 圆角按钮 ──────────────────────────────────────────────────
@Composable
private fun ExpressionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    iconSize: Dp,
    containerColor: Color,
    iconTint: Color,
    cornerRadius: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription,
            modifier = Modifier.size(iconSize), tint = iconTint)
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun PlayerControls(
    music: MusicItem,
    isPlaying: Boolean,
    systemVolume: Int,
    maxVolume: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(16.dp))

        Text(music.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(3.dp))

        Text(music.artist,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressionButton(onClick = onPrevious, icon = Icons.Filled.SkipPrevious,
                contentDescription = "上一首", size = 48.dp, iconSize = 28.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                iconTint = MaterialTheme.colorScheme.onSurface, cornerRadius = 16.dp)
            PlayPauseButton(isPlaying = isPlaying, onClick = onPlayPause)
            ExpressionButton(onClick = onNext, icon = Icons.Filled.SkipNext,
                contentDescription = "下一首", size = 48.dp, iconSize = 28.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                iconTint = MaterialTheme.colorScheme.onSurface, cornerRadius = 16.dp)
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressionButton(onClick = onVolumeDown, icon = Icons.Filled.VolumeDown,
                contentDescription = "音量减", size = 44.dp, iconSize = 26.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                iconTint = MaterialTheme.colorScheme.onSurface, cornerRadius = 14.dp)
            ExpressionButton(onClick = onVolumeUp, icon = Icons.Filled.VolumeUp,
                contentDescription = "音量加", size = 44.dp, iconSize = 26.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                iconTint = MaterialTheme.colorScheme.onSurface, cornerRadius = 14.dp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LyricsView(
    music: MusicItem,
    lyricsLines: List<LyricsLine>,
    isLoading: Boolean,
    progressMs: Int,
    durationMs: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Text("♪", style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
            lyricsLines.isEmpty() -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♪", style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    Spacer(Modifier.height(10.dp))
                    Text(music.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(music.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("暂无歌词", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(6.dp))
                    Text("右滑返回", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }
            else -> SyncedLyricsColumn(lines = lyricsLines, progressMs = progressMs, durationMs = durationMs)
        }
    }
}

@Composable
private fun SyncedLyricsColumn(lines: List<LyricsLine>, progressMs: Int, durationMs: Int) {
    val hasTimed = lines.any { it.timeMs >= 0 }
    val currentIdx = if (hasTimed) {
        lines.indexOfLast { it.timeMs in 0..progressMs.toLong() }.coerceAtLeast(0)
    } else {
        if (durationMs > 0)
            ((progressMs.toFloat() / durationMs) * lines.size).toInt().coerceIn(0, lines.size - 1)
        else 0
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentIdx) {
        listState.animateScrollToItem((currentIdx - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState, horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 50.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(lines.size) { idx ->
            val isCurrent = idx == currentIdx
            Text(
                text = lines[idx].text,
                style = if (isCurrent)
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                else
                    MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    idx == currentIdx - 1 || idx == currentIdx + 1 ->
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }
    }
}
