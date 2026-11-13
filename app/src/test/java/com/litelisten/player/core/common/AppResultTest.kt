package com.litelisten.player.core.common

import com.google.common.truth.Truth.assertThat
import com.litelisten.player.core.model.SourceError
import org.junit.Test

class AppResultTest {
    @Test
    fun `success maps value`() {
        val result = Result.success(42).toAppResult()
        assertThat(result).isEqualTo(AppResult.Success(42))
    }

    @Test
    fun `failure maps to SourceError`() {
        val result = Result.failure<Int>(IllegalStateException("boom")).toAppResult()
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error).isInstanceOf(SourceError.Unknown::class.java)
    }

    @Test
    fun `custom error mapping`() {
        val result = Result.failure<Int>(IllegalArgumentException())
            .toAppResult { SourceError.AuthFailed() }
        assertThat((result as AppResult.Failure).error).isInstanceOf(SourceError.AuthFailed::class.java)
    }
}
