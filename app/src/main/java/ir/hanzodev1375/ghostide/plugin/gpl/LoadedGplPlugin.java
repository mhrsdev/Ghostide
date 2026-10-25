package ir.hanzodev1375.ghostide.plugin.gpl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginDescriptor;

/** Everything the loader needs to keep track of and later unload one activated plugin. */
public final class LoadedGplPlugin {

  private final PluginDescriptor descriptor;
  private final GhostPlugin plugin;
  private final DefaultPluginContext context;

  LoadedGplPlugin(PluginDescriptor descriptor, GhostPlugin plugin, DefaultPluginContext context) {
    this.descriptor = descriptor;
    this.plugin = plugin;
    this.context = context;
  }

  public PluginDescriptor getDescriptor() {
    return descriptor;
  }

  public GhostPlugin getPlugin() {
    return plugin;
  }

  /**
   * Calls the plugin's {@code deactivate()} on a short-lived worker thread and waits at most
   * {@code timeoutMs}, so a plugin that throws or hangs cannot block or crash the caller.
   *
   * @return {@code null} when {@code deactivate()} returned in time, otherwise what it threw or a
   *     {@link TimeoutException}
   */
  Throwable deactivate(long timeoutMs) {
    FutureTask<Void> task =
        new FutureTask<>(
            () -> {
              plugin.deactivate();
              return null;
            });
    Thread worker = new Thread(task, "gpl-deactivate-" + descriptor.getId());
    worker.setDaemon(true);
    worker.start();
    try {
      task.get(timeoutMs, TimeUnit.MILLISECONDS);
      return null;
    } catch (ExecutionException e) {
      return e.getCause() != null ? e.getCause() : e;
    } catch (TimeoutException e) {
      worker.interrupt();
      return new TimeoutException(
          "deactivate() of " + descriptor.getId() + " did not return within " + timeoutMs + " ms");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return e;
    }
  }

  /**
   * Disposes every {@code Disposable} the plugin registered.
   *
   * @return the first failure, or {@code null}
   */
  Throwable dispose() {
    return context.disposeAll();
  }
}
