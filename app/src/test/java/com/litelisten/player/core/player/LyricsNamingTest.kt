package com.litelisten.player.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LyricsNamingTest {
    @Test
    fun derivesSideCarPathFromSongPath() {
        assertThat(LyricsNaming.sideCarPath("/Music/Jay/晴天.flac"))
            .isEqualTo("/Music/Jay/晴天.lrc")
        assertThat(LyricsNaming.sideCarPath("/Music/Jay/晴天"))
            .isEqualTo("/Music/Jay/晴天.lrc")
        assertThat(LyricsNaming.sideCarPath("Music/Jay/a.b.flac"))
            .isEqualTo("Music/Jay/a.b.lrc")
    }

    @Test
    fun rejectsEmptyOrFolderLikePaths() {
        assertThat(LyricsNaming.sideCarPath("")).isNull()
        assertThat(LyricsNaming.sideCarPath("/Music/Jay/")).isNull()
        assertThat(LyricsNaming.sideCarPath(".flac")).isNull()
    }

    @Test
    fun normalizeIgnoresSpacesAndCase() {
        assertThat(LyricsNaming.normalize("  晴天 ")).isEqualTo("晴天")
        assertThat(LyricsNaming.normalize("Hello World")).isEqualTo("helloworld")
        assertThat(LyricsNaming.normalize("周杰伦　晴天")).isEqualTo("周杰伦晴天")
    }
}
