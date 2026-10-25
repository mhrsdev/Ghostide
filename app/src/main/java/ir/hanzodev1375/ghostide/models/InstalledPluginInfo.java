package ir.hanzodev1375.ghostide.models;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import java.io.File;

import ir.hanzodev1375.ghostide.plugin.PluginActivationController;
import ir.hanzodev1375.ghostide.plugin.gpl.GplManifest;

/**
 * One row in the plugin manager list: an installed {@code .gpl} file, its manifest and a snapshot
 * of its runtime state.
 *
 * @param loaded whether the plugin is activated right now
 * @param enabled the plugin's own switch, independent of the master switch
 * @param masterEnabled the global switch at the time of the snapshot
 * @param pending whether a transition for this plugin is still queued
 * @param failure the last failed transition, or {@code null}
 * @param sizeBytes size of the package file
 * @param icon decoded manifest icon, or {@code null} to show the fallback icon
 */
public record InstalledPluginInfo(
    File file,
    GplManifest manifest,
    boolean loaded,
    boolean enabled,
    boolean masterEnabled,
    boolean pending,
    @Nullable PluginActivationController.Failure failure,
    long sizeBytes,
    @Nullable Bitmap icon) {

  /** What the row tells the user about the plugin. */
  public enum State {
    ACTIVE,
    OFF,
    PAUSED,
    WAITING,
    STARTING,
    STOPPING,
    FAILED_START,
    FAILED_STOP
  }

  /** A loaded, enabled plugin with no failure; used where only the manifest is known. */
  public InstalledPluginInfo(File file, GplManifest manifest, boolean loaded) {
    this(file, manifest, loaded, true, true, false, null, file.length(), null);
  }

  /** Whether the plugin should be running according to both switches. */
  public boolean wanted() {
    return masterEnabled && enabled;
  }

  /** The single state shown for this plugin; a queued transition wins over a past failure. */
  public State state() {
    if (pending) {
      if (wanted() && !loaded) {
        return State.STARTING;
      }
      if (!wanted() && loaded) {
        return State.STOPPING;
      }
    }
    if (failure != null) {
      return failure.kind() == PluginActivationController.FailureKind.ACTIVATE
          ? State.FAILED_START
          : State.FAILED_STOP;
    }
    if (loaded) {
      return State.ACTIVE;
    }
    if (!enabled) {
      return State.OFF;
    }
    if (!masterEnabled) {
      return State.PAUSED;
    }
    return State.WAITING;
  }
}
