package net.automationse.relayharbor.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The relay access token is user supplied, never embedded in the APK, and
 * encrypted using an Android Keystore hardware-backed key where available.
 * Provider master keys (OpenAI/Dify) belong only on the relay server.
 */
class RelaySecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("relay_secret_v1", Context.MODE_PRIVATE)
    private val alias = "relayharbor_client_token_aes_v1"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun hasToken(): Boolean = getToken() != null

    fun getToken(): String? = runCatching {
        val iv = prefs.getString("iv", null) ?: return@runCatching null
        val bytes = prefs.getString("ciphertext", null) ?: return@runCatching null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, secretKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(bytes, Base64.NO_WRAP)), Charsets.UTF_8)
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    fun setToken(value: String) {
        require(value.length >= 32) { "接続トークンは32文字以上にしてください" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()) { "接続トークンを保存できませんでした" }
    }

    fun clearToken() {
        prefs.edit().remove("iv").remove("ciphertext").apply()
    }
}
