package com.litelisten.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinyinTest {
    @Test
    fun `normalize lower removes whitespace`() {
        assertThat(Pinyin.normalizeLower("  Qing Tian \t周杰伦 ")).isEqualTo("qingtian周杰伦")
    }

    @Test
    fun `fts query escapes special characters`() {
        assertThat(Pinyin.buildFtsQuery("晴天 qt")).isEqualTo("\"晴天\"* OR \"qt\"*")
        assertThat(Pinyin.buildFtsQuery("a OR b")).isEqualTo("\"a\"* OR \"or\"* OR \"b\"*")
    }

    @Test
    fun `blank input yields empty query`() {
        assertThat(Pinyin.buildFtsQuery("   ")).isEmpty()
        assertThat(Pinyin.buildFtsQuery("***")).isEmpty()
    }
}
