package com.example.suiting.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.suiting.presentation.theme.WearAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var musicService: MusicService? = null
    private var isBound = false

    private var musicList    by mutableStateOf<List<MusicItem>>(emptyList())
    private var currentIndex by mutableIntStateOf(0)
    private var isPlaying    by mutableStateOf(false)
    private var progress     by mutableFloatStateOf(0f)
    private var volume       by mutableFloatStateOf(0.8f)
    private var isLoading    by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as MusicService.MusicBinder).getService()
            musicService = svc
            isBound = true
            svc.onProgressUpdate = { pos, dur ->
                if (dur > 0) progress = pos.toFloat() / dur.toFloat()
            }
            svc.onCompletionListener = { playNext() }
            svc.onError = { msg -> Log.e("MainActivity", "播放错误: $msg") }
            svc.setVolume(volume)
            isPlaying = svc.isPlaying()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            // ✅ 权限回调在主线程，必须切到 IO 线程执行查询
            loadMusicAsync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, MusicService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        requestPermissionsIfNeeded()

        setContent {
            WearAppTheme {
                val navController = rememberSwipeDismissableNavController()

                AppScaffold {
                    SwipeDismissableNavHost(
                        navController = navController,
                        startDestination = "list"
                    ) {
                        composable("list") {
                            MusicListScreen(
                                musicList = musicList,
                                isLoading = isLoading,
                                onMusicClick = { idx ->
                                    val clickedUri = musicList.getOrNull(idx)?.data ?: return@MusicListScreen
                                    currentIndex = idx
                                    // 只有不同的歌才重新播放
                                    if (musicService?.currentUri != clickedUri) {
                                        playCurrentSong()
                                    }
                                    navController.navigate("player")
                                },
                                onRefresh = { loadMusicAsync() }
                            )
                        }
                        composable("player") {
                            val music = musicList.getOrNull(currentIndex)
                            if (music != null) {
                                PlayerScreen(
                                    music          = music,
                                    isPlaying      = isPlaying,
                                    progress       = progress,
                                    volume         = volume,
                                    onPlayPause    = { togglePlayPause() },
                                    onNext         = { playNext() },
                                    onPrevious     = { playPrevious() },
                                    onVolumeChange = { v ->
                                        volume = v
                                        musicService?.setVolume(v)
                                    },
                                    onSeek = { p ->
                                        progress = p
                                        musicService?.let { s ->
                                            val dur = s.getDuration()
                                            if (dur > 0) s.seekTo((p * dur).toInt())
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(perms)
        } else {
            loadMusicAsync()
        }
    }

    /**
     * 在 IO 线程执行 MediaStore 查询，结果回到主线程更新 State。
     * 避免在主线程做 ContentResolver 查询导致 ANR/crash。
     */
    private fun loadMusicAsync() {
        if (isLoading) return
        lifecycleScope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    MusicRepository.loadAllMusic(this@MainActivity)
                }
                musicList = result
                Log.d("MainActivity", "加载完成，共 ${result.size} 首")
            } catch (e: Exception) {
                Log.e("MainActivity", "加载音乐失败", e)
                musicList = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    private fun playCurrentSong() {
        val song = musicList.getOrNull(currentIndex) ?: return
        musicService?.playMusic(song.data)
        isPlaying = true
        progress = 0f
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
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}
