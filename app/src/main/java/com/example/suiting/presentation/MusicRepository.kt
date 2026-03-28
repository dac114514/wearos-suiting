package com.example.suiting.presentation

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object MusicRepository {

    fun loadAllMusic(context: Context): List<MusicItem> {
        val list = mutableListOf<MusicItem>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0" +
                " AND ${MediaStore.Audio.Media.DURATION} > 10000"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE LOCALIZED ASC"

        context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                // Android 10+ 不再暴露 DATA 列，用 ContentUris 构建播放 URI
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val albumArtUri: Uri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )

                list.add(
                    MusicItem(
                        id       = id,
                        title    = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "未知标题",
                        artist   = cursor.getString(artistCol)?.takeIf { it.isNotBlank() } ?: "未知艺术家",
                        album    = cursor.getString(albumCol) ?: "",
                        duration = cursor.getLong(durationCol),
                        // data 改为 content:// URI 字符串，Android 10+ 兼容
                        data     = contentUri.toString(),
                        albumArtUri = albumArtUri.toString()
                    )
                )
            }
        }
        return list
    }
}
