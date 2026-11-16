package com.litelisten.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DurationFilterTest {
    private val min = DurationFilter.secondsToMs(30)   // 30s
    private val max = DurationFilter.secondsToMs(600)  // 10min

    @Test
    fun unknownDurationIsAlwaysKept() {
        assertThat(DurationFilter.keep(0L, min, max)).isTrue()
    }

    @Test
    fun shortAudioBelowMinIsDropped() {
        assertThat(DurationFilter.keep(20_000L, min, max)).isFalse()
        assertThat(DurationFilter.keep(29_999L, min, max)).isFalse()
    }

    @Test
    fun boundaryAndNormalSongsAreKept() {
        assertThat(DurationFilter.keep(30_000L, min, max)).isTrue()
        assertThat(DurationFilter.keep(210_000L, min, max)).isTrue()
    }

    @Test
    fun longRecordingAboveMaxIsDropped() {
        assertThat(DurationFilter.keep(600_001L, min, max)).isFalse()
        assertThat(DurationFilter.keep(7200_000L, min, max)).isFalse()
    }

    @Test
    fun zeroMeansNoLimitOnThatSide() {
        assertThat(DurationFilter.keep(1_000L, 0L, 0L)).isTrue()
        assertThat(DurationFilter.keep(99_999_999L, 0L, 0L)).isTrue()
        // 只有上限：短音频不设限
        assertThat(DurationFilter.keep(1_000L, 0L, max)).isTrue()
        // 只有下限：长音频不设限
        assertThat(DurationFilter.keep(99_999_999L, min, 0L)).isTrue()
    }

    @Test
    fun secondsToMsTreatsNonPositiveAsUnlimited() {
        assertThat(DurationFilter.secondsToMs(0)).isEqualTo(0L)
        assertThat(DurationFilter.secondsToMs(-5)).isEqualTo(0L)
        assertThat(DurationFilter.secondsToMs(45)).isEqualTo(45_000L)
    }
}
