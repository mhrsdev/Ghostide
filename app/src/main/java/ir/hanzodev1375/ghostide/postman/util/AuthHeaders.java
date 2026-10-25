package ir.hanzodev1375.ghostide.postman.util;

import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.postman.model.KeyValueItem;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds and reads the {@code Authorization} header behind the request editor's Auth tab.
 *
 * <p>Credentials are not a separate field of {@link
 * ir.hanzodev1375.ghostide.postman.model.RequestSnapshot}: the Auth tab writes an ordinary header
 * row, so history, collections and the stored JSON format stay exactly as they were.
 */
public final class AuthHeaders {

  public static final String HEADER_NAME = "Authorization";

  public static final int TYPE_NONE = 0;
  public static final int TYPE_BEARER = 1;
  public static final int TYPE_BASIC = 2;

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String BASIC_PREFIX = "Basic ";

  private AuthHeaders() {}

  /** Credentials recovered from an existing header value. */
  public static final class Parsed {
    public final int type;
    public final String token;
    public final String user;
    public final String password;

    Parsed(int type, String token, String user, String password) {
      this.type = type;
      this.token = token;
      this.user = user;
      this.password = password;
    }
  }

  /**
   * @return the header value for the given credentials, or {@code null} when they are incomplete
   *     (blank token, or blank user name for Basic)
   */
  @Nullable
  public static String build(int type, String token, String user, String password) {
    if (type == TYPE_BEARER) {
      String trimmed = token == null ? "" : token.trim();
      return trimmed.isEmpty() ? null : BEARER_PREFIX + trimmed;
    }
    if (type == TYPE_BASIC) {
      String name = user == null ? "" : user;
      if (name.trim().isEmpty()) {
        return null;
      }
      String pair = name + ":" + (password == null ? "" : password);
      return BASIC_PREFIX
          + Base64.encodeToString(pair.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }
    return null;
  }

  /** Reads Bearer and Basic values; anything else is reported as {@link #TYPE_NONE}. */
  @NonNull
  public static Parsed parse(@Nullable String value) {
    String v = value == null ? "" : value.trim();
    if (v.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return new Parsed(TYPE_BEARER, v.substring(BEARER_PREFIX.length()).trim(), "", "");
    }
    if (v.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
      try {
        byte[] decoded = Base64.decode(v.substring(BASIC_PREFIX.length()).trim(), Base64.DEFAULT);
        String pair = new String(decoded, StandardCharsets.UTF_8);
        int colon = pair.indexOf(':');
        if (colon > 0) {
          return new Parsed(TYPE_BASIC, "", pair.substring(0, colon), pair.substring(colon + 1));
        }
      } catch (IllegalArgumentException notBase64) {
        return new Parsed(TYPE_NONE, "", "", "");
      }
    }
    return new Parsed(TYPE_NONE, "", "", "");
  }

  /** @return the index of the first enabled {@code Authorization} row, or -1 */
  public static int findEnabled(@NonNull List<KeyValueItem> headers) {
    for (int i = 0; i < headers.size(); i++) {
      KeyValueItem item = headers.get(i);
      if (item.isEnabled() && isAuthorization(item)) {
        return i;
      }
    }
    return -1;
  }

  /** @return the index of the first {@code Authorization} row, enabled or not, or -1 */
  public static int find(@NonNull List<KeyValueItem> headers) {
    for (int i = 0; i < headers.size(); i++) {
      if (isAuthorization(headers.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isAuthorization(KeyValueItem item) {
    String key = item.getKey();
    return key != null && HEADER_NAME.equalsIgnoreCase(key.trim());
  }
}
