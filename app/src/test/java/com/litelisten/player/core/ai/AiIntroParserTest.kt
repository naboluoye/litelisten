package com.litelisten.player.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiIntroParserTest {

    private val bg = "[BACKGROUND]"
    private val artist = "[ARTIST]"
    private val lyrics = "[LYRICS]"

    @Test
    fun `structured reply splits into three sections`() {
        val text = "$bg this is the background. $artist the singer is great. ${lyrics}the song is about rain."
        val s = AiIntroParser.parse(text, bg, artist, lyrics)
        assertThat(s.isStructured).isTrue()
        assertThat(s.background).isEqualTo("this is the background.")
        assertThat(s.artist).isEqualTo("the singer is great.")
        assertThat(s.lyricsIntro).isEqualTo("the song is about rain.")
    }

    @Test
    fun `missing any marker falls back to raw text`() {
        val text = "$bg background only, artist section missing"
        val s = AiIntroParser.parse(text, bg, artist, lyrics)
        assertThat(s.isStructured).isFalse()
        assertThat(s.raw).isEqualTo(text)
    }

    @Test
    fun `empty reply produces unstructured result`() {
        val s = AiIntroParser.parse("", bg, artist, lyrics)
        assertThat(s.isStructured).isFalse()
        assertThat(s.raw).isEmpty()
    }

    @Test
    fun `markers appearing later in text still parse in order`() {
        val text = "intro sentence. $bg b1 b2 $artist a1 $lyrics l1 l2"
        val s = AiIntroParser.parse(text, bg, artist, lyrics)
        assertThat(s.background).isEqualTo("b1 b2")
        assertThat(s.artist).isEqualTo("a1")
        assertThat(s.lyricsIntro).isEqualTo("l1 l2")
    }
}
