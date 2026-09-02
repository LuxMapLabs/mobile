package com.luxmap.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

// Bọc khoá AES/GCM trong AndroidKeyStore để mã hoá access/refresh token trước khi TokenStore
// lưu xuống DataStore (CLAUDE.md mobile: DataStore lưu token, Android Keystore bảo vệ token).
// Khoá sinh ra không bao giờ rời khỏi phần cứng bảo mật của thiết bị — chỉ IV + ciphertext được
// lưu, dạng Base64 một chuỗi duy nhất (12 byte IV đứng trước).
@Singleton
class TokenCipher
    @Inject
    constructor() {
        private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        fun encrypt(plainText: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = cipher.iv + cipherBytes
            return Base64.getEncoder().encodeToString(combined)
        }

        fun decrypt(encoded: String): String {
            val combined = Base64.getDecoder().decode(encoded)
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherBytes = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }

        private fun secretKey(): SecretKey = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()

        private fun generateKey(): SecretKey {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val spec =
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build()
            generator.init(spec)
            return generator.generateKey()
        }

        private companion object {
            const val ANDROID_KEY_STORE = "AndroidKeyStore"
            const val KEY_ALIAS = "luxmap_auth_key"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val KEY_SIZE_BITS = 256
            const val IV_LENGTH_BYTES = 12
            const val GCM_TAG_LENGTH_BITS = 128
        }
    }
