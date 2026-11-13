package com.litelisten.player.core.source.jellyfin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JellyfinJsonTest {

    @Test
    fun authResponseYieldsTokenAndUserId() {
        val raw = """{"AccessToken":"tok-123","User":{"Id":"user-1","Name":"bob"}}"""
        val auth = JellyfinJson.parseAuth(raw)
        assertThat(auth?.token).isEqualTo("tok-123")
        assertThat(auth?.userId).isEqualTo("user-1")
    }

    @Test
    fun malformedAuthIsRejected() {
        assertThat(JellyfinJson.parseAuth("""<html>login</html>""")).isNull()
        assertThat(JellyfinJson.parseAuth("""{"User":{"Id":"u"}}""")).isNull()
    }

    @Test
    fun publicSystemInfoIsParsed() {
        val info = JellyfinJson.parseServer(
            """{"ServerName":"Home Media","Version":"10.10.7","Id":"server-1"}""",
        )
        assertThat(info.name).isEqualTo("Home Media")
        assertThat(info.version).isEqualTo("10.10.7")
    }

    @Test
    fun audioItemCarriesHiResMetadata() {
        val raw = """
            {
              "Items": [
                {
                  "Name": "Song A",
                  "Id": "item-1",
                  "Type": "Audio",
                  "Container": "flac",
                  "Path": "/music/a.flac",
                  "RunTimeTicks": 3000000000,
                  "Artists": ["Artist A"],
                  "Album": "Album A",
                  "ImageTags": {"Primary": "tag"},
                  "MediaSources": [
                    {
                      "Size": 45000000,
                      "Container": "flac",
                      "MediaStreams": [
                        {"Type": "Audio", "SampleRate": 96000, "BitDepth": 24,
                         "Channels": 2, "BitRate": 3000000}
                      ]
                    }
                  ]
                },
                {"Name": "Album B", "Id": "item-2", "Type": "MusicAlbum", "IsFolder": true}
              ],
              "TotalRecordCount": 2
            }
        """.trimIndent()
        val items = JellyfinJson.parseItems(raw)
        assertThat(items).hasSize(2)
        val song = items[0]
        assertThat(song.id).isEqualTo("item-1")
        assertThat(song.name).isEqualTo("Song A")
        assertThat(song.isFolder).isFalse()
        assertThat(song.container).isEqualTo("flac")
        assertThat(song.sizeBytes).isEqualTo(45_000_000L)
        assertThat(song.durationMs).isEqualTo(300_000L)
        assertThat(song.artist).isEqualTo("Artist A")
        assertThat(song.album).isEqualTo("Album A")
        assertThat(song.hasArtwork).isTrue()
        assertThat(song.stream?.sampleRateHz).isEqualTo(96_000)
        assertThat(song.stream?.bitDepth).isEqualTo(24)
        assertThat(song.stream?.channels).isEqualTo(2)
        assertThat(song.stream?.bitrateKbps).isEqualTo(3_000)
    }

    @Test
    fun folderTypeIsRecognisedWithoutIsFolderFlag() {
        val raw = """{"Items":[{"Id":"v1","Name":"Music","Type":"CollectionFolder"}]}"""
        val items = JellyfinJson.parseItems(raw)
        assertThat(items.single().isFolder).isTrue()
    }

    @Test
    fun playbackPathRequestsOriginalStreamWithoutTranscoding() {
        assertThat(JellyfinJson.playbackPath("item-1"))
            .isEqualTo("/Audio/item-1/stream?static=true")
        assertThat(JellyfinJson.playbackPath("item-1")).doesNotContain("Download")
    }

    @Test
    fun nonJsonPayloadDoesNotCrash() {
        assertThat(JellyfinJson.parseItems("<html>not json</html>")).isEmpty()
        assertThat(JellyfinJson.parseItems("""{"Items":"unexpected"}""")).isEmpty()
    }
}
