package ir.hanzodev1375.ghostide.vibe;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;

/**
 * Persistence of the selected provider configuration (plain, private preferences) and of its API
 * key plus the generated local Hermes API key (Keystore-wrapped, see {@link VibeSecretStore}).
 *
 * <p>Methods documented as blocking touch the secret file and must not run on the main thread.
 */
public final class VibeProviderStore {

  private static final String PREFS_NAME = "ghost_vibe_provider";
  private static final String KEY_TYPE = "type";
  private static final String KEY_BASE_URL = "base_url";
  private static final String KEY_MODEL = "model";
  private static final String SECRET_DIR = "vibe";
  private static final String SECRET_FILE = "secrets.json";
  private static final String SECRET_PREFIX = "provider.";
  private static final String LOCAL_API_KEY = "hermes.api_key";
  private static final int LOCAL_API_KEY_BYTES = 32;
  private static final int MAX_SECRET_LENGTH = 4096;
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private static VibeProviderStore instance;

  private final SharedPreferences prefs;
  private final VibeSecretStore secrets;

  VibeProviderStore(SharedPreferences prefs, VibeSecretStore secrets) {
    this.prefs = prefs;
    this.secrets = secrets;
  }

  /** Returns the process-wide store; only the application context is retained. */
  @NonNull
  public static synchronized VibeProviderStore get(@NonNull Context context) {
    if (instance == null) {
      Context app = context.getApplicationContext();
      File dir = new File(app.getNoBackupFilesDir(), SECRET_DIR);
      instance =
          new VibeProviderStore(
              app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
              new VibeSecretStore(
                  new File(dir, SECRET_FILE).toPath(), new KeystoreSecretCipher()));
    }
    return instance;
  }

  /** The saved configuration, or {@code null} if none was saved or the saved one is invalid. */
  @Nullable
  public VibeProviderConfig load() {
    String typeName = prefs.getString(KEY_TYPE, null);
    if (typeName == null) {
      return null;
    }
    try {
      return VibeProviderConfig.create(
          VibeProviderType.valueOf(typeName),
          prefs.getString(KEY_BASE_URL, ""),
          prefs.getString(KEY_MODEL, ""));
    } catch (IllegalArgumentException | VibeConfigException e) {
      return null;
    }
  }

  /**
   * Saves {@code config} and, if given, its API key. Blocking.
   *
   * @param secret the new API key, or {@code null}/empty to keep the stored one; ignored for types
   *     that do not use a key
   * @throws VibeConfigException if the key is invalid or required but not available; nothing is
   *     saved then
   * @throws IOException if the key cannot be encrypted or written
   */
  public void save(@NonNull VibeProviderConfig config, @Nullable String secret)
      throws VibeConfigException, IOException {
    VibeProviderType type = config.getType();
    String cleanSecret = secret == null ? "" : secret.trim();
    if (type.needsSecret()) {
      if (cleanSecret.isEmpty() && !hasSecret(type)) {
        throw new VibeConfigException(
            VibeConfigException.Problem.SECRET_MISSING, "an API key is required");
      }
      if (!cleanSecret.isEmpty()) {
        validateSecret(cleanSecret);
        secrets.put(SECRET_PREFIX + type.name(), cleanSecret);
      }
    }
    prefs
        .edit()
        .putString(KEY_TYPE, type.name())
        .putString(KEY_BASE_URL, config.getBaseUrl())
        .putString(KEY_MODEL, config.getModel())
        .apply();
  }

  /** True if an API key is stored for {@code type}. Blocking. */
  public boolean hasSecret(@NonNull VibeProviderType type) {
    return secrets.contains(SECRET_PREFIX + type.name());
  }

  /** Removes the API key stored for {@code type}. Blocking. */
  public void clearSecret(@NonNull VibeProviderType type) throws IOException {
    secrets.remove(SECRET_PREFIX + type.name());
  }

  /** Removes the saved configuration and every provider key. Blocking. */
  public void clear() throws IOException {
    prefs.edit().clear().apply();
    for (VibeProviderType type : VibeProviderType.values()) {
      secrets.remove(SECRET_PREFIX + type.name());
    }
  }

  @Nullable
  String readSecret(@NonNull VibeProviderType type) {
    return secrets.get(SECRET_PREFIX + type.name());
  }

  /**
   * Returns the bearer key of the local Hermes API server, creating a random 256-bit one on first
   * use. Blocking.
   */
  @NonNull
  String localApiKey() throws IOException {
    synchronized (secrets) {
      String existing = secrets.get(LOCAL_API_KEY);
      if (existing != null) {
        return existing;
      }
      byte[] random = new byte[LOCAL_API_KEY_BYTES];
      new SecureRandom().nextBytes(random);
      char[] chars = new char[random.length * 2];
      for (int i = 0; i < random.length; i++) {
        chars[i * 2] = HEX[(random[i] >> 4) & 0xF];
        chars[i * 2 + 1] = HEX[random[i] & 0xF];
      }
      String created = new String(chars);
      secrets.put(LOCAL_API_KEY, created);
      return created;
    }
  }

  private static void validateSecret(String secret) throws VibeConfigException {
    if (secret.length() > MAX_SECRET_LENGTH) {
      throw new VibeConfigException(
          VibeConfigException.Problem.SECRET_INVALID, "the API key is too long");
    }
    for (int i = 0; i < secret.length(); i++) {
      char c = secret.charAt(i);
      if (c <= ' ' || c == 0x7f) {
        throw new VibeConfigException(
            VibeConfigException.Problem.SECRET_INVALID, "the API key contains whitespace");
      }
    }
  }
}
