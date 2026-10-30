package ir.hanzodev1375.ghostide.vibe.ui.setup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;
import okhttp3.HttpUrl;

/** Decides whether a provider can work without internet access. */
public final class ProviderNetwork {

  private static final int PRIVATE_172_FIRST = 16;
  private static final int PRIVATE_172_LAST = 31;
  private static final int IPV4_PARTS = 4;

  private ProviderNetwork() {}

  /**
   * True unless the provider is an OpenAI-compatible server on this device or the local network
   * (loopback, private IPv4 range or a {@code .local} name), which keeps working offline.
   */
  public static boolean needsInternet(@Nullable VibeProviderConfig config) {
    if (config == null || config.getType() != VibeProviderType.OPENAI_COMPATIBLE) {
      return true;
    }
    HttpUrl url = HttpUrl.parse(config.getBaseUrl());
    return url == null || !isLocalHost(url.host());
  }

  static boolean isLocalHost(@NonNull String host) {
    if ("localhost".equals(host) || "::1".equals(host) || host.endsWith(".local")) {
      return true;
    }
    if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
      return true;
    }
    if (host.startsWith("172.")) {
      String[] parts = host.split("\\.");
      if (parts.length == IPV4_PARTS) {
        try {
          int second = Integer.parseInt(parts[1]);
          return second >= PRIVATE_172_FIRST && second <= PRIVATE_172_LAST;
        } catch (NumberFormatException e) {
          return false;
        }
      }
    }
    return false;
  }
}
