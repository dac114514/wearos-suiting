package com.faster.suiting.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.faster.suiting.presentation.theme.WearAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var audioManager: AudioManager

    private var musicList    by mutableStateOf<List<MusicItem>>(emptyList())
    private var currentIndex by mutableIntStateOf(0)
    private var isPlaying    by mutableStateOf(false)
    private var progressMs   by mutableIntStateOf(0)
    private var durationMs   by mutableIntStateOf(1)
    private var isLoading    by mutableStateOf(false)
    // 是否曾经开始过播放（用于列表页左滑是否可以打开播放器）
    private var hasPlayed    by mutableStateOf(false)

    private var systemVolume by mutableIntStateOf(0)
    private var maxVolume    by mutableIntStateOf(15)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as MusicService.MusicBinder).getService()
            musicService = svc
            isBound      = true
            svc.onProgressUpdate = { pos, dur ->
                progressMs = pos
                if (dur > 0) durationMs = dur
            }
            svc.onCompletionListener = { playNext() }
            svc.onError = { msg -> Log.e(TAG, "播放错误: $msg") }
            isPlaying = svc.isPlaying()
            if (isPlaying) hasPlayed = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null; isBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) loadMusicAsync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume    = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val serviceIntent = Intent(this, MusicService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            WearAppTheme {
                val navController = rememberSwipeDismissableNavController()

                AppScaffold {
                    SwipeDismissableNavHost(
                        navController    = navController,
                        startDestination = "list"
                    ) {
                        composable("list") {
                            MusicListScreen(
                                musicList       = musicList,
                                isLoading       = isLoading,
                                // 只有曾经开始过播放，左滑才有意义
                                hasActivePlayer = hasPlayed,
                                onMusicClick    = { idx ->
                                    val clickedUri = musicList.getOrNull(idx)?.data
                                        ?: return@MusicListScreen
                                    currentIndex = idx
                                    if (musicService?.currentUri != clickedUri) {
                                        playCurrentSong()
                                    }
                                    navController.navigate("player")
                                },
                                onRefresh    = { loadMusicAsync() },
                                // 列表页左滑 → 导航到播放器（不重新播放）
                                onShowPlayer = {
                                    if (hasPlayed) navController.navigate("player")
                                }
                            )
                        }
                        composable("player") {
                            val music = musicList.getOrNull(currentIndex)
                            if (music != null) {
                                val progress = if (durationMs > 0)
                                    progressMs.toFloat() / durationMs.toFloat()
                                else 0f

                                PlayerScreen(
                                    music          = music,
                                    isPlaying      = isPlaying,
                                    progress       = progress,
                                    progressMs     = progressMs,
                                    durationMs     = durationMs,
                                    systemVolume   = systemVolume,
                                    maxVolume      = maxVolume,
                                    onPlayPause    = { togglePlayPause() },
                                    onNext         = { playNext() },
                                    onPrevious     = { playPrevious() },
                                    onVolumeUp     = { adjustVolume(+1) },
                                    onVolumeDown   = { adjustVolume(-1) },
                                    onSeek         = { p ->
                                        progressMs = (p * durationMs).toInt()
                                        musicService?.let { s ->
                                            if (durationMs > 0) s.seekTo(progressMs)
                                        }
                                    },
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }

        requestPermissionsIfNeeded()
    }

    /** 每次调节前从系统实时读取当前音量，避免与系统实际值脱节 */
    private fun adjustVolume(delta: Int) {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVol  = (current + delta).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVol,
            AudioManager.FLAG_SHOW_UI
        )
        systemVolume = newVol
    }

    private fun requestPermissionsIfNeeded() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED })
            permissionLauncher.launch(perms)
        else
            loadMusicAsync()
    }

    private fun loadMusicAsync() {
        lifecycleScope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    MusicRepository.loadAllMusic(this@MainActivity)
                }
                musicList = result
                Log.d(TAG, "加载完成，共 ${result.size} 首")
            } catch (e: Exception) {
                Log.e(TAG, "加载音乐失败", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun playCurrentSong() {
        val song = musicList.getOrNull(currentIndex) ?: return
        musicService?.playMusic(song.data)
        isPlaying  = true
        progressMs = 0
        hasPlayed  = true
    }

    private fun togglePlayPause() {
        musicService?.let { isPlaying = it.pauseResume() }
    }

    private fun playNext() {
        if (musicList.isEmpty()) return
        currentIndex = (currentIndex + 1) % musicList.size
        playCurrentSong()
    }

    private fun playPrevious() {
        if (musicList.isEmpty()) return
        currentIndex = if (currentIndex == 0) musicList.size - 1 else currentIndex - 1
        playCurrentSong()
    }

    override fun onDestroy() {
        if (isBound) { unbindService(serviceConnection); isBound = false }
        super.onDestroy()
    }

    companion object { private const val TAG = "MainActivity" }
}
