package ir.hanzodev1375.ghostide.plugin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.components.childern.BackgroundChildRegistry;
import ir.hanzodev1375.ghostide.plugin.gpl.GplInstalledPlugins;
import ir.hanzodev1375.ghostide.plugin.gpl.GplManifest;
import ir.hanzodev1375.ghostide.plugin.gpl.GplManifestReader;
import ir.hanzodev1375.ghostide.plugin.gpl.GplPluginLoader;
import ir.hanzodev1375.ghostide.plugin.gpl.PluginDisabledException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies the choices stored in {@link PluginAvailability} to the running plugins.
 *
 * <p>Every transition (master switch, per-plugin switch, retry, install, uninstall) runs on one
 * serial worker, so two transitions for the same plugin can never overlap. A transition never
 * trusts what the caller saw: it re-reads the persisted intent and the real loaded state when it
 * runs and only does what is still needed, which makes repeated taps and replays harmless.
 *
 * <p>Failures are kept per plugin until the next successful transition or {@link
 * #retry(String)}, so the UI can show them; listeners are always called on the main thread.
 */
public final class PluginActivationController {

  private static final String TAG = "PluginActivation";

  /** What the controller needs from the plugin runtime; implemented by {@code GplPluginLoader}. */
  public interface Runtime {

    /** Installed plugin packages with a readable manifest. May do I/O; called off the main thread. */
    List<InstalledPlugin> scanInstalled();

    List<String> loadedIds();

    boolean isLoaded(String pluginId);

    void load(File gplFile) throws IOException, ReflectiveOperationException;

    /**
     * @return {@code null} on a clean stop, otherwise the failure; the plugin's registrations must
     *     be released either way
     */
    @Nullable
    Throwable unload(String pluginId);

    /** Called after a plugin was unloaded, on the worker thread. */
    default void onUnloaded(String pluginId) {}

    /** The installed package of {@code pluginId}, or {@code null}. May do I/O. */
    @Nullable
    default File findInstalled(String pluginId) {
      for (InstalledPlugin plugin : scanInstalled()) {
        if (plugin.id().equals(pluginId)) {
          return plugin.file();
        }
      }
      return null;
    }
  }

  /** One installed package: its manifest id and file. */
  public record InstalledPlugin(String id, File file) {}

  /** Which direction of a transition failed. */
  public enum FailureKind {
    ACTIVATE,
    SUSPEND
  }

  /** Last failed transition of one plugin. */
  public record Failure(FailureKind kind, String message) {}

  /** Notified on the main thread whenever a plugin's state may have changed. */
  public interface Listener {
    void onPluginStatesChanged();
  }

  /** Places a picked or downloaded package into the install dir; runs on the worker thread. */
  public interface Installer {
    @NonNull
    PlacedPlugin place() throws IOException;
  }

  /**
   * Result of {@link Installer#place()}.
   *
   * @param fresh {@code true} when no package with this id was installed before
   */
  public record PlacedPlugin(String id, String name, File file, boolean fresh) {}

  /** Outcome of {@link #install}, delivered on the main thread. */
  public interface InstallCallback {
    void onInstalled(PlacedPlugin plugin, boolean active, @Nullable Failure failure);

    void onInstallFailed(String message);
  }

  /** Outcome of {@link #uninstall}, delivered on the main thread. */
  public interface UninstallCallback {
    void onUninstalled(boolean deleted, @Nullable Failure suspendFailure);
  }

  private static volatile PluginActivationController instance;

  private final PluginAvailability availability;
  private final Runtime runtime;
  private final Executor worker;
  private final Executor main;
  private final Map<String, Failure> failures = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> pending = new ConcurrentHashMap<>();
  private final AtomicInteger masterPending = new AtomicInteger();
  private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

  PluginActivationController(
      PluginAvailability availability, Runtime runtime, Executor worker, Executor main) {
    this.availability = availability;
    this.runtime = runtime;
    this.worker = worker;
    this.main = main;
  }

  /** The process-wide controller backed by {@link GplPluginLoader}. */
  public static PluginActivationController get(@NonNull Context context) {
    PluginActivationController local = instance;
    if (local == null) {
      synchronized (PluginActivationController.class) {
        local = instance;
        if (local == null) {
          Context app = context.getApplicationContext();
          Handler handler = new Handler(Looper.getMainLooper());
          local =
              new PluginActivationController(
                  PluginAvailability.get(app),
                  new LoaderRuntime(app, GplPluginLoader.getInstance(app), handler),
                  Executors.newSingleThreadExecutor(
                      runnable -> {
                        Thread thread = new Thread(runnable, "ghost-plugin-activation");
                        thread.setDaemon(true);
                        return thread;
                      }),
                  handler::post);
          instance = local;
        }
      }
    }
    return local;
  }

  public PluginAvailability availability() {
    return availability;
  }

  public void addListener(@NonNull Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(@NonNull Listener listener) {
    listeners.remove(listener);
  }

  /** The last failure of {@code pluginId}, or {@code null}. */
  @Nullable
  public Failure failureOf(@NonNull String pluginId) {
    return failures.get(pluginId);
  }

  /** Whether a transition for {@code pluginId}, or a master transition, is still queued. */
  public boolean isPending(@NonNull String pluginId) {
    AtomicInteger count = pending.get(pluginId);
    return masterPending.get() > 0 || (count != null && count.get() > 0);
  }

  public boolean isMasterPending() {
    return masterPending.get() > 0;
  }

  /**
   * Persists the master switch and loads or unloads plugins to match. Turning it on loads only the
   * plugins whose own switch is on; turning it off unloads every loaded plugin.
   */
  public void setMasterEnabled(boolean enabled) {
    availability.setMasterEnabled(enabled);
    masterPending.incrementAndGet();
    notifyListeners();
    worker.execute(
        () -> {
          try {
            reconcileAllNow();
          } finally {
            masterPending.decrementAndGet();
            notifyListeners();
          }
        });
  }

  /** Persists the plugin's own switch and loads or unloads just that plugin to match. */
  public void setPluginEnabled(@NonNull String pluginId, boolean enabled) {
    availability.setPluginEnabled(pluginId, enabled);
    reconcileAsync(pluginId);
  }

  /** Clears the plugin's failure and applies its current intent again. */
  public void retry(@NonNull String pluginId) {
    failures.remove(pluginId);
    reconcileAsync(pluginId);
  }

  /**
   * Brings every installed plugin in line with its intent: loads enabled plugins that are not
   * loaded yet (including ones that failed before) and unloads the ones that are turned off.
   */
  public void reconcileAll() {
    worker.execute(
        () -> {
          reconcileAllNow();
          notifyListeners();
        });
  }

  /**
   * Places a new package with {@code installer}, records a fresh install as enabled and activates
   * it only when the master switch allows it.
   */
  public void install(@NonNull Installer installer, @NonNull InstallCallback callback) {
    worker.execute(
        () -> {
          PlacedPlugin placed;
          try {
            placed = installer.place();
          } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Install failed", e);
            String message = describe(e);
            main.execute(() -> callback.onInstallFailed(message));
            return;
          }
          if (placed.fresh()) {
            availability.setPluginEnabled(placed.id(), true);
          }
          failures.remove(placed.id());
          reconcileNow(placed.id(), placed.file());
          boolean active = runtime.isLoaded(placed.id());
          Failure failure = failures.get(placed.id());
          main.execute(() -> callback.onInstalled(placed, active, failure));
          notifyListeners();
        });
  }

  /** Stops the plugin, deletes its package and forgets its per-plugin choice. */
  public void uninstall(
      @NonNull String pluginId, @NonNull File file, @NonNull UninstallCallback callback) {
    beginPending(pluginId);
    worker.execute(
        () -> {
          Failure suspendFailure = null;
          try {
            if (runtime.isLoaded(pluginId)) {
              Throwable failure = runtime.unload(pluginId);
              runtime.onUnloaded(pluginId);
              if (failure != null) {
                Log.w(TAG, "Plugin " + pluginId + " did not stop cleanly before uninstall", failure);
                suspendFailure = new Failure(FailureKind.SUSPEND, describe(failure));
              }
            }
            boolean deleted = !file.exists() || file.delete();
            if (deleted) {
              availability.forgetPlugin(pluginId);
              failures.remove(pluginId);
            }
            Failure reported = suspendFailure;
            main.execute(() -> callback.onUninstalled(deleted, reported));
          } finally {
            endPending(pluginId);
            notifyListeners();
          }
        });
  }

  private void reconcileAsync(String pluginId) {
    beginPending(pluginId);
    notifyListeners();
    worker.execute(
        () -> {
          try {
            reconcileNow(pluginId, runtime.findInstalled(pluginId));
          } finally {
            endPending(pluginId);
            notifyListeners();
          }
        });
  }

  private void reconcileAllNow() {
    Map<String, File> targets = new LinkedHashMap<>();
    for (InstalledPlugin plugin : runtime.scanInstalled()) {
      targets.put(plugin.id(), plugin.file());
    }
    for (String loadedId : runtime.loadedIds()) {
      targets.putIfAbsent(loadedId, null);
    }
    for (Map.Entry<String, File> target : targets.entrySet()) {
      reconcileNow(target.getKey(), target.getValue());
    }
  }

  private void reconcileNow(String pluginId, @Nullable File file) {
    boolean wanted = availability.isEffectivelyEnabled(pluginId);
    boolean loaded = runtime.isLoaded(pluginId);
    if (wanted && !loaded) {
      if (file == null) {
        return;
      }
      try {
        runtime.load(file);
        failures.remove(pluginId);
      } catch (PluginDisabledException e) {
        failures.remove(pluginId);
      } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError e) {
        Log.e(TAG, "Could not activate plugin " + pluginId, e);
        failures.put(pluginId, new Failure(FailureKind.ACTIVATE, describe(e)));
      }
    } else if (!wanted && loaded) {
      Throwable failure = runtime.unload(pluginId);
      runtime.onUnloaded(pluginId);
      if (failure != null) {
        Log.w(TAG, "Plugin " + pluginId + " did not stop cleanly", failure);
        failures.put(pluginId, new Failure(FailureKind.SUSPEND, describe(failure)));
      } else {
        failures.remove(pluginId);
      }
    } else if (wanted) {
      failures.remove(pluginId);
    } else {
      Failure previous = failures.get(pluginId);
      if (previous != null && previous.kind() == FailureKind.ACTIVATE) {
        failures.remove(pluginId);
      }
    }
  }

  private void beginPending(String pluginId) {
    pending.computeIfAbsent(pluginId, id -> new AtomicInteger()).incrementAndGet();
  }

  private void endPending(String pluginId) {
    AtomicInteger count = pending.get(pluginId);
    if (count != null && count.decrementAndGet() <= 0) {
      pending.remove(pluginId, count);
    }
  }

  private void notifyListeners() {
    if (listeners.isEmpty()) {
      return;
    }
    main.execute(
        () -> {
          for (Listener listener : listeners) {
            listener.onPluginStatesChanged();
          }
        });
  }

  private static String describe(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.trim().isEmpty()) {
      return error.getClass().getSimpleName();
    }
    return message;
  }

  /** {@link Runtime} over the real {@link GplPluginLoader}. */
  private static final class LoaderRuntime implements Runtime {

    private final Context appContext;
    private final GplPluginLoader loader;
    private final Handler mainHandler;

    LoaderRuntime(Context appContext, GplPluginLoader loader, Handler mainHandler) {
      this.appContext = appContext;
      this.loader = loader;
      this.mainHandler = mainHandler;
    }

    @Override
    public List<InstalledPlugin> scanInstalled() {
      List<InstalledPlugin> plugins = new ArrayList<>();
      for (File file : GplInstalledPlugins.listInstalled(appContext)) {
        GplManifest manifest = GplManifestReader.read(file);
        if (manifest != null) {
          plugins.add(new InstalledPlugin(manifest.id(), file));
        }
      }
      return plugins;
    }

    @Override
    @Nullable
    public File findInstalled(String pluginId) {
      File conventional =
          new File(
              GplInstalledPlugins.installDir(appContext),
              GplInstalledPlugins.packageFileName(pluginId));
      if (conventional.isFile()) {
        GplManifest manifest = GplManifestReader.read(conventional);
        if (manifest != null && manifest.id().equals(pluginId)) {
          return conventional;
        }
      }
      return Runtime.super.findInstalled(pluginId);
    }

    @Override
    public List<String> loadedIds() {
      return loader.getLoadedIds();
    }

    @Override
    public boolean isLoaded(String pluginId) {
      return loader.isLoaded(pluginId);
    }

    @Override
    public void load(File gplFile) throws IOException, ReflectiveOperationException {
      loader.load(gplFile);
    }

    @Override
    @Nullable
    public Throwable unload(String pluginId) {
      return loader.unloadSafely(pluginId);
    }

    @Override
    public void onUnloaded(String pluginId) {
      if (pluginId.equals(BackgroundPluginManager.selected(appContext))) {
        mainHandler.post(BackgroundChildRegistry::bumpGeneration);
      }
    }
  }
}
