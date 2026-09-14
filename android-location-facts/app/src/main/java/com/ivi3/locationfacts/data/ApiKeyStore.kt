package com.ivi3.locationfacts.data

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
 * Stores the user's Anthropic API key encrypted with a key held in the Android Keystore,
 * so the secret is not sitting in a readable preferences file.
 *
 * This protects the key at rest on the device. It does not change the bigger picture: an
 * app that calls the Claude API directly must hold a usable key on the phone, and anyone
 * with the unlocked device (or a debugger attached to the app) can get at it. For anything
 * beyond your own device, put a small server between the app and the API and let the phone
 * authenticate to that instead. See the README.
 */
class ApiKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasKey(): Boolean = prefs.contains(CIPHERTEXT)

    fun load(): String? {
        val stored = prefs.getString(CIPHERTEXT, null) ?: return null
        val blob = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (blob.size <= IV_BYTES) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = blob.copyOfRange(0, IV_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            // Key invalidated (app data restored to another device, keystore reset): treat
            // it as "no key stored" so the user is asked for it again.
            clear()
            null
        }
    }

    fun save(apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + encrypted
        prefs.edit().putString(CIPHERTEXT, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        prefs.edit().remove(CIPHERTEXT).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "location_facts_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "location_facts"
        const val CIPHERTEXT = "api_key_ciphertext"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
