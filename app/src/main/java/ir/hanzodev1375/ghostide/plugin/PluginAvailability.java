package ir.hanzodev1375.ghostide.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

/**
 * Persisted on/off choices for installed plugins: one global master switch plus one flag per
 * plugin id. Both default to "on", so a user who never touches the plugin manager keeps the
 * behaviour of loading every installed plugin.
 *
 * <p>This class only stores the user's intent. Loading and unloading according to it is done by
 * {@link PluginActivationController}, and {@code GplPluginLoader} refuses to activate a plugin for
 * which {@link #isEffectivelyEnabled(String)} is false. Plugin files are never touched.
 *
 * <p>Reads and writes are thread-safe: {@link SharedPreferences} serves them from memory after the
 * first load and {@link SharedPreferences.Editor#apply()} persists asynchronously.
 */
public final class PluginAvailability {

  static final String PREFS_NAME = "ghost_plugin_availability";
  static final String KEY_MASTER = "plugins_master_enabled";
  static final String KEY_PLUGIN_PREFIX = "plugin_enabled:";

  private static volatile PluginAvailability instance;

  private final SharedPreferences prefs;

  PluginAvailability(@NonNull SharedPreferences prefs) {
    this.prefs = prefs;
  }

  /** The process-wide store, backed by the application context. */
  public static PluginAvailability get(@NonNull Context context) {
    PluginAvailability local = instance;
    if (local == null) {
      synchronized (PluginAvailability.class) {
        local = instance;
        if (local == null) {
          local =
              new PluginAvailability(
                  context
                      .getApplicationContext()
                      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
          instance = local;
        }
      }
    }
    return local;
  }

  /** Whether plugins are allowed to run at all. Defaults to {@code true}. */
  public boolean isMasterEnabled() {
    return prefs.getBoolean(KEY_MASTER, true);
  }

  public void setMasterEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_MASTER, enabled).apply();
  }

  /**
   * The user's own choice for {@code pluginId}, independent of the master switch. A plugin that
   * was never switched off is enabled.
   */
  public boolean isPluginEnabled(@NonNull String pluginId) {
    return prefs.getBoolean(KEY_PLUGIN_PREFIX + pluginId, true);
  }

  public void setPluginEnabled(@NonNull String pluginId, boolean enabled) {
    prefs.edit().putBoolean(KEY_PLUGIN_PREFIX + pluginId, enabled).apply();
  }

  /** Forgets the per-plugin choice, e.g. after uninstalling, so a reinstall starts enabled. */
  public void forgetPlugin(@NonNull String pluginId) {
    prefs.edit().remove(KEY_PLUGIN_PREFIX + pluginId).apply();
  }

  /** Whether {@code pluginId} may be activated right now: master on and the plugin itself on. */
  public boolean isEffectivelyEnabled(@NonNull String pluginId) {
    return isMasterEnabled() && isPluginEnabled(pluginId);
  }
}
