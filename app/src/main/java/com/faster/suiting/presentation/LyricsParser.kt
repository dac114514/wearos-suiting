package com.faster.suiting.presentation

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LyricsLine(
    val timeMs: Long,   // -1 表示无时间戳（纯文本歌词）
    val text: String
)

object LyricsParser {

    private const val TAG = "LyricsParser"

    /**
     * 从音频文件的 content:// URI 提取歌词。
     * 优先读取 ID3 USLT（非同步歌词），其次 SYLT（同步歌词）。
     * 返回解析好的歌词行列表，空列表表示无歌词。
     */
    suspend fun loadLyrics(context: Context, uriString: String): List<LyricsLine> =
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                val uri = Uri.parse(uriString)
                retriever.setDataSource(context, uri)

                // 1. 尝试读取 USLT（非同步纯文本歌词，最常见）
                val embeddedPicture = retriever.embeddedPicture // 触发元数据完整加载
                val rawLyrics = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                ) // 先确认是音频

                // MediaMetadataRetriever 没有直接暴露 USLT，
                // 需读取 ID3 原始数据自行解析
                val result = parseId3Lyrics(context, uri, retriever)

                retriever.release()
                result
            } catch (e: Exception) {
                Log.e(TAG, "loadLyrics failed for $uriString", e)
                emptyList()
            }
        }

    private fun parseId3Lyrics(
        context: Context,
        uri: Uri,
        retriever: MediaMetadataRetriever
    ): List<LyricsLine> {
        // Android API 29+ MediaMetadataRetriever 支持 getEmbeddedPicture 等，
        // 但 USLT 没有直接 API。改为读取文件原始字节解析 ID3v2 帧。
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(10)
                if (stream.read(header) < 10) return emptyList()

                // 检查 ID3v2 头
                if (header[0] != 'I'.code.toByte() ||
                    header[1] != 'D'.code.toByte() ||
                    header[2] != '3'.code.toByte()) {
                    Log.d(TAG, "No ID3v2 tag")
                    return emptyList()
                }

                val version = header[3].toInt() and 0xFF  // ID3v2.x
                val flags   = header[5].toInt() and 0xFF
                // ID3v2 总大小（4字节 syncsafe integer）
                val tagSize = ((header[6].toLong() and 0x7F) shl 21) or
                              ((header[7].toLong() and 0x7F) shl 14) or
                              ((header[8].toLong() and 0x7F) shl 7)  or
                               (header[9].toLong() and 0x7F)

                // 跳过扩展头（如果有）
                var offset = 0L
                if (flags and 0x40 != 0 && version >= 3) {
                    val extHeader = ByteArray(4)
                    stream.read(extHeader)
                    val extSize = if (version == 4) {
                        ((extHeader[0].toLong() and 0x7F) shl 21) or
                        ((extHeader[1].toLong() and 0x7F) shl 14) or
                        ((extHeader[2].toLong() and 0x7F) shl 7)  or
                         (extHeader[3].toLong() and 0x7F)
                    } else {
                        ((extHeader[0].toLong() and 0xFF) shl 24) or
                        ((extHeader[1].toLong() and 0xFF) shl 16) or
                        ((extHeader[2].toLong() and 0xFF) shl 8)  or
                         (extHeader[3].toLong() and 0xFF)
                    }
                    stream.skip(extSize - 4)
                    offset += extSize
                }

                val tagBytes = ByteArray(tagSize.toInt().coerceAtMost(512 * 1024))
                stream.read(tagBytes)

                // 在 tagBytes 中搜索 USLT / SYLT 帧
                findLyricsInId3Frames(tagBytes, version)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "ID3 parse error", e)
            emptyList()
        }
    }

    private fun findLyricsInId3Frames(data: ByteArray, version: Int): List<LyricsLine> {
        var pos = 0
        val frameIdSize = if (version >= 3) 4 else 3
        val frameSizeBytes = if (version >= 3) 4 else 3

        while (pos + frameIdSize + frameSizeBytes + 2 < data.size) {
            // 读取帧 ID
            val frameId = String(data, pos, frameIdSize, Charsets.ISO_8859_1)
            if (frameId[0].code == 0) break // 填充区

            pos += frameIdSize

            // 读取帧大小
            val frameSize: Int = if (version >= 4) {
                // syncsafe
                ((data[pos].toInt() and 0x7F) shl 21) or
                ((data[pos+1].toInt() and 0x7F) shl 14) or
                ((data[pos+2].toInt() and 0x7F) shl 7)  or
                 (data[pos+3].toInt() and 0x7F)
            } else {
                ((data[pos].toInt() and 0xFF) shl 24) or
                ((data[pos+1].toInt() and 0xFF) shl 16) or
                ((data[pos+2].toInt() and 0xFF) shl 8)  or
                 (data[pos+3].toInt() and 0xFF)
            }
            pos += frameSizeBytes

            // 跳过帧标志（ID3v2.3/4 有 2 字节，v2.2 没有）
            val flagBytes = if (version >= 3) 2 else 0
            pos += flagBytes

            if (frameSize <= 0 || pos + frameSize > data.size) break

            val frameData = data.copyOfRange(pos, pos + frameSize)
            pos += frameSize

            when (frameId.trimEnd('\u0000')) {
                "USLT", "ULT" -> {
                    // USLT: encoding(1) + lang(3) + content_desc + \0 + lyrics
                    val lyrics = parseUslt(frameData)
                    if (lyrics.isNotEmpty()) return lyrics
                }
                "SYLT", "SLT" -> {
                    val lyrics = parseSylt(frameData)
                    if (lyrics.isNotEmpty()) return lyrics
                }
            }
        }
        return emptyList()
    }

    /** 解析 USLT 帧 → 按 LRC 格式解析时间戳，或直接返回纯文本行 */
    private fun parseUslt(data: ByteArray): List<LyricsLine> {
        if (data.size < 5) return emptyList()
        val encoding = data[0].toInt() and 0xFF
        // 跳过语言(3字节) + 内容描述(以 null 结尾)
        var i = 4
        // 跳过内容描述直到 null 终止符（UTF-16 用双字节 null）
        val nullSize = if (encoding == 1 || encoding == 2) 2 else 1
        while (i + nullSize <= data.size) {
            if (encoding == 1 || encoding == 2) {
                if (data[i] == 0.toByte() && i + 1 < data.size && data[i+1] == 0.toByte()) {
                    i += 2; break
                }
                i += 2
            } else {
                if (data[i] == 0.toByte()) { i += 1; break }
                i += 1
            }
        }
        if (i >= data.size) return emptyList()

        val lyricsText = when (encoding) {
            1, 2 -> String(data, i, data.size - i, Charsets.UTF_16)
            3    -> String(data, i, data.size - i, Charsets.UTF_8)
            else -> String(data, i, data.size - i, Charsets.ISO_8859_1)
        }.trim()

        return parseLrcText(lyricsText)
    }

    /** 解析 SYLT 帧 → 带时间戳歌词 */
    private fun parseSylt(data: ByteArray): List<LyricsLine> {
        if (data.size < 6) return emptyList()
        val encoding = data[0].toInt() and 0xFF
        // lang(3) + timestampFormat(1) + contentType(1) + contentDesc + null
        var i = 6
        val nullSize = if (encoding == 1 || encoding == 2) 2 else 1
        while (i + nullSize <= data.size) {
            if (encoding == 1 || encoding == 2) {
                if (data[i] == 0.toByte() && i+1 < data.size && data[i+1] == 0.toByte()) { i += 2; break }
                i += 2
            } else {
                if (data[i] == 0.toByte()) { i += 1; break }
                i += 1
            }
        }

        val lines = mutableListOf<LyricsLine>()
        while (i < data.size - 4) {
            // 读取文本直到 null
            val textStart = i
            while (i < data.size) {
                if (encoding == 1 || encoding == 2) {
                    if (i + 1 < data.size && data[i] == 0.toByte() && data[i+1] == 0.toByte()) { i += 2; break }
                    i += 2
                } else {
                    if (data[i] == 0.toByte()) { i += 1; break }
                    i += 1
                }
            }
            val textBytes = data.copyOfRange(textStart, i - nullSize)
            val text = when (encoding) {
                1, 2 -> String(textBytes, Charsets.UTF_16)
                3    -> String(textBytes, Charsets.UTF_8)
                else -> String(textBytes, Charsets.ISO_8859_1)
            }
            if (i + 4 > data.size) break
            val timeMs = ((data[i].toLong() and 0xFF) shl 24) or
                         ((data[i+1].toLong() and 0xFF) shl 16) or
                         ((data[i+2].toLong() and 0xFF) shl 8) or
                          (data[i+3].toLong() and 0xFF)
            i += 4
            if (text.isNotBlank()) lines.add(LyricsLine(timeMs, text.trim()))
        }
        return lines
    }

    /** 解析 LRC 格式文本（[mm:ss.xx]歌词行） */
    fun parseLrcText(text: String): List<LyricsLine> {
        val lrcPattern = Regex("""\[(\d{2}):(\d{2})[\.\:](\d{2,3})\](.*)""")
        val lines = mutableListOf<LyricsLine>()

        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val matches = lrcPattern.findAll(line)
            if (matches.none()) {
                // 无时间戳，作为纯文本行（元数据行如 [ar:xxx] 跳过）
                if (!line.startsWith("[") && line.isNotBlank()) {
                    lines.add(LyricsLine(-1L, line))
                }
            } else {
                matches.forEach { match ->
                    val min  = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec  = match.groupValues[2].toLongOrNull() ?: 0L
                    val ms   = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    val lyricsText = match.groupValues[4].trim()
                    val timeMs = min * 60_000L + sec * 1_000L + ms
                    if (lyricsText.isNotBlank()) {
                        lines.add(LyricsLine(timeMs, lyricsText))
                    }
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }
}
