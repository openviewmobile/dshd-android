package com.dshd.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the provider API key with a hardware-backed Android Keystore key. */
object ProviderSecretStore {
  private const val ALIAS = "dshd-provider-key"
  private const val FILE = "provider-key.enc"
  private const val VERSION: Byte = 1

  @JvmStatic
  fun has(context: Context): Boolean {
    return File(context.filesDir, FILE).isFile
  }

  @JvmStatic
  @Throws(Exception::class)
  fun save(context: Context, secret: String) {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(secret.toByteArray(StandardCharsets.UTF_8))
    val payload = ByteBuffer.allocate(1 + iv.size + ciphertext.size)
    payload.put(VERSION)
    payload.put(iv)
    payload.put(ciphertext)

    val target = File(context.filesDir, FILE)
    val temp = File(context.filesDir, FILE + ".tmp")
    FileOutputStream(temp).use { output ->
      output.write(payload.array())
      output.fd.sync()
    }
    if (!temp.renameTo(target)) {
      //noinspection ResultOfMethodCallIgnored
      temp.delete()
      throw Exception("cannot write provider secret")
    }
  }

  @JvmStatic
  @Throws(Exception::class)
  fun load(context: Context): String {
    val file = File(context.filesDir, FILE)
    if (!file.isFile) return ""
    val data = ByteArray(file.length().toInt())
    FileInputStream(file).use { input ->
      var offset = 0
      while (offset < data.size) {
        val read = input.read(data, offset, data.size - offset)
        if (read < 0) break
        offset += read
      }
      if (offset != data.size) throw Exception("truncated provider secret")
    }
    val payload = ByteBuffer.wrap(data)
    if (data.size < 2 || payload.get() != VERSION) throw Exception("bad provider secret")
    val ivLength = 12
    if (data.size <= 1 + ivLength) throw Exception("bad provider secret IV")
    val iv = ByteArray(ivLength)
    payload.get(iv)
    val ciphertext = ByteArray(payload.remaining())
    payload.get(ciphertext)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
    return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
  }

  @Throws(Exception::class)
  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore")
    store.load(null as KeyStore.LoadStoreParameter?)
    if (!store.containsAlias(ALIAS)) {
      val generator = KeyGenerator.getInstance(
          KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      generator.init(KeyGenParameterSpec.Builder(ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build())
      generator.generateKey()
    }
    return store.getKey(ALIAS, null) as SecretKey
  }
}
