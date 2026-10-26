package ir.hanzodev1375.components.store.notification;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Ties the {@link PluginCheckService} to the app's foreground state: the service (and its
 * notification) only stays alive while at least one activity is on screen. As soon as the last
 * activity leaves the screen the service is stopped on purpose. Fresh-plugin alerts keep working in
 * the background through the alarm scheduled by {@link PluginNotifier#schedule}.
 *
 * <p>The service is started only when the first activity becomes visible and stopped only after
 * the last one has been gone for {@link #STOP_DELAY_MS}. Moving between activities or rotating the
 * screen therefore neither restarts the service nor repeats its store check, and the foreground
 * notification is not removed and posted again.
 */
public final class PluginCheckLifecycle implements Application.ActivityLifecycleCallbacks {

  private static PluginCheckLifecycle INSTANCE;

  private static final long STOP_DELAY_MS = 1000L;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable stopTask = this::stopIfHidden;
  private Context app;
  private int visibleActivities;
  private boolean serviceRunning;

  public static void init(Context context) {
    if (INSTANCE != null) return;
    Context applicationContext = context.getApplicationContext();
    if (!(applicationContext instanceof Application)) return;
    INSTANCE = new PluginCheckLifecycle();
    INSTANCE.app = applicationContext;
    ((Application) applicationContext).registerActivityLifecycleCallbacks(INSTANCE);
  }

  private void stopIfHidden() {
    if (app == null || visibleActivities > 0 || !serviceRunning) return;
    serviceRunning = false;
    PluginCheckService.stop(app);
  }

  @Override
  public void onActivityStarted(Activity activity) {
    visibleActivities++;
    handler.removeCallbacks(stopTask);
    if (app != null && !serviceRunning) {
      serviceRunning = true;
      PluginCheckService.start(app);
    }
  }

  @Override
  public void onActivityStopped(Activity activity) {
    if (visibleActivities > 0) visibleActivities--;
    if (visibleActivities == 0) {
      handler.removeCallbacks(stopTask);
      handler.postDelayed(stopTask, STOP_DELAY_MS);
    }
  }

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

  @Override
  public void onActivityResumed(Activity activity) {}

  @Override
  public void onActivityPaused(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  @Override
  public void onActivityDestroyed(Activity activity) {}
}
