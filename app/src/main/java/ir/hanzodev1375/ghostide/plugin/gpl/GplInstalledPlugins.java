package ir.hanzodev1375.ghostide.plugin.gpl;

import android.content.Context;
import android.util.Log;

import ir.hanzodev1375.components.store.data.PluginRepository;
import ir.hanzodev1375.ghostide.plugin.PluginAvailability;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Where installed {@code .gpl} files live, and the scan that (re)activates all of them. */
public final class GplInstalledPlugins {

  private static final String TAG = "GplInstalledPlugins";
  private static final String GPL_EXTENSION = ".gpl";

  private GplInstalledPlugins() {}

  public static File installDir(Context context) {
    File dir = PluginRepository.pluginsDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
    return dir;
  }

  /**
   * File name of the installed package for {@code pluginId}: {@code <id>.gpl}, with the id passed
   * through {@link #safePathSegment(String)} so a manifest id can never place the package outside
   * {@link #installDir(Context)}.
   */
  public static String packageFileName(String pluginId) {
    return safePathSegment(pluginId) + GPL_EXTENSION;
  }

  /**
   * Turns an untrusted plugin id into a single path segment. Ids that are already a plain segment
   * (every reverse-domain id) are returned unchanged, so existing files and caches keep their
   * names; {@code '/'} and NUL become {@code '_'}, and the special names {@code "."} and {@code
   * ".."} have their dots replaced.
   *
   * @param pluginId id read from a package manifest; {@code null} becomes {@code "null"}, as the
   *     former string concatenation produced
   * @return a name that resolves to a direct child of the directory it is joined to
   */
  public static String safePathSegment(String pluginId) {
    String segment = String.valueOf(pluginId).replace('/', '_').replace('\0', '_');
    if (".".equals(segment) || "..".equals(segment)) {
      return segment.replace('.', '_');
    }
    return segment;
  }

  public static List<File> listInstalled(Context context) {
    File[] files = installDir(context).listFiles((dir, name) -> name.endsWith(GPL_EXTENSION));
    if (files == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(files);
  }

  /**
   * Loads every installed plugin not already active, except those turned off in {@link
   * PluginAvailability} (individually or through the master switch). A broken plugin is skipped,
   * not fatal.
   */
  public static void loadAll(Context context, GplPluginLoader loader) {
    PluginAvailability availability = PluginAvailability.get(context);
    for (File file : listInstalled(context)) {
      try {
        GplManifest manifest = GplManifestReader.read(file);
        if (manifest == null) {
          Log.w(TAG, "Skipping plugin with unreadable manifest: " + file);
          continue;
        }
        if (!availability.isEffectivelyEnabled(manifest.id())) {
          continue;
        }
        if (!loader.isLoaded(manifest.id())) {
          loader.load(file);
        }
      } catch (IOException | ReflectiveOperationException | RuntimeException e) {
        Log.w(TAG, "Skipping broken plugin " + file + ": " + e.getMessage());
      }
    }
  }
}
