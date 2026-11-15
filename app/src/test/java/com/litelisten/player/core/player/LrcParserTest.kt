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

    @Test
    fun plainLinesHaveNoWordTimeline() {
        val lines = LrcParser.parse("[00:01.50]first")
        assertThat(lines[0].words).isEmpty()
    }

    @Test
    fun enhancedLrcBuildsWordTimeline() {
        val lines = LrcParser.parse("[00:10.00]<00:10.00>深<00:10.50>蓝<00:11.00>的夜")
        assertThat(lines).hasSize(1)
        assertThat(lines[0].text).isEqualTo("深蓝的夜")
        val words = lines[0].words
        assertThat(words).hasSize(3)
        assertThat(words[0].startMs).isEqualTo(10_000L)
        assertThat(words[0].text).isEqualTo("深")
        assertThat(words[1].startMs).isEqualTo(10_500L)
        assertThat(words[1].text).isEqualTo("蓝")
        assertThat(words[2].startMs).isEqualTo(11_000L)
        assertThat(words[2].text).isEqualTo("的夜")
        // 前两字以「下一个字的开始」收尾；最后一个字没有下一句可依赖时给一个正时长。
        assertThat(words[0].endMs).isEqualTo(10_500L)
        assertThat(words[1].endMs).isEqualTo(11_000L)
        assertThat(words[2].endMs).isGreaterThan(11_000L)
    }

    @Test
    fun wordTimelineAlsoAppliesOffset() {
        val lines = LrcParser.parse(
            "[offset:+500]\n[00:10.00]<00:10.00>一<00:10.80>二<00:11.60>三",
        )
        val words = lines.single().words
        assertThat(words.map { it.startMs }).containsExactly(9_500L, 10_300L, 11_100L).inOrder()
    }

    @Test
    fun lineEndComesFromNextLineStart() {
        val lines = LrcParser.parse("[00:01.00]一\n[00:04.50]二\n[00:08.00]三")
        assertThat(lines[0].endMs).isEqualTo(4_500L)
        assertThat(lines[1].endMs).isEqualTo(8_000L)
        // 最后一行没有下一句，保留未知，由 effectiveEndMs 兜底。
        assertThat(lines[2].endMs).isEqualTo(0L)
        assertThat(lines[2].effectiveEndMs).isEqualTo(8_000L + LyricLine.DEFAULT_LINE_MS)
    }

    @Test
    fun lastWordUsesLineEndWhenNextLineKnown() {
        val lines = LrcParser.parse("[00:10.00]<00:10.00>甲<00:10.40>乙\n[00:12.00]下一句")
        val words = lines[0].words
        assertThat(words.last().endMs).isEqualTo(12_000L)
        assertThat(lines[0].endMs).isEqualTo(12_000L)
    }

    @Test
    fun outOfOrderWordTagsKeepSourceOrderAndPositiveDuration() {
        // 逐字标签乱序的歌词极少见，但保持原文顺序才不会让显示的文字错位；
        // 时间倒退时不能算出负时长，必须回退到估计值。
        val lines = LrcParser.parse("[00:05.00]<00:06.00>后<00:05.00>先")
        val words = lines.single().words
        assertThat(words.map { it.text }).containsExactly("后", "先").inOrder()
        assertThat(words.all { it.endMs > it.startMs }).isTrue()
    }
}
