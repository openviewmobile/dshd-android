package com.dshd.android;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the provider API key with a hardware-backed Android Keystore key. */
final class ProviderSecretStore {
  private static final String ALIAS = "dshd-provider-key";
  private static final String FILE = "provider-key.enc";
  private static final byte VERSION = 1;

  private ProviderSecretStore() {}

  static boolean has(Context context) {
    return new File(context.getFilesDir(), FILE).isFile();
  }

  static void save(Context context, String secret) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    byte[] iv = cipher.getIV();
    byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
    ByteBuffer payload = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
    payload.put(VERSION);
    payload.put(iv);
    payload.put(ciphertext);

    File target = new File(context.getFilesDir(), FILE);
    File temp = new File(context.getFilesDir(), FILE + ".tmp");
    try (FileOutputStream output = new FileOutputStream(temp)) {
      output.write(payload.array());
      output.getFD().sync();
    }
    if (!temp.renameTo(target)) {
      //noinspection ResultOfMethodCallIgnored
      temp.delete();
      throw new Exception("cannot write provider secret");
    }
  }

  static String load(Context context) throws Exception {
    File file = new File(context.getFilesDir(), FILE);
    if (!file.isFile()) return "";
    byte[] data = new byte[(int) file.length()];
    try (FileInputStream input = new FileInputStream(file)) {
      int offset = 0;
      while (offset < data.length) {
        int read = input.read(data, offset, data.length - offset);
        if (read < 0) break;
        offset += read;
      }
      if (offset != data.length) throw new Exception("truncated provider secret");
    }
    ByteBuffer payload = ByteBuffer.wrap(data);
    if (data.length < 2 || payload.get() != VERSION) throw new Exception("bad provider secret");
    int ivLength = 12;
    if (data.length <= 1 + ivLength) throw new Exception("bad provider secret IV");
    byte[] iv = new byte[ivLength];
    payload.get(iv);
    byte[] ciphertext = new byte[payload.remaining()];
    payload.get(ciphertext);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
    return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
  }

  private static SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias(ALIAS)) {
      KeyGenerator generator = KeyGenerator.getInstance(
          KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(new KeyGenParameterSpec.Builder(ALIAS,
          KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build());
      generator.generateKey();
    }
    return (SecretKey) store.getKey(ALIAS, null);
  }
}
