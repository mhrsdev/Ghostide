package ir.hanzodev1375.ghostide.terminal;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.ResourceUtils;
import com.termux.terminal.TerminalSession;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalViewModel extends AndroidViewModel {

  private static final String LOG_TAG = "TerminalViewModel";
  private static final String ASSET_INIT_SH = "shell/init.sh";
  private static final String INIT_RUN_MARKER = "ghostide-init-run";
  private static final String[] HELPER_COMMANDS = {"weblsp"};

  /** Bit set in {@link #getModifierState()} while the sticky CTRL key is armed. */
  public static final int MODIFIER_CTRL = 1;

  /** Bit set in {@link #getModifierState()} while the sticky ALT key is armed. */
  public static final int MODIFIER_ALT = 2;

  private static final int INIT_FIRST_DELAY_MS = 2000;
  private static final int INIT_RETRY_DELAY_MS = 200;
  private static final int INIT_OVERLAY_HIDE_DELAY_MS = 3000;

  private final MutableLiveData<List<TerminalTab>> sessions = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<Integer> currentTabIndex = new MutableLiveData<>(-1);

  /** فرگمنتی که یه سشن رو نشون میده، خودش رو با tabId ثبت می‌کنه تا وقتی خروجی جدید میاد صدا زده بشه. */
  public interface ScreenRefresher {
    void onScreenUpdated();
  }

  private final Map<Integer, ScreenRefresher> screenRefreshers = new HashMap<>();

  public void registerScreenRefresher(int tabId, ScreenRefresher refresher) {
    screenRefreshers.put(tabId, refresher);
  }

  public void unregisterScreenRefresher(int tabId) {
    screenRefreshers.remove(tabId);
  }

  private TerminalSessionService service;
  private boolean isBound;
  private boolean ctrlToggled;
  private boolean altToggled;
  private final MutableLiveData<Integer> modifierState = new MutableLiveData<>(0);
  private int fontSizeSp = -1;
  private boolean autoInstallAttempted;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

  public interface SessionListener {
    void onServiceConnected();
    void onSessionAdded(int index);
    void onSessionRemoved(boolean empty);
    void onTitleChanged(int index);
    void onServiceLost();

    /** A tab that is not selected printed new output; its activity indicator should update. */
    default void onSessionActivity(int index) {}
  }

  /** وقتی init.sh تازه قراره اجرا بشه (نصب تازه) صدا زده میشه تا Activity اورلیِ لودینگ رو نشون بده. */
  private Runnable initOverlayShower;
  /** بعد از کامل شدن init.sh صدا زده میشه تا Activity اورلیِ لودینگ رو مخفی کنه. */
  private Runnable initOverlayHider;

  private SessionListener activityListener;

  private final ServiceConnection connection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
          service = ((TerminalSessionService.LocalBinder) binder).getService();
          isBound = true;
          sessions.setValue(new ArrayList<>(service.getSessions()));
          setServiceListener();
          mainHandler.post(() -> {
            if (activityListener != null) activityListener.onServiceConnected();
          });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          isBound = false;
          service = null;
          mainHandler.post(() -> {
            if (activityListener != null) activityListener.onServiceLost();
          });
        }
      };

  public TerminalViewModel(@NonNull Application application) {
    super(application);
  }

  public void setActivityListener(SessionListener listener) {
    this.activityListener = listener;
  }

  /**
   * Drops every reference to a destroyed host: the session listener (only if it is still {@code
   * listener}) and the init overlay callbacks it registered.
   */
  public void releaseActivityListener(SessionListener listener) {
    if (activityListener == listener) {
      activityListener = null;
      initOverlayShower = null;
      initOverlayHider = null;
    }
  }

  /** Sticky modifier keys armed from the extra-keys row, as {@link #MODIFIER_CTRL} bits. */
  public LiveData<Integer> getModifierState() {
    return modifierState;
  }

  /** Terminal font size chosen with pinch-zoom, or -1 while the default is still in use. */
  public int getFontSizeSp() {
    return fontSizeSp;
  }

  public void setFontSizeSp(int fontSizeSp) {
    this.fontSizeSp = fontSizeSp;
  }

  /**
   * Returns true only the first time it is called for this ViewModel, so the automatic Debian
   * install starts once per screen instead of again after every cancel, failure or onStart.
   */
  public boolean consumeAutoInstallAttempt() {
    if (autoInstallAttempted) return false;
    autoInstallAttempted = true;
    return true;
  }

  public void setInitOverlayShower(Runnable shower) {
    this.initOverlayShower = shower;
  }

  public void setInitOverlayHider(Runnable hider) {
    this.initOverlayHider = hider;
  }

  public LiveData<List<TerminalTab>> getSessions() {
    return sessions;
  }

  public LiveData<Integer> getCurrentTabIndex() {
    return currentTabIndex;
  }

  public List<TerminalTab> getSessionList() {
    List<TerminalTab> list = sessions.getValue();
    return list != null ? list : new ArrayList<>();
  }

  public TerminalSession getCurrentSession() {
    int idx = currentTabIndex.getValue() != null ? currentTabIndex.getValue() : -1;
    List<TerminalTab> list = sessions.getValue();
    if (list == null || idx < 0 || idx >= list.size()) return null;
    return list.get(idx).session;
  }

  public TerminalSession getSessionById(int id) {
    List<TerminalTab> list = sessions.getValue();
    if (list == null) return null;
    for (TerminalTab tab : list) {
      if (tab.id == id) return tab.session;
    }
    return null;
  }

  public boolean containsSessionId(int id) {
    List<TerminalTab> list = sessions.getValue();
    if (list == null) return false;
    for (TerminalTab tab : list) {
      if (tab.id == id) return true;
    }
    return false;
  }

  public boolean isCtrlToggled() {
    return ctrlToggled;
  }

  public boolean isAltToggled() {
    return altToggled;
  }

  public void consumeCtrlToggle() {
    ctrlToggled = false;
    publishModifiers();
  }

  public void consumeAltToggle() {
    altToggled = false;
    publishModifiers();
  }

  public void toggleCtrl() {
    ctrlToggled = !ctrlToggled;
    publishModifiers();
  }

  public void toggleAlt() {
    altToggled = !altToggled;
    publishModifiers();
  }

  private void publishModifiers() {
    int state = (ctrlToggled ? MODIFIER_CTRL : 0) | (altToggled ? MODIFIER_ALT : 0);
    Integer current = modifierState.getValue();
    if (current == null || current != state) modifierState.setValue(state);
  }

  public void bindService() {
    if (isBound) return;
    Intent intent = new Intent(getApplication(), TerminalSessionService.class);
    ContextCompat.startForegroundService(getApplication(), intent);
    getApplication().bindService(intent, connection, Context.BIND_AUTO_CREATE);
  }

  public void unbindService() {
    if (!isBound) return;
    isBound = false;
    if (service != null) service.clearUiListener(serviceListener);
    getApplication().unbindService(connection);
    service = null;
  }

  /**
   * Output arrives on the main thread (TerminalSession's handler), once per PTY read. The refresher
   * is called directly; the fragment coalesces the redraw to one per frame, so this path allocates
   * nothing while output streams.
   */
  private final TerminalSessionService.SessionListener serviceListener =
      new TerminalSessionService.SessionListener() {
        @Override
        public void onTextChanged(TerminalSession session) {
          int index = indexOfSession(session);
          if (index < 0) return;
          List<TerminalTab> list = sessions.getValue();
          if (list == null) return;
          TerminalTab tab = list.get(index);
          ScreenRefresher refresher = screenRefreshers.get(tab.id);
          if (refresher != null) refresher.onScreenUpdated();
          Integer current = currentTabIndex.getValue();
          if ((current == null || current != index) && !tab.hasUnseenOutput()) {
            tab.setUnseenOutput(true);
            if (activityListener != null) activityListener.onSessionActivity(index);
          }
        }

        @Override
        public void onTitleChanged(TerminalSession session) {
          int index = indexOfSession(session);
          if (index >= 0) {
            int i = index;
            mainHandler.post(() -> {
              if (activityListener != null) activityListener.onTitleChanged(i);
            });
          }
        }

        @Override
        public void onSessionFinished(TerminalSession session) {
          removeSessionFromList(session);
        }
      };

  public void setServiceListener() {
    if (service == null) return;
    service.setUiListener(serviceListener);
  }

  public void addSession(@Nullable String workingDir) {
    if (service == null) return;
    service.createSession(workingDir);
    sessions.setValue(new ArrayList<>(service.getSessions()));
    int newIndex = sessions.getValue().size() - 1;
    currentTabIndex.setValue(newIndex);
    int added = newIndex;
    mainHandler.post(() -> {
      if (activityListener != null) activityListener.onSessionAdded(added);
    });
  }

  public void addDebianSession() {
    if (service == null) return;
    boolean debianReady = DebianBootstrap.isInstalled(getApplication());
    if (debianReady) {
      service.createDebianSession();
    } else {
      // rootfs رو نداریم (هنوز نصب نشده/ناقص/حذف شده). اگه force کنیم سرویس کرش میکنه؛
      // به‌جاش شل ساده باز کن و اجازه بده کاربر از همین تب نصب رو ادامه بده.
      service.createSession(null);
    }
    sessions.setValue(new ArrayList<>(service.getSessions()));
    int newIndex = sessions.getValue().size() - 1;
    currentTabIndex.setValue(newIndex);
    if (debianReady) {
      ioExecutor.execute(this::syncShellScriptsToFilesDir);
      runInitScriptIfNeeded();
    }
    int added = newIndex;
    mainHandler.post(() -> {
      if (activityListener != null) activityListener.onSessionAdded(added);
    });
  }

  public void removeSession(int position) {
    if (service == null) return;
    List<TerminalTab> list = sessions.getValue();
    if (list == null || position < 0 || position >= list.size()) return;
    TerminalSession session = list.get(position).session;
    service.removeSession(session);
    List<TerminalTab> updated = new ArrayList<>(service.getSessions());
    sessions.setValue(updated);

    if (updated.isEmpty()) {
      currentTabIndex.setValue(-1);
      mainHandler.post(() -> {
        if (activityListener != null) activityListener.onSessionRemoved(true);
      });
    } else {
      int newIdx = Math.min(position, updated.size() - 1);
      currentTabIndex.setValue(newIdx);
      mainHandler.post(() -> {
        if (activityListener != null) activityListener.onSessionRemoved(false);
      });
    }
  }

  public void switchToTab(int position) {
    List<TerminalTab> list = sessions.getValue();
    if (list == null || position < 0 || position >= list.size()) return;
    TerminalTab tab = list.get(position);
    tab.setUnseenOutput(false);
    TerminalColorsUtil.refreshSession(tab.session);
    currentTabIndex.setValue(position);
  }

  public void writeCommandWhenReady(TerminalSession session, String command) {
    if (session == null || command == null || command.isEmpty()) return;
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (session.getEmulator() != null) {
              session.write(command + "\n");
            } else {
              mainHandler.postDelayed(this, 100);
            }
          }
        });
  }

  private int indexOfSession(TerminalSession session) {
    List<TerminalTab> list = sessions.getValue();
    if (list == null) return -1;
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).session == session) return i;
    }
    return -1;
  }

  private void removeSessionFromList(TerminalSession session) {
    List<TerminalTab> list = sessions.getValue();
    if (list == null) return;
    int index = indexOfSession(session);
    if (index < 0) return;
    list.remove(index);
    sessions.postValue(new ArrayList<>(list));
    if (list.isEmpty()) {
      currentTabIndex.postValue(-1);
      mainHandler.post(() -> {
        if (activityListener != null) activityListener.onSessionRemoved(true);
      });
    } else {
      int newIdx = Math.min(index, list.size() - 1);
      currentTabIndex.postValue(newIdx);
      mainHandler.post(() -> {
        if (activityListener != null) activityListener.onSessionRemoved(false);
      });
    }
  }

  public void syncShellScriptsToFilesDir() {
    File shellDir = new File(getApplication().getFilesDir(), "shell");
    if (!shellDir.exists() && !shellDir.mkdirs()) return;
    for (String name : HELPER_COMMANDS) {
      copyAssetToFile("shell/" + name + ".sh", new File(shellDir, name + ".sh"));
    }
  }

  public void runInitScriptIfNeeded() {
    File rootfs = DebianBootstrap.getRootfsDir(getApplication());
    if (!new File(rootfs, "bin/bash").exists()) return;
    File marker = new File(rootfs, INIT_RUN_MARKER);
    if (marker.exists()) return;

    mainHandler.post(() -> {
      if (initOverlayShower != null) initOverlayShower.run();
    });
    mainHandler.postDelayed(() -> writeInitScriptWhenReady(rootfs, marker), INIT_FIRST_DELAY_MS);
  }

  private void writeInitScriptWhenReady(File rootfs, File marker) {
    List<TerminalTab> list = sessions.getValue();
    if (list == null || list.isEmpty()) return;
    TerminalSession session = list.get(list.size() - 1).session;
    if (session.getEmulator() == null) {
      mainHandler.postDelayed(() -> writeInitScriptWhenReady(rootfs, marker), INIT_RETRY_DELAY_MS);
      return;
    }
    ioExecutor.execute(
        () -> {
          installHelperCommands(rootfs);
          String script = buildInitScript();
          if (script == null) return;
          FileUtils.createFileByDeleteOldFile(marker);
          FileIOUtils.writeFileFromString(marker, "done");
          mainHandler.post(
              () -> {
                if (indexOfSession(session) < 0) return;
                session.write(script);
                mainHandler.postDelayed(
                    () -> {
                      if (initOverlayHider != null) initOverlayHider.run();
                    },
                    INIT_OVERLAY_HIDE_DELAY_MS);
              });
        });
  }

  @Nullable
  private String buildInitScript() {
    String initScript = ResourceUtils.readAssets2String(ASSET_INIT_SH);
    if (initScript == null || initScript.isEmpty()) return null;
    String weblspScript = ResourceUtils.readAssets2String("shell/weblsp.sh");
    StringBuilder combined = new StringBuilder(applyJavaVersion(initScript));
    if (weblspScript != null && !weblspScript.isEmpty()) {
      combined.append("\n");
      combined.append(weblspScript);
    }
    combined.append("\necho '[GHOSTIDE] setup complete ✓'\n");
    return combined.toString();
  }

  private String applyJavaVersion(String script) {
    int version = new PreferencesUtils(getApplication()).getJavaRunnerVersion();
    return script.replace("__GHOSTIDE_JAVA_VERSION__", String.valueOf(version));
  }

  private void installHelperCommands(File rootfs) {
    for (String name : HELPER_COMMANDS) {
      installHelperCommand(rootfs, name);
    }
  }

  private void installHelperCommand(File rootfs, String name) {
    try {
      File binDir = new File(rootfs, "usr/local/bin");
      if (!binDir.exists() && !binDir.mkdirs()) return;
      File command = new File(binDir, name);
      String wrapper = "#!/bin/bash\nexec bash /ghostide/files/shell/" + name + ".sh \"$@\"\n";
      FileUtils.createFileByDeleteOldFile(command);
      FileIOUtils.writeFileFromString(command, wrapper);
      command.setExecutable(true, false);
    } catch (Exception e) {
      Log.w(LOG_TAG, "install helper command failed: " + name, e);
    }
  }

  private void copyAssetToFile(String assetPath, File target) {
    try {
      String content = ResourceUtils.readAssets2String(assetPath);
      if (content == null || content.isEmpty()) return;
      FileUtils.createFileByDeleteOldFile(target);
      FileIOUtils.writeFileFromString(target, content);
    } catch (Exception e) {
      Log.w(LOG_TAG, "copy asset failed: " + assetPath, e);
    }
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    mainHandler.removeCallbacksAndMessages(null);
    ioExecutor.shutdown();
    activityListener = null;
    initOverlayShower = null;
    initOverlayHider = null;
    unbindService();
  }
}