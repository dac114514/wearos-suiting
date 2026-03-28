package com.faster.suiting.presentation

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

    // 保存最新音量，prepareAsync 完成后立即应用
    private var pendingVolume: Float = 0.8f

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
                Log.e(TAG, "progress tick error", e)
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
            startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        handler.post(progressRunnable)
    }

    fun playMusic(uriString: String) {
        stopCurrent()
        currentUri = uriString

        val uri = try { Uri.parse(uriString) } catch (e: Exception) {
            Log.e(TAG, "Bad URI: $uriString", e)
            onError?.invoke("无效路径"); return
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
                // 应用最新音量后再播放
                player.setVolume(pendingVolume, pendingVolume)
                player.start()
                onProgressUpdate?.invoke(0, player.duration)
            }
            mp.setOnCompletionListener { onCompletionListener?.invoke() }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                onError?.invoke("播放出错 ($what)"); true
            }

            mp.prepareAsync()
            mediaPlayer = mp
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "playMusic failed", e)
            mp.release(); currentUri = null
            onError?.invoke("无法播放: ${e.message}")
        }
    }

    fun pauseResume(): Boolean {
        val mp = mediaPlayer ?: return false
        return try {
            if (mp.isPlaying) { mp.pause(); false } else { mp.start(); true }
        } catch (e: Exception) { Log.e(TAG, "pauseResume", e); false }
    }

    fun isPlaying(): Boolean = try { mediaPlayer?.isPlaying ?: false } catch (e: Exception) { false }
    fun getCurrentPosition(): Int = try { mediaPlayer?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun getDuration(): Int = try { mediaPlayer?.duration ?: 0 } catch (e: Exception) { 0 }

    fun seekTo(pos: Int) { try { mediaPlayer?.seekTo(pos) } catch (e: Exception) { Log.e(TAG, "seekTo", e) } }

    /** 音量立即生效；prepareAsync 期间先缓存，prepared 后自动应用 */
    fun setVolume(vol: Float) {
        pendingVolume = vol.coerceIn(0f, 1f)
        try { mediaPlayer?.setVolume(pendingVolume, pendingVolume) } catch (e: Exception) { Log.e(TAG, "setVolume", e) }
    }

    private fun stopCurrent() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset(); release()
            }
        } catch (e: Exception) { Log.e(TAG, "stopCurrent", e) }
        finally { mediaPlayer = null }
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
        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
        catch (e: Exception) { Log.e(TAG, "updateNotification", e) }
    }

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "music_channel"
        private const val NOTIFICATION_ID = 1
    }
}
