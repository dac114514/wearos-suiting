package com.example.suiting.presentation

data class MusicItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // ms
    val data: String,   // file path
    val albumArtUri: String? = null
)
