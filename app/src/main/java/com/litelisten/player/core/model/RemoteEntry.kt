package com.litelisten.player.core.model

/** 浏览网络目录时的一条记录（尚未入库）。媒体服务器会额外带标签与音质元数据。 */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val lastModified: Long? = null,
    val mimeType: String? = null,
    val playableUri: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val bitrateKbps: Int? = null,
    val artworkUri: String? = null,
)

data class ShareInfo(val name: String, val comment: String? = null)

data class SourceInfo(
    val displayName: String,
    val detail: String,
    val supportsSearch: Boolean = false,
)

/** 已保存的来源（Repository 面向 UI 的视图，不含任何凭据）。 */
data class Source(
    val id: String,
    val type: SourceType,
    val name: String,
    val detail: String,
    val enabled: Boolean = true,
    val trackCount: Int = 0,
    val lastConnectedAt: Long? = null,
)

/** SSDP / 局域网扫描实时发现的设备。 */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val type: SourceType,
    val address: String,
    /** 端口等附加信息，UI 用 strings.xml 格式化后展示。 */
    val detail: String = "",
    val port: Int? = null,
)
