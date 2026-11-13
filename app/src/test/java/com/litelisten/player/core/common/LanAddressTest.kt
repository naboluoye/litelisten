package com.litelisten.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 硬约束「不联网」的回归防线：任何非私网 host 都必须在 CI 里被拦下。 */
class LanAddressTest {
    @Test
    fun `rfc1918 ranges are allowed`() {
        assertThat(LanAddress.isPrivateHost("192.168.1.10")).isTrue()
        assertThat(LanAddress.isPrivateHost("10.0.0.5")).isTrue()
        assertThat(LanAddress.isPrivateHost("172.16.0.1")).isTrue()
        assertThat(LanAddress.isPrivateHost("172.31.255.254")).isTrue()
        assertThat(LanAddress.isPrivateHost("169.254.3.4")).isTrue()
    }

    @Test
    fun `public addresses are rejected`() {
        assertThat(LanAddress.isPrivateHost("8.8.8.8")).isFalse()
        assertThat(LanAddress.isPrivateHost("172.32.0.1")).isFalse()
        assertThat(LanAddress.isPrivateHost("1.1.1.1")).isFalse()
        assertThat(LanAddress.isPrivateHost("music.example.com")).isFalse()
    }

    @Test
    fun `loopback mdns and ipv6 ula`() {
        assertThat(LanAddress.isPrivateHost("127.0.0.1")).isTrue()
        assertThat(LanAddress.isPrivateHost("nas.local")).isTrue()
        assertThat(LanAddress.isPrivateHost("localhost")).isTrue()
        assertThat(LanAddress.isPrivateHost("::1")).isTrue()
        assertThat(LanAddress.isPrivateHost("[fe80::1]")).isTrue()
        assertThat(LanAddress.isPrivateHost("fd00::a")).isTrue()
        assertThat(LanAddress.isPrivateHost("2001:4860:4860::8888")).isFalse()
    }

    @Test
    fun `malformed input is rejected closed`() {
        assertThat(LanAddress.isPrivateHost("")).isFalse()
        assertThat(LanAddress.isPrivateHost("999.1.1.1")).isFalse()
        assertThat(LanAddress.isPrivateHost("192.168.1")).isFalse()
    }
}
