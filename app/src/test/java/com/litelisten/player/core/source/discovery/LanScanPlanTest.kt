package com.litelisten.player.core.source.discovery

import com.litelisten.player.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScanPlanTest {

    private fun self(vararg octets: Int): Int {
        var value = 0
        octets.forEach { value = (value shl 8) or it }
        return value
    }

    @Test
    fun `hosts covers the whole class C except network broadcast and self`() {
        val list = LanScanPlan.hosts(self(192, 168, 1, 5), 24)
        assertEquals(253, list.size)
        assertTrue(list.contains("192.168.1.1"))
        assertTrue(list.contains("192.168.1.254"))
        assertFalse(list.contains("192.168.1.5"))
        assertFalse(list.contains("192.168.1.0"))
        assertFalse(list.contains("192.168.1.255"))
    }

    @Test
    fun `wide subnet is narrowed to the local class C`() {
        val list = LanScanPlan.hosts(self(10, 0, 3, 9), 16)
        assertEquals(253, list.size)
        assertTrue(list.all { it.startsWith("10.0.3.") })
    }

    @Test
    fun `small subnet respects its real prefix`() {
        // /30 只有 .5 和 .6 两个可用主机，排除本机 .5 后只剩 .6。
        val list = LanScanPlan.hosts(self(192, 168, 1, 5), 30)
        assertEquals(1, list.size)
        assertEquals("192.168.1.6", list.single())
    }

    @Test
    fun `ip conversion round trips`() {
        val ip = "172.16.31.7"
        assertEquals(ip, LanScanPlan.intToIp((LanScanPlan.ipToInt(ip)!!.toLong() and 0xffffffffL)))
        assertEquals("192.168.1.1", LanScanPlan.intToIp(0xc0a80101L))
    }

    @Test
    fun `port classification picks SMB only for SMB ports`() {
        assertEquals(SourceType.SMB, LanScanPlan.typeOf(445))
        assertEquals(SourceType.SMB, LanScanPlan.typeOf(139))
        assertEquals(SourceType.WEBDAV, LanScanPlan.typeOf(5000))
        assertEquals(SourceType.WEBDAV, LanScanPlan.typeOf(8080))
    }
}
