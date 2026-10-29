package ir.hanzodev1375.ghostide.vibe;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists a small set of named secrets (provider API keys, the local Hermes API key) encrypted
 * with a {@link SecretCipher}. The file holds {@code name -> base64(blob)} and never plaintext.
 *
 * <p>An entry that can no longer be decrypted (key removed by a lock-screen change, restore on
 * another device) is dropped and reported as absent so the user is asked again instead of the app
 * failing. All methods are thread-safe and do file I/O: call them off the main thread.
 */
final class VibeSecretStore {

  private static final String TAG = "GhostVibeSecrets";
  private static final java.lang.reflect.Type MAP_TYPE =
      new TypeToken<LinkedHashMap<String, String>>() {}.getType();

  private final Path file;
  private final SecretCipher cipher;
  private final Gson gson = new Gson();
  private Map<String, String> entries;

  VibeSecretStore(@NonNull Path file, @NonNull SecretCipher cipher) {
    this.file = file;
    this.cipher = cipher;
  }

  /** Returns the secret stored under {@code name}, or {@code null} if absent or unreadable. */
  @Nullable
  synchronized String get(@NonNull String name) {
    Map<String, String> map = load();
    String encoded = map.get(name);
    if (encoded == null) {
      return null;
    }
    try {
      byte[] plain = cipher.decrypt(Base64.getDecoder().decode(encoded));
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      Log.w(TAG, "dropping an unreadable secret entry", e);
      map.remove(name);
      persistQuietly(map);
      return null;
    }
  }

  /** True if a readable secret exists under {@code name}. */
  synchronized boolean contains(@NonNull String name) {
    return get(name) != null;
  }

  /**
   * Encrypts and stores {@code value} under {@code name}.
   *
   * @throws IOException if the secret cannot be encrypted or written; nothing is changed then
   */
  synchronized void put(@NonNull String name, @NonNull String value) throws IOException {
    Map<String, String> next = new LinkedHashMap<>(load());
    try {
      byte[] blob = cipher.encrypt(value.getBytes(StandardCharsets.UTF_8));
      next.put(name, Base64.getEncoder().encodeToString(blob));
    } catch (GeneralSecurityException e) {
      throw new IOException("cannot encrypt the secret", e);
    }
    persist(next);
    entries = next;
  }

  /** Removes the secret stored under {@code name}; absent names are ignored. */
  synchronized void remove(@NonNull String name) throws IOException {
    Map<String, String> next = new LinkedHashMap<>(load());
    if (next.remove(name) != null) {
      persist(next);
      entries = next;
    }
  }

  private Map<String, String> load() {
    if (entries != null) {
      return entries;
    }
    Map<String, String> loaded = null;
    try {
      String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      loaded = gson.fromJson(json, MAP_TYPE);
    } catch (NoSuchFileException absent) {
      loaded = null;
    } catch (IOException | JsonSyntaxException e) {
      Log.w(TAG, "secret file unreadable, starting empty", e);
    }
    entries = loaded == null ? new LinkedHashMap<>() : loaded;
    return entries;
  }

  private void persist(Map<String, String> map) throws IOException {
    VibeFiles.writeAtomically(file, gson.toJson(map, MAP_TYPE).getBytes(StandardCharsets.UTF_8));
  }

  private void persistQuietly(Map<String, String> map) {
    try {
      persist(map);
    } catch (IOException e) {
      Log.w(TAG, "could not rewrite the secret file", e);
    }
  }
}
