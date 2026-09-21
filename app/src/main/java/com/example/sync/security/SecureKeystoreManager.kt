package com.example.sync.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure Keystore Manager for Android.
 *
 * Protects long-term pairing secrets, authentication tokens, and sensitive credentials
 * using hardware-backed Android Keystore (AES-256-GCM).
 *
 * Plain text secrets are NEVER stored directly in Room or standard SharedPreferences.
 */
object SecureKeystoreManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "MyBusinessMasterKey_v1"
    private const val PREFS_NAME = "my_business_secure_storage"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // Fallback key for non-Android JVM environments (e.g. Unit Tests without Robolectric)
    private var fallbackTestKey: SecretKey? = null

    private fun getOrCreateMasterKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                return keyGenerator.generateKey()
            }
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        } catch (e: Exception) {
            // JVM Unit testing fallback
            if (fallbackTestKey == null) {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                fallbackTestKey = keyGen.generateKey()
            }
            return fallbackTestKey!!
        }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun storeSecret(context: Context, key: String, plaintext: String) {
        if (plaintext.isBlank()) return
        try {
            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            getEncryptedPrefs(context).edit().putString(key, encoded).apply()
        } catch (e: Exception) {
            android.util.Log.e("SecureKeystore", "Failed to store secret: ${e.message}")
        }
    }

    @Synchronized
    fun getSecret(context: Context, key: String): String? {
        try {
            val encoded = getEncryptedPrefs(context).getString(key, null) ?: return null
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return null
            val iv = ByteArray(GCM_IV_LENGTH)
            val encryptedBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)

            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecureKeystore", "Failed to retrieve secret: ${e.message}")
            return null
        }
    }

    @Synchronized
    fun removeSecret(context: Context, key: String) {
        getEncryptedPrefs(context).edit().remove(key).apply()
    }

    fun getDeviceAuthTokenKey(deviceId: String): String = "auth_token_$deviceId"
}
