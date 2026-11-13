package com.litelisten.player.core.source.api

import com.litelisten.player.core.model.Credentials

/** 凭据走 Android Keystore AES-GCM；实现在 P3 落地，P0 只冻结接口。 */
interface CredentialStore {
    suspend fun put(sourceId: String, username: String?, password: String?): Boolean
    suspend fun get(sourceId: String): Credentials?
    suspend fun remove(sourceId: String)
}
