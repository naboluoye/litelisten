package com.litelisten.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeFormatTest {
    @Test
    fun `zero and sub-second`() {
        assertThat(TimeFormat.formatDuration(0L)).isEqualTo("00:00")
        assertThat(TimeFormat.formatDuration(999L)).isEqualTo("00:00")
    }

    @Test
    fun `mm ss boundary`() {
        assertThat(TimeFormat.formatDuration(119_000L)).isEqualTo("01:59")
        assertThat(TimeFormat.formatDuration(3_599_000L)).isEqualTo("59:59")
    }

    @Test
    fun `h mm ss boundary`() {
        assertThat(TimeFormat.formatDuration(3_600_000L)).isEqualTo("1:00:00")
        assertThat(TimeFormat.formatDuration(3_661_000L)).isEqualTo("1:01:01")
    }

    @Test
    fun `negative is clamped`() {
        assertThat(TimeFormat.formatDuration(-5_000L)).isEqualTo("00:00")
    }
}
