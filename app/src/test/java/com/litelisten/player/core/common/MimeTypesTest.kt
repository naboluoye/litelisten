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
}
