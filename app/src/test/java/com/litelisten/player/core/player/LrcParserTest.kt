package com.litelisten.player.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesTimeTagsAndSortsByTime() {
        val lines = LrcParser.parse(
            "[00:12.50]second line\n[00:03.20]first line\n[00:20]third line",
        )
        assertThat(lines).hasSize(3)
        assertThat(lines[0].timeMs).isEqualTo(3_200L)
        assertThat(lines[0].text).isEqualTo("first line")
        assertThat(lines[1].timeMs).isEqualTo(12_500L)
        assertThat(lines[2].timeMs).isEqualTo(20_000L)
    }

    @Test
    fun supportsMultipleTagsPerLineAndMetadata() {
        val lines = LrcParser.parse(
            "[ti:title]\n[ar:artist]\n[00:01.00][00:30.00]chorus",
        )
        assertThat(lines).hasSize(2)
        assertThat(lines.map { it.timeMs }).containsExactly(1_000L, 30_000L).inOrder()
        assertThat(lines[0].text).isEqualTo("chorus")
    }

    @Test
    fun appliesOffsetAndIgnoresBrokenLines() {
        val lines = LrcParser.parse(
            "[offset:+500]\nnot a lyric\n[00:10.00]delayed\n[bad]oops",
        )
        assertThat(lines).hasSize(1)
        assertThat(lines[0].timeMs).isEqualTo(9_500L)
    }

    @Test
    fun emptyContentProducesNoLines() {
        assertThat(LrcParser.parse("")).isEmpty()
        assertThat(LrcParser.parse("[ti:x]")).isEmpty()
    }
}
