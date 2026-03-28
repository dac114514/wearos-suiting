package com.example.suiting.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class MusicService : Service() {

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null

    var onCompletionListener: (() -> Unit)? = null
    var onProgressUpdate: ((Int, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    var currentUri: String? = null
        private set

    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        onProgressUpdate?.invoke(mp.currentPosition, mp.duration)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "progressRunnable error", e)
            }
            handler.postDelayed(this, 300)
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        handler.post(progressRunnable)
    }

    /**
     * 异步播放（prepareAsync），不阻塞主线程。
     * 支持 content:// URI 和 file:// 路径。
     */
    fun playMusic(uriString: String) {
        // 先释放上一个 player
        stopCurrent()
        currentUri = uriString

        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid URI: $uriString", e)
            onError?.invoke("无效的文件路径")
            return
        }

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(this, uri)

            mp.setOnPreparedListener { player ->
                player.start()
                onProgressUpdate?.invoke(0, player.duration)
            }
            mp.setOnCompletionListener {
                onCompletionListener?.invoke()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                onError?.invoke("播放出错 ($what)")
                true
            }

            // 异步准备，不阻塞主线程
            mp.prepareAsync()
            mediaPlayer = mp
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "playMusic failed: $uriString", e)
            mp.release()
            currentUri = null
            onError?.invoke("无法播放: ${e.message}")
        }
    }

    fun pauseResume(): Boolean {
        val mp = mediaPlayer ?: return false
        return try {
            if (mp.isPlaying) {
                mp.pause()
                false
            } else {
                mp.start()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "pauseResume error", e)
            false
        }
    }

    fun isPlaying(): Boolean = try {
        mediaPlayer?.isPlaying ?: false
    } catch (e: Exception) { false }

    fun getCurrentPosition(): Int = try {
        mediaPlayer?.currentPosition ?: 0
    } catch (e: Exception) { 0 }

    fun getDuration(): Int = try {
        mediaPlayer?.duration ?: 0
    } catch (e: Exception) { 0 }

    fun seekTo(position: Int) {
        try { mediaPlayer?.seekTo(position) } catch (e: Exception) {
            Log.e(TAG, "seekTo error", e)
        }
    }

    fun setVolume(vol: Float) {
        try { mediaPlayer?.setVolume(vol, vol) } catch (e: Exception) {
            Log.e(TAG, "setVolume error", e)
        }
    }

    private fun stopCurrent() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopCurrent error", e)
        } finally {
            mediaPlayer = null
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        stopCurrent()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "随听", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("随听")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

    private fun updateNotification() {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "updateNotification error", e)
        }
    }

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "music_channel"
        private const val NOTIFICATION_ID = 1
    }
}
