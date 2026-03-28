package com.faster.suiting.presentation

data class MusicItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val data: String,          // content:// URI
    val albumArtUri: String? = null
)
