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

    @Test
    fun matchesSameName() {
        // 用户核心场景：歌曲与歌词同名（含大小写/空格差异）。
        val song = LyricsNaming.songCandidates("晴天.flac", "晴天")
        assertThat(LyricsNaming.matches("晴天", song)).isTrue()
        assertThat(LyricsNaming.matches("晴天.lrc", song)).isTrue()
        assertThat(LyricsNaming.matches("  晴天 ", song)).isTrue()
    }

    @Test
    fun matchesWithArtistPrefixAndTrackNumber() {
        // 曲库标题可能是「晴天」，歌词文件名带歌手前缀或序号。
        val song = LyricsNaming.songCandidates("晴天.flac", "晴天")
        assertThat(LyricsNaming.matches("周杰伦 - 晴天", song)).isTrue()
        assertThat(LyricsNaming.matches("周杰伦_晴天", song)).isTrue()
        assertThat(LyricsNaming.matches("01. 晴天", song)).isTrue()
        assertThat(LyricsNaming.matches("[01]晴天", song)).isTrue()
    }

    @Test
    fun matchesWhenSongNameHasPrefix() {
        // 反过来：曲库标题带前缀，歌词是纯歌名。
        val song = LyricsNaming.songCandidates("周杰伦 - 晴天.flac", "周杰伦 - 晴天")
        assertThat(LyricsNaming.matches("晴天", song)).isTrue()
    }

    @Test
    fun doesNotFuzzyMatchShortNames() {
        // 不应把「爱」误配到「恋爱」：只做变体精确比较，不做包含匹配。
        val song = LyricsNaming.songCandidates("恋爱.flac", "恋爱")
        assertThat(LyricsNaming.matches("爱", song)).isFalse()
    }

    @Test
    fun variantsDeduplicateAndKeepOrder() {
        val v = LyricsNaming.variants("  周杰伦 - 晴天 ")
        assertThat(v).contains("周杰伦-晴天")
        assertThat(v).contains("晴天")
    }
}
