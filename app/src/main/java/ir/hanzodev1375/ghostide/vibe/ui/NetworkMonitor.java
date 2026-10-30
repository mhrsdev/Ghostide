package ir.hanzodev1375.ghostide.vibe.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Reports whether the device has a network that claims internet access. It relies on the
 * {@code ACCESS_NETWORK_STATE} permission the app already declares. Register in {@code onStart}
 * and unregister in {@code onStop}; the callback always runs on the main thread. The value is a
 * hint for the user: a connected network can still be unable to reach the provider.
 */
public class NetworkMonitor {

  private static final String TAG = "VibeNetwork";

  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private ConnectivityManager.NetworkCallback callback;
  private ConnectivityManager manager;

  public NetworkMonitor(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  /** Starts reporting; the current state is delivered first. */
  @MainThread
  public void start(@NonNull Consumer<Boolean> listener) {
    stop();
    ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
    if (cm == null) {
      listener.accept(true);
      return;
    }
    manager = cm;
    listener.accept(isOnline(cm));
    callback =
        new ConnectivityManager.NetworkCallback() {
          @Override
          public void onAvailable(@NonNull Network network) {
            main.post(() -> listener.accept(isOnline(cm)));
          }

          @Override
          public void onLost(@NonNull Network network) {
            main.post(() -> listener.accept(isOnline(cm)));
          }

          @Override
          public void onCapabilitiesChanged(
              @NonNull Network network, @NonNull NetworkCapabilities capabilities) {
            main.post(() -> listener.accept(isOnline(cm)));
          }
        };
    try {
      cm.registerDefaultNetworkCallback(callback);
    } catch (RuntimeException e) {
      Log.w(TAG, "network updates unavailable: " + e.getClass().getSimpleName());
      callback = null;
    }
  }

  /** Stops reporting; safe to call twice. */
  @MainThread
  public void stop() {
    ConnectivityManager current = manager;
    ConnectivityManager.NetworkCallback registered = callback;
    callback = null;
    manager = null;
    main.removeCallbacksAndMessages(null);
    if (current != null && registered != null) {
      current.unregisterNetworkCallback(registered);
    }
  }

  static boolean isOnline(@Nullable ConnectivityManager manager) {
    if (manager == null) {
      return true;
    }
    Network network = manager.getActiveNetwork();
    NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
    return capabilities != null
        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }
}
