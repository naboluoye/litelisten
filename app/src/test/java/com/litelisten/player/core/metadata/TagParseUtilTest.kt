package com.litelisten.player.core.metadata

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagParseUtilTest {

    // ---------- ReplayGain value parsing ----------

    @Test
    fun gainParsesCommonFormats() {
        assertEquals(-7.89, TagParseUtil.parseGain("-7.89 dB")!!, 1e-9)
        assertEquals(-7.89, TagParseUtil.parseGain("-7.89db")!!, 1e-9)
        assertEquals(1.5, TagParseUtil.parseGain("+1.5 dB")!!, 1e-9)
        assertEquals(0.0, TagParseUtil.parseGain("0.00 dB")!!, 1e-9)
        assertEquals(-7.89, TagParseUtil.parseGain("-7.89")!!, 1e-9)
    }

    @Test
    fun gainRejectsGarbage() {
        assertNull(TagParseUtil.parseGain(null))
        assertNull(TagParseUtil.parseGain(""))
        assertNull(TagParseUtil.parseGain("loud"))
        assertNull(TagParseUtil.parseGain("--7 dB"))
    }

    // ---------- FLAC ----------

    @Test
    fun flacVorbisCommentYieldsGainAndSyncedLyrics() {
        val comments = mapOf(
            "REPLAYGAIN_TRACK_GAIN" to "-7.89 dB",
            "REPLAYGAIN_TRACK_PEAK" to "0.999",
            "SYNCEDLYRICS" to "[00:01.00]hello",
            "TITLE" to "song",
        )
        val bytes = flacWithComments(comments)
        val tags = TagParseUtil.parse(bytes, "flac")
        assertEquals(-7.89, tags.replayGainDb!!, 1e-9)
        assertTrue(tags.lyrics!!.contains("hello"))
        assertTrue(TagParseUtil.looksSynced(tags.lyrics))
    }

    @Test
    fun flacWithoutInterestingTagsIsEmpty() {
        val bytes = flacWithComments(mapOf("TITLE" to "song"))
        val tags = TagParseUtil.parse(bytes, "flac")
        assertNull(tags.replayGainDb)
        assertNull(tags.lyrics)
    }

    @Test
    fun flacBadMagicIsEmpty() {
        val tags = TagParseUtil.parse("NOTfLaC!".toByteArray(), "flac")
        assertNull(tags.replayGainDb)
        assertNull(tags.lyrics)
    }

    @Test
    fun looksSyncedAcceptsLongFractionStamps() {
        assertTrue(TagParseUtil.looksSynced("[00:12.3456]line"))
        assertTrue(TagParseUtil.looksSynced("[01:02:03]frame-based stamp"))
    }

    // ---------- MP3 / ID3v2 ----------

    @Test
    fun id3TxxxAndUsltAreParsed() {
        val frames = txxxFrame("REPLAYGAIN_TRACK_GAIN", "-4.50 dB") +
            usltFrame("[00:05.00]line one\n[00:09.00]line two")
        val bytes = id3WithFrames(frames)
        val tags = TagParseUtil.parse(bytes, "mp3")
        assertEquals(-4.5, tags.replayGainDb!!, 1e-9)
        assertTrue(tags.lyrics!!.contains("line two"))
        assertTrue(TagParseUtil.looksSynced(tags.lyrics))
    }

    @Test
    fun id3WithoutTagsIsEmpty() {
        val bytes = id3WithFrames(txxxFrame("TIT2", "just a title"))
        val tags = TagParseUtil.parse(bytes, "mp3")
        assertNull(tags.replayGainDb)
        assertNull(tags.lyrics)
    }

    @Test
    fun id3Utf16UsltIsDecoded() {
        val lyrics = "[00:01.00]歌词"
        val frames = usltFrameUtf16(lyrics)
        val bytes = id3WithFrames(frames)
        val tags = TagParseUtil.parse(bytes, "mp3")
        assertEquals(lyrics, tags.lyrics)
    }

    @Test
    fun unknownContainerIsEmpty() {
        val tags = TagParseUtil.parse(byteArrayOf(1, 2, 3), "ogg")
        assertNull(tags.replayGainDb)
        assertNull(tags.lyrics)
    }

    // ---------- fixtures ----------

    private fun flacWithComments(comments: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        val body = ByteArrayOutputStream()
        // vendor block
        val vendor = "test".toByteArray(Charsets.UTF_8)
        body.writeLe32(vendor.size)
        body.write(vendor)
        body.writeLe32(comments.size)
        comments.forEach { (key, value) ->
            val entry = "$key=$value".toByteArray(Charsets.UTF_8)
            body.writeLe32(entry.size)
            body.write(entry)
        }
        val payload = body.toByteArray()
        out.write(byteArrayOf(0x84.toByte())) // last block + type 4 (VORBIS_COMMENT)
        out.write(byteArrayOf(
            ((payload.size shr 16) and 0xFF).toByte(),
            ((payload.size shr 8) and 0xFF).toByte(),
            (payload.size and 0xFF).toByte(),
        ))
        out.write(payload)
        return out.toByteArray()
    }

    private fun id3WithFrames(frames: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(byteArrayOf(0x04, 0x00, 0x00)) // v2.4, no flags
        out.writeSyncsafe(frames.size)
        out.write(frames)
        return out.toByteArray()
    }

    private fun txxxFrame(description: String, value: String): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(0x00) // ISO-8859-1
        data.write(description.toByteArray(Charsets.ISO_8859_1))
        data.write(0x00)
        data.write(value.toByteArray(Charsets.ISO_8859_1))
        return frame("TXXX", data.toByteArray())
    }

    private fun usltFrame(lyrics: String): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(0x03) // UTF-8
        data.write("eng".toByteArray(Charsets.ISO_8859_1))
        data.write(0x00) // empty descriptor
        data.write(lyrics.toByteArray(Charsets.UTF_8))
        return frame("USLT", data.toByteArray())
    }

    private fun usltFrameUtf16(lyrics: String): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(0x01) // UTF-16 with BOM
        data.write("eng".toByteArray(Charsets.ISO_8859_1))
        // descriptor: empty UTF-16 terminated by 00 00
        data.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00))
        data.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) // BOM for text
        data.write(lyrics.toByteArray(Charsets.UTF_16LE))
        data.write(byteArrayOf(0x00, 0x00)) // trailing terminator (optional but legal)
        return frame("USLT", data.toByteArray())
    }

    private fun frame(id: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.writeSyncsafe(data.size)
        out.write(byteArrayOf(0x00, 0x00))
        out.write(data)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        ))
    }

    private fun ByteArrayOutputStream.writeSyncsafe(value: Int) {
        write(byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte(),
        ))
    }
}
