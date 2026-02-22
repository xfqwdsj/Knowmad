/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.ltfan.knowmad.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.Immutable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

open class CryptoManager private constructor(private val keyAlias: String) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private val logger = Logger("CryptoManager")
    }

    object LLMApiKey : CryptoManager("knowmad_llm_api_key") {
        fun encryptOrPlain(apiKey: String): CryptoData {
            try {
                if (isKeyInitialized()) {
                    return encrypt(apiKey.toByteArray())
                }
            } catch (e: Throwable) {
                logger.error(e) { "Failed to encrypt API key, falling back to plain storage" }
            }
            return CryptoData.Plain(apiKey.toByteArray())
        }
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    fun isKeyInitialized(): Boolean {
        return keyStore.containsAlias(keyAlias)
    }

    fun generateKey(userAuthenticationRequired: Boolean = false) {
        if (isKeyInitialized()) return

        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (userAuthenticationRequired) {
            builder.setUserAuthenticationRequired(true)
            // You might want to set validity duration seconds if needed
            // builder.setUserAuthenticationValidityDurationSeconds(30)
        }

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    private fun getKey(): SecretKey {
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    fun encrypt(data: ByteArray): CryptoData.Encrypted {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        return CryptoData.Encrypted(ciphertext, iv)
    }

    fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)
        return cipher.doFinal(ciphertext)
    }

    fun decrypt(data: CryptoData.Encrypted): ByteArray {
        return decrypt(data.value, data.iv)
    }
}

@Immutable
sealed interface CryptoData {
    val value: ByteArray

    @Immutable
    data class Plain(
        override val value: ByteArray,
    ) : CryptoData {
        override fun component2() = null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Plain

            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }
    }

    @Immutable
    data class Encrypted(
        override val value: ByteArray,
        val iv: ByteArray,
    ) : CryptoData {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Encrypted

            if (!value.contentEquals(other.value)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = value.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    operator fun component1(): ByteArray = value
    operator fun component2(): ByteArray?
}
