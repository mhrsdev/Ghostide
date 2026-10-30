package ir.hanzodev1375.ghostide.vibe.ui.provider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.VibeConfigException;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Small pure helpers of the provider form. */
public final class ProviderForm {

  private static final String PROBE_MODEL = "probe";
  private static final String HTTPS = "https";

  private ProviderForm() {}

  /**
   * True if the backend would accept {@code baseUrl} for {@code type} and it uses cleartext http,
   * so the form can warn before anything is saved. Invalid addresses return false; they are
   * reported as validation errors when saving.
   */
  public static boolean isCleartext(@NonNull VibeProviderType type, @Nullable String baseUrl) {
    if (type == VibeProviderType.CHATGPT_ACCOUNT || baseUrl == null || baseUrl.isBlank()) {
      return false;
    }
    try {
      return VibeProviderConfig.create(type, baseUrl, PROBE_MODEL).isCleartext();
    } catch (VibeConfigException e) {
      return false;
    }
  }

  /**
   * True only for an absolute {@code https} URL with a host. Used before a verification page from
   * the sign-in helper is opened in a browser; anything else is never opened.
   */
  public static boolean isSafeHttpsUrl(@Nullable String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      URI uri = new URI(url.trim());
      return HTTPS.equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))
          && uri.getHost() != null
          && uri.getUserInfo() == null;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
