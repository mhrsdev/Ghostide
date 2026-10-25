package ir.hanzodev1375.ghostide.plugin;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.iconpack.IconPackManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells whether stopping a plugin would visibly interrupt something the user is using right now:
 * an open plugin screen, the selected wallpaper renderer or the selected file icon pack.
 *
 * <p>Open plugin screens report themselves through {@link #screenOpened(String)} and {@link
 * #screenClosed(String)}; the other uses are read from the existing selection preferences.
 */
public final class PluginUsage {

  private static final Map<String, Integer> OPEN_SCREENS = new HashMap<>();

  private PluginUsage() {}

  /** Records that a {@code PluginScreen} owned by {@code ownerPluginId} is showing. */
  public static void screenOpened(@Nullable String ownerPluginId) {
    if (ownerPluginId == null) {
      return;
    }
    synchronized (OPEN_SCREENS) {
      OPEN_SCREENS.merge(ownerPluginId, 1, Integer::sum);
    }
  }

  /** Balances an earlier {@link #screenOpened(String)}. */
  public static void screenClosed(@Nullable String ownerPluginId) {
    if (ownerPluginId == null) {
      return;
    }
    synchronized (OPEN_SCREENS) {
      Integer count = OPEN_SCREENS.get(ownerPluginId);
      if (count == null || count <= 1) {
        OPEN_SCREENS.remove(ownerPluginId);
      } else {
        OPEN_SCREENS.put(ownerPluginId, count - 1);
      }
    }
  }

  /**
   * Localized, user-facing descriptions of what {@code pluginId} is currently doing for the user;
   * empty when stopping it interrupts nothing visible.
   */
  @NonNull
  public static List<String> describeUse(@NonNull Context context, @NonNull String pluginId) {
    List<String> uses = new ArrayList<>();
    synchronized (OPEN_SCREENS) {
      if (OPEN_SCREENS.containsKey(pluginId)) {
        uses.add(context.getString(R.string.plugin_manager_use_screen));
      }
    }
    if (pluginId.equals(BackgroundPluginManager.selected(context))) {
      uses.add(context.getString(R.string.plugin_manager_use_background));
    }
    if (pluginId.equals(IconPackManager.selected(context))) {
      uses.add(context.getString(R.string.plugin_manager_use_icons));
    }
    return uses;
  }
}
