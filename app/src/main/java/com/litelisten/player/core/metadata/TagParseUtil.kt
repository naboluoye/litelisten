package com.litelisten.player.core.metadata

import java.io.File
import java.io.RandomAccessFile

/** Local-only tag extraction result; replayGainDb in dB (usually negative), lyrics raw text. */
data class MediaTags(
    val replayGainDb: Double? = null,
    val lyrics: String? = null,
) {
    val hasTags: Boolean get() = replayGainDb != null || lyrics != null
}

/**
 * Minimal offline tag parser for ReplayGain and embedded lyrics.
 * Pure Kotlin (no Android deps) so it runs in unit tests. Bounded reads only.
 * Supported containers: FLAC (VORBIS_COMMENT) and MP3 (ID3v2.3/2.4 TXXX/USLT).
 * Never throws; malformed input yields an empty MediaTags.
 */
object TagParseUtil {
    private const val FLAC_READ_CAP = 1 shl 20
    private const val ID3_READ_CAP = 4 shl 20
    private val gainRegex = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*d?B?$", RegexOption.IGNORE_CASE)
    private val lrcStampRegex = Regex("\\[\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?]")

    fun parse(file: File): MediaTags = runCatching {
        when (file.extension.lowercase()) {
            "flac" -> parseFlac(readHead(file, FLAC_READ_CAP))
            "mp3" -> parseId3(readId3(file))
            else -> MediaTags()
        }
    }.getOrDefault(MediaTags())

    fun parse(bytes: ByteArray, format: String): MediaTags = runCatching {
        when (format.lowercase()) {
            "flac" -> parseFlac(bytes)
            "mp3" -> parseId3(bytes)
            else -> MediaTags()
        }
    }.getOrDefault(MediaTags())

    fun parseGain(raw: String?): Double? {
        val text = raw?.trim() ?: return null
        if (text.isEmpty()) return null
        val match = gainRegex.find(text) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    fun looksSynced(lyrics: String?): Boolean {
        if (lyrics.isNullOrBlank()) return false
        return lrcStampRegex.containsMatchIn(lyrics)
    }

    private fun readHead(file: File, cap: Int): ByteArray {
        val size = file.length().coerceAtMost(cap.toLong()).toInt()
        val buffer = ByteArray(size)
        RandomAccessFile(file, "r").use { input ->
            input.readFully(buffer)
        }
        return buffer
    }

    /** MP3 tag size is declared in the 10-byte header; read exactly the tag (bounded). */
    private fun readId3(file: File): ByteArray {
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(10)
            if (input.read(header) < 10) return ByteArray(0)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() ||
                header[2] != '3'.code.toByte()
            ) {
                return ByteArray(0)
            }
            val size = syncsafe(header, 6)
            val total = (size + 10).coerceAtMost(ID3_READ_CAP)
            val buffer = ByteArray(total)
            input.readFully(buffer)
            return buffer
        }
    }

    private fun parseFlac(bytes: ByteArray): MediaTags {
        if (bytes.size < 8 ||
            bytes[0] != 'f'.code.toByte() || bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'a'.code.toByte() || bytes[3] != 'C'.code.toByte()
        ) {
            return MediaTags()
        }
        var pos = 4
        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = header and 0x80 != 0
            val type = header and 0x7F
            val len = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + len > bytes.size) break
            if (type == 4) {
                val map = vorbisComments(bytes, pos, len)
                val gain = parseGain(map["REPLAYGAIN_TRACK_GAIN"])
                val lyrics = map["SYNCEDLYRICS"] ?: map["LYRICS"] ?: map["UNSYNCEDLYRICS"]
                if (gain != null || lyrics != null) {
                    return MediaTags(gain, lyrics?.takeIf { it.isNotBlank() })
                }
            }
            pos += len
            if (isLast) break
        }
        return MediaTags()
    }

    private fun vorbisComments(bytes: ByteArray, off: Int, len: Int): Map<String, String> {
        val buffer = java.nio.ByteBuffer.wrap(bytes, off, len)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val vendorLen = buffer.int
        if (vendorLen < 0 || vendorLen > buffer.remaining()) return emptyMap()
        buffer.position(buffer.position() + vendorLen)
        val count = buffer.int
        if (count < 0 || count > 4096) return emptyMap()
        val map = HashMap<String, String>()
        repeat(count) {
            val entryLen = buffer.int
            if (entryLen < 0 || entryLen > buffer.remaining()) return map
            val entry = ByteArray(entryLen)
            buffer.get(entry)
            val text = String(entry, Charsets.UTF_8)
            val eq = text.indexOf('=')
            if (eq > 0) {
                map.putIfAbsent(text.substring(0, eq).uppercase(), text.substring(eq + 1))
            }
        }
        return map
    }

    private fun parseId3(bytes: ByteArray): MediaTags {
        if (bytes.size < 10 ||
            bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() ||
            bytes[2] != '3'.code.toByte()
        ) {
            return MediaTags()
        }
        val major = bytes[3].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        val tagSize = syncsafe(bytes, 6)
        val end = minOf(bytes.size, tagSize + 10)
        var pos = 10
        if (flags and 0x40 != 0) {
            pos += if (major >= 4) syncsafe(bytes, pos) else be32(bytes, pos) + 4
        }
        if (pos < 10 || pos > end) return MediaTags()
        var gain: Double? = null
        var lyrics: String? = null
        while (pos + 10 <= end) {
            val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
            if (id.any { !(it in 'A'..'Z' || it in '0'..'9') }) break
            val frameSize = if (major >= 4) syncsafe(bytes, pos + 4) else be32(bytes, pos + 4)
            pos += 10
            if (frameSize <= 0 || pos + frameSize > end) break
            val data = bytes.copyOfRange(pos, pos + frameSize)
            pos += frameSize
            when (id) {
                "TXXX" -> {
                    val pair = userTextFrame(data)
                    if (pair != null && pair.first.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                        gain = parseGain(pair.second)
                    }
                }
                "USLT" -> {
                    if (lyrics == null) lyrics = unsyncLyricsFrame(data)
                }
            }
        }
        return MediaTags(gain, lyrics?.takeIf { it.isNotBlank() })
    }

    private fun userTextFrame(data: ByteArray): Pair<String, String>? {
        if (data.isEmpty()) return null
        val encoding = data[0].toInt() and 0xFF
        val (description, valueStart) = decodeTerminated(data, 1, encoding) ?: return null
        val value = decodeText(data, valueStart, encoding, data.size)
        return description to value
    }

    private fun unsyncLyricsFrame(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt() and 0xFF
        val (_, textStart) = decodeTerminated(data, 4, encoding) ?: return null
        return decodeText(data, textStart, encoding, data.size)
    }

    /** Decode a null-terminated string at start; returns text and offset after terminator. */
    private fun decodeTerminated(data: ByteArray, start: Int, encoding: Int): Pair<String, Int>? {
        val unit = if (encoding == 1 || encoding == 2) 2 else 1
        var i = start
        while (i + unit <= data.size) {
            val isTerm = if (unit == 1) {
                data[i] == 0.toByte()
            } else {
                data[i] == 0.toByte() && data[i + 1] == 0.toByte()
            }
            if (isTerm) break
            i += unit
        }
        if (i + unit > data.size) return null
        val text = decodeText(data, start, encoding, i)
        return text to (i + unit)
    }

    private fun decodeText(data: ByteArray, start: Int, encoding: Int, end: Int): String {
        if (start >= end) return ""
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(data, start, end - start, charset).trim('\u0000')
    }

    private fun syncsafe(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0x7F) shl 21) or
            ((bytes[off + 1].toInt() and 0x7F) shl 14) or
            ((bytes[off + 2].toInt() and 0x7F) shl 7) or
            (bytes[off + 3].toInt() and 0x7F)

    private fun be32(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xFF) shl 24) or
            ((bytes[off + 1].toInt() and 0xFF) shl 16) or
            ((bytes[off + 2].toInt() and 0xFF) shl 8) or
            (bytes[off + 3].toInt() and 0xFF)
}
