package ir.hanzodev1375.ghostide.vibe;

import java.security.GeneralSecurityException;

/** Authenticated encryption of small secrets; the key never leaves the implementation. */
interface SecretCipher {

  /** Encrypts {@code plain}; the result contains everything needed by {@link #decrypt}. */
  byte[] encrypt(byte[] plain) throws GeneralSecurityException;

  /** Decrypts a blob produced by {@link #encrypt}; fails if it was tampered with or the key changed. */
  byte[] decrypt(byte[] blob) throws GeneralSecurityException;
}
