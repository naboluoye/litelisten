package com.litelisten.player.core.source.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.litelisten.player.core.model.Credentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** 凭据用 Android Keystore 的 AES-256-GCM 加密后落盘，密文与 IV 都进 SharedPreferences。 */
@Singleton
class CredentialStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CredentialStore {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override suspend fun put(sourceId: String, username: String?, password: String?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("u", username ?: "")
                    put("p", password ?: "")
                }.toString()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                val iv = cipher.iv
                val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
                prefs.edit()
                    .putString(key("data", sourceId), Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(key("iv", sourceId), Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()
                true
            }.getOrDefault(false)
        }

    override suspend fun get(sourceId: String): Credentials? = withContext(Dispatchers.IO) {
        runCatching {
            val data = prefs.getString(key("data", sourceId), null) ?: return@withContext null
            val iv = prefs.getString(key("iv", sourceId), null) ?: return@withContext null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val plain = String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
            val obj = JSONObject(plain)
            val user = obj.optString("u")
            val pass = obj.optString("p")
            if (user.isBlank() && pass.isBlank()) null else Credentials(user, pass)
        }.getOrNull()
    }

    override suspend fun remove(sourceId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key("data", sourceId)).remove(key("iv", sourceId)).apply()
        Unit
    }

    private fun key(prefix: String, sourceId: String) = "$prefix:$sourceId"

    private fun secretKey(): java.security.Key {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
                init(spec)
                generateKey()
            }
        }
        return (store.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private companion object {
        const val PREFS = "litelisten_credentials"
        const val ALIAS = "litelisten_credential_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
