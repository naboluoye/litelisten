package com.litelisten.player.core.model

import kotlinx.serialization.Serializable

/** 可序列化的来源配置；密码永远不进这里，只有 credentialId 指向 Keystore。 */
@Serializable
data class SourceConfig(
    val id: String,
    val type: SourceType,
    val name: String,
    val host: String? = null,
    val port: Int? = null,
    val share: String? = null,
    val basePath: String? = null,
    val username: String? = null,
    val domain: String? = null,
    val anonymous: Boolean = false,
    val useTls: Boolean = false,
    val url: String? = null,
    val deviceUuid: String? = null,
    val controlUrl: String? = null,
    val isScanRoot: Boolean = false,
) {
    // toString 脱敏：崩溃堆栈 / 诊断日志都不会带出密码字段。
    override fun toString(): String =
        "SourceConfig(id=$id, type=$type, name=$name, host=$host, port=$port, share=$share)"
}

data class Credentials(val username: String?, val password: String?) {
    override fun toString(): String = "Credentials(username=***, password=***)"
}
