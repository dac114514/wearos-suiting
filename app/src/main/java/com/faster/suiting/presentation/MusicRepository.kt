package com.faster.suiting.presentation

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

object MusicRepository {

    private const val TAG = "MusicRepository"

    fun loadAllMusic(context: Context): List<MusicItem> {
        val list = mutableListOf<MusicItem>()

        // 使用最兼容的 URI，避免 VOLUME_EXTERNAL 在部分设备上为空
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )

        // 放宽条件：只要 IS_MUSIC=1，不再卡 DURATION（部分文件 duration 元数据为 0）
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                Log.d(TAG, "cursor count = ${cursor.count}")

                val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id      = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val dur     = cursor.getLong(durationCol)

                    // 跳过超短（< 5 秒）以过滤通知音等
                    if (dur in 1..4999) continue

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val albumArtUri: Uri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )

                    list.add(
                        MusicItem(
                            id          = id,
                            title       = cursor.getString(titleCol)
                                ?.takeIf { it.isNotBlank() } ?: "未知标题",
                            artist      = cursor.getString(artistCol)
                                ?.takeIf { it.isNotBlank() } ?: "未知艺术家",
                            album       = cursor.getString(albumCol) ?: "",
                            duration    = dur,
                            data        = contentUri.toString(),
                            albumArtUri = albumArtUri.toString()
                        )
                    )
                }
            } ?: Log.w(TAG, "query returned null cursor")
        } catch (e: Exception) {
            Log.e(TAG, "loadAllMusic failed", e)
        }

        Log.d(TAG, "loaded ${list.size} tracks")
        return list
    }
}
