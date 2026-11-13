package com.litelisten.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MimeTypesTest {
    @Test
    fun `supported extensions`() {
        assertThat(MimeTypes.fromPath("/Music/晴天.FLAC")).isEqualTo("audio/flac")
        assertThat(MimeTypes.fromExtension(".mp3")).isEqualTo("audio/mpeg")
        assertThat(MimeTypes.fromPath("a.ogg")).isEqualTo("audio/ogg")
    }

    @Test
    fun `unsupported formats are rejected`() {
        assertThat(MimeTypes.isSupported("song.ape")).isFalse()
        assertThat(MimeTypes.isSupported("song.wma")).isFalse()
        assertThat(MimeTypes.isSupported("song.dsf")).isFalse()
        assertThat(MimeTypes.fromPath("no_extension")).isNull()
    }

    @Test
    fun `lossless and hi-res detection`() {
        assertThat(MimeTypes.isLossless("song.flac")).isTrue()
        assertThat(MimeTypes.isLossless("audio/flac")).isTrue()
        assertThat(MimeTypes.isLossless("song.mp3")).isFalse()
        assertThat(MimeTypes.isHiRes(96_000, 24)).isTrue()
        assertThat(MimeTypes.isHiRes(44_100, 16)).isFalse()
    }

    @Test
    fun `jellyfin container maps to mime type`() {
        assertThat(MimeTypes.fromContainer("FLAC")).isEqualTo("audio/flac")
        assertThat(MimeTypes.fromContainer("m4a")).isEqualTo("audio/mp4")
        assertThat(MimeTypes.fromContainer("ape")).isNull()
    }
}
