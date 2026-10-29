package ir.hanzodev1375.ghostide.vibe;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * {@link SecretCipher} backed by an AES-256-GCM key that lives in the Android Keystore and is not
 * extractable. The blob layout is {@code iv (12 bytes) || ciphertext || tag}.
 */
final class KeystoreSecretCipher implements SecretCipher {

  private static final String KEYSTORE = "AndroidKeyStore";
  private static final String KEY_ALIAS = "ghost_vibe_secrets_v1";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_BITS = 256;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private SecretKey cachedKey;

  @Override
  public synchronized byte[] encrypt(byte[] plain) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, key());
    byte[] iv = cipher.getIV();
    if (iv == null || iv.length != IV_BYTES) {
      throw new GeneralSecurityException("unexpected GCM IV length");
    }
    byte[] encrypted = cipher.doFinal(plain);
    byte[] blob = new byte[IV_BYTES + encrypted.length];
    System.arraycopy(iv, 0, blob, 0, IV_BYTES);
    System.arraycopy(encrypted, 0, blob, IV_BYTES, encrypted.length);
    return blob;
  }

  @Override
  public synchronized byte[] decrypt(byte[] blob) throws GeneralSecurityException {
    if (blob.length <= IV_BYTES) {
      throw new GeneralSecurityException("blob too short");
    }
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(
        Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, Arrays.copyOf(blob, IV_BYTES)));
    return cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
  }

  private SecretKey key() throws GeneralSecurityException {
    if (cachedKey != null) {
      return cachedKey;
    }
    try {
      KeyStore store = KeyStore.getInstance(KEYSTORE);
      store.load(null);
      KeyStore.Entry entry = store.getEntry(KEY_ALIAS, null);
      if (entry instanceof KeyStore.SecretKeyEntry) {
        cachedKey = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        return cachedKey;
      }
    } catch (IOException e) {
      throw new GeneralSecurityException("cannot open the Android Keystore", e);
    }
    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
    generator.init(
        new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build());
    cachedKey = generator.generateKey();
    return cachedKey;
  }
}
