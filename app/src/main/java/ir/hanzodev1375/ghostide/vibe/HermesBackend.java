package ir.hanzodev1375.ghostide.vibe;

import java.io.File;
import java.io.IOException;

/** Everything {@link HermesLifecycle} needs from the platform; replaced by a fake in tests. */
interface HermesBackend {

  /** Receives install progress; may be called at a high rate (the lifecycle throttles). */
  interface ProgressSink {
    void onProgress(HermesRuntime.InstallStep step, int percent);
  }

  /** True if the Debian rootfs Hermes runs in exists. */
  boolean isRootfsReady();

  /** True if a complete Hermes installation of the pinned version exists. */
  boolean isInstalled();

  /**
   * Installs Hermes into the rootfs; blocks until done. Cancelling {@code token} must stop the work
   * promptly and make this method throw.
   */
  void install(ProgressSink progress, OperationToken token) throws HermesBackendException;

  /**
   * Writes the Hermes configuration for {@code workspace} and starts the API server process; does
   * not wait for it to become healthy.
   */
  HermesServer launch(File workspace, OperationToken token) throws HermesBackendException;

  /** Deletes the Hermes installation and its state (not the rootfs). */
  void uninstall() throws IOException;
}
