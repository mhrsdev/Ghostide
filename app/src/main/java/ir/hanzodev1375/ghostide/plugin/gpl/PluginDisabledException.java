package ir.hanzodev1375.ghostide.plugin.gpl;

import java.io.IOException;

/**
 * Thrown by {@link GplPluginLoader#load(java.io.File)} when the plugin may not be activated
 * because the user turned it, or all plugins, off. The {@code .gpl} file itself is fine.
 *
 * <p>It extends {@link IOException} so every existing caller, which already handles a failed load,
 * treats it as "not loaded" without further changes.
 */
public final class PluginDisabledException extends IOException {

  private final String pluginId;

  public PluginDisabledException(String pluginId) {
    super("Plugin " + pluginId + " is turned off");
    this.pluginId = pluginId;
  }

  /** Manifest id of the plugin that was not activated. */
  public String getPluginId() {
    return pluginId;
  }
}
