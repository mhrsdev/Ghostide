package ir.hanzodev1375.ghostide.terminal.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.termux.terminal.TerminalSession;
import ir.hanzodev1375.components.glsl.GLSLShaderView;
import ir.hanzodev1375.components.glsl.GlslPalette;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.LiquidGlassDialogBuilderJava;
import ir.hanzodev1375.components.utils.ComponentsPrefs;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.activity.BaseCompat;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import ir.hanzodev1375.ghostide.databinding.ActivityTerminalBinding;
import ir.hanzodev1375.ghostide.terminal.DebianBootstrap;
import ir.hanzodev1375.ghostide.terminal.DebianInstaller;
import ir.hanzodev1375.ghostide.terminal.InstallProgressStyle;
import ir.hanzodev1375.ghostide.terminal.TerminalBackupManager;
import ir.hanzodev1375.ghostide.terminal.TerminalInputDock;
import ir.hanzodev1375.ghostide.terminal.TerminalSessionFragment;
import ir.hanzodev1375.ghostide.terminal.TerminalTab;
import ir.hanzodev1375.ghostide.terminal.TerminalViewModel;
import ir.hanzodev1375.ghostide.utils.ObjectUtil;
import ir.hanzodev1375.ghostide.utils.StorageUtils;
import ir.theme.GhostTheme;
import ir.theme.M3Theme;
import ir.theme.ThemeManager;
import ir.theme.ThemeUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TerminalActivity extends BaseCompat implements TerminalViewModel.SessionListener {

  public static final String EXTRA_WORKING_DIR = "working_dir";
  public static final String EXTRA_COMMAND = "command";
  private static final String LOG_TAG = "TerminalActivity";
  private static final float INACTIVE_TAB_ALPHA = 0.68f;
  private static final float IDLE_DOT_ALPHA = 0.45f;
  private static final float KEY_RIPPLE_ALPHA = 0.18f;
  private static final float REVEAL_OFFSET_DP = 6f;
  private static final int COMMAND_INPUT_MAX_LINES = 4;

  private ActivityTerminalBinding b;
  private TerminalViewModel viewModel;
  private TerminalInputDock inputDock;
  private SessionPagerAdapter pagerAdapter;
  private MaterialButton[] extraKeys;
  private boolean revealPending;

  private View initOverlay;
  private CircularProgressIndicator initOverlayProgress;
  private DebianInstaller.InstallListener installListener;

  private AlertDialog transferDialog;
  private TextView transferStatusText;
  private TextView transferPathText;
  private ProgressBar transferProgressBar;
  private boolean transferIsBackup;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    b = ActivityTerminalBinding.inflate(getLayoutInflater());
    setContentView(b.getRoot());
    M3Theme.apply(b.getRoot());
    viewModel = new ViewModelProvider(this).get(TerminalViewModel.class);
    viewModel.setActivityListener(this);
    revealPending = savedInstanceState == null;
    setupToolbar();
    setupEdgeToEdgeInsets();
    setupViewPager();
    setupExtraKeys();
    applyJsonTheme();
    setupInputDock();
    setupInstallBanner();
    setupBackHandler();
    setupBackgroundBlur();
    setupInitOverlay();
    maybeRequestNotificationPermission();
  }

  @Override
  protected void onDestroy() {
    viewModel.releaseActivityListener(this);
    if (transferDialog != null && transferDialog.isShowing()) transferDialog.dismiss();
    if (initOverlay != null) initOverlay.animate().cancel();
    super.onDestroy();
  }

  @Override
  protected void onStart() {
    super.onStart();
    viewModel.bindService();
    viewModel.setServiceListener();
    maybeStartInstall();
  }

  @Override
  protected void onStop() {
    viewModel.unbindService();
    DebianInstaller.detach(installListener);
    super.onStop();
  }

  /**
   * Attaches to a running install, or starts the automatic install once per screen. A cancelled or
   * failed install is not restarted on every onStart; the banner and the overflow menu offer it.
   */
  private void maybeStartInstall() {
    if (DebianInstaller.isInstalling()) {
      attachToRunningInstall();
      return;
    }
    if (DebianBootstrap.isInstalled(this)) return;
    if (viewModel.consumeAutoInstallAttempt()) startDebianInstall();
  }

  private void maybeRequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this, new String[] {Manifest.permission.POST_NOTIFICATIONS}, 4821);
      }
    }
  }

  // ─── ViewPager + TabLayout ───────────────────────────────────────────

  private void setupViewPager() {
    pagerAdapter = new SessionPagerAdapter(this);
    b.viewPager.setAdapter(pagerAdapter);
    b.viewPager.setOffscreenPageLimit(4);
    b.viewPager.setUserInputEnabled(false);
    TabLayoutMediator mediator =
        new TabLayoutMediator(b.tabLayout, b.viewPager, true, this::bindTab);
    mediator.attach();

    b.viewPager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            viewModel.switchToTab(position);
          }
        });

    b.tabLayout.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            updateTabView(tab, tab.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {
            updateTabView(tab, tab.getPosition());
          }

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            showTabMenu(tab.view, tab.getPosition());
          }
        });

    b.btnNewSession.setOnClickListener(this::showNewSessionMenu);
  }

  private void bindTab(TabLayout.Tab tab, int position) {
    tab.setCustomView(R.layout.item_terminal_session_tab);
    tab.view.setOnLongClickListener(
        v -> {
          confirmCloseTab(position);
          return true;
        });
    updateTabView(tab, position);
  }

  /**
   * Renders one session tab: the title, and a status dot that is primary for the selected tab,
   * tertiary for a background tab with unseen output, error once the shell exited, and a dim
   * outline otherwise.
   */
  private void updateTabView(@Nullable TabLayout.Tab tab, int position) {
    List<TerminalTab> tabs = viewModel.getSessionList();
    if (tab == null || tab.getCustomView() == null || position < 0 || position >= tabs.size()) {
      return;
    }
    TerminalTab model = tabs.get(position);
    View custom = tab.getCustomView();
    TextView title = custom.findViewById(R.id.tabTitle);
    View dot = custom.findViewById(R.id.tabStatusDot);
    boolean selected = tab.isSelected();
    String text = model.getDisplayTitle();
    title.setText(text);
    tab.setContentDescription(text);
    int onSurface = fallback(M3Theme.onSurface(), Color.WHITE);
    title.setTextColor(onSurface);
    title.setAlpha(selected ? 1f : INACTIVE_TAB_ALPHA);
    int dotColor;
    float dotAlpha = 1f;
    if (!model.session.isRunning()) {
      dotColor = fallback(M3Theme.error(), onSurface);
    } else if (selected) {
      dotColor = fallback(M3Theme.primary(), onSurface);
    } else if (model.hasUnseenOutput()) {
      dotColor = fallback(M3Theme.tertiary(), fallback(M3Theme.primary(), onSurface));
    } else {
      dotColor = fallback(M3Theme.outline(), onSurface);
      dotAlpha = IDLE_DOT_ALPHA;
    }
    Drawable dotBackground = dot.getBackground();
    if (dotBackground != null) DrawableCompat.setTint(dotBackground.mutate(), dotColor);
    dot.setAlpha(dotAlpha);
  }

  private void refreshTabs() {
    pagerAdapter.notifyDataSetChanged();
    for (int i = 0; i < viewModel.getSessionList().size(); i++) {
      updateTabView(b.tabLayout.getTabAt(i), i);
    }
    updateToolbarSubtitle();
  }

  private void updateToolbarSubtitle() {
    int count = viewModel.getSessionList().size();
    if (getSupportActionBar() == null) return;
    if (count <= 0) {
      getSupportActionBar().setSubtitle(null);
    } else if (count == 1) {
      getSupportActionBar().setSubtitle(R.string.terminal_session_count_one);
    } else {
      getSupportActionBar().setSubtitle(getString(R.string.terminal_session_count_many, count));
    }
  }

  private void confirmCloseTab(int position) {
    List<TerminalTab> tabs = viewModel.getSessionList();
    if (position < 0 || position >= tabs.size()) return;
    new DialogCompat(this)
        .setTitle(getString(R.string.terminal_close_tab))
        .setMessage(getString(R.string.terminal_close_tab_confirm))
        .setPositiveButton(
            getString(R.string.terminal_action_close),
            (dialog, which) -> viewModel.removeSession(position))
        .setNegativeButton(getString(R.string.terminal_action_cancel), null)
        .show();
  }

  private void showTabMenu(View anchor, int position) {
    List<String> items =
        Arrays.asList(
            getString(R.string.close),
            getString(R.string.closeother),
            getString(R.string.closeall));
    ObjectUtil.showGlassMenu(
        this,
        anchor,
        items,
        (index, title) -> {
          switch (index) {
            case 0 -> closeTab(position);
            case 1 -> closeOtherTabs(position);
            case 2 -> closeAllTabs();
          }
        });
  }

  private void closeTab(int position) {
    viewModel.removeSession(position);
  }

  private void closeOtherTabs(int keep) {
    for (int i = viewModel.getSessionList().size() - 1; i >= 0; i--) {
      if (i != keep) viewModel.removeSession(i);
    }
  }

  private void closeAllTabs() {
    for (int i = viewModel.getSessionList().size() - 1; i >= 0; i--) {
      viewModel.removeSession(i);
    }
  }

  // ─── SessionListener ─────────────────────────────────────────────────

  @Override
  public void onServiceConnected() {
    if (revealPending) {
      revealPending = false;
      animateTerminalReveal();
    }
    boolean runRequested = hasCommandExtra();
    if (viewModel.getSessionList().isEmpty() || runRequested) {
      // اگه سشنی موجود نیست، یا این اکتیویتی برای اجرای کد (EXTRA_COMMAND) اومده، همیشه یه
      // تبِ تازه باز کن — نه این که تبِ قبلی رو دوباره لود کنی. دستورِ کد رو هم
      // consumeCommandExtra() (بعد از onSessionAdded) روی همین تبِ تازه می‌نویسه.
      if (DebianBootstrap.isInstalled(this)) {
        addNewDebianSession();
      } else {
        addNewShellSession();
      }
    } else {
      int idx =
          viewModel.getCurrentTabIndex().getValue() != null
              ? viewModel.getCurrentTabIndex().getValue()
              : 0;
      viewModel.switchToTab(idx);
      refreshTabs();
      b.viewPager.setCurrentItem(idx, false);
    }
  }

  /** وقتی اکتیویتی دوباره به سرویس وصل می‌شه، ترمینال و تب‌ها با فیِد + اسلاید ظاهر می‌شن. */
  private void animateTerminalReveal() {
    boolean animate = getAnimationManager() != null && getAnimationManager().areAnimationsEnabled();
    animateIn(b.viewPager, animate);
    animateIn(b.tabLayout, animate);
  }

  private void animateIn(View v, boolean animate) {
    if (v == null) return;
    if (!animate) {
      v.setAlpha(1f);
      v.setTranslationY(0f);
      return;
    }
    v.setAlpha(0f);
    v.setTranslationY(REVEAL_OFFSET_DP * getResources().getDisplayMetrics().density);
    v.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(300)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }

  @Override
  public void onSessionAdded(int index) {
    refreshTabs();
    b.viewPager.setCurrentItem(index, true);
    consumeCommandExtra();
  }

  @Override
  public void onSessionRemoved(boolean empty) {
    if (empty) {
      finish();
      return;
    }
    refreshTabs();
  }

  @Override
  public void onTitleChanged(int index) {
    updateTabView(b.tabLayout.getTabAt(index), index);
  }

  @Override
  public void onSessionActivity(int index) {
    updateTabView(b.tabLayout.getTabAt(index), index);
  }

  @Override
  public void onServiceLost() {
    GhostToast.makeText(this, getString(R.string.terminal_service_lost), GhostToast.LENGTH_SHORT)
        .show();
    finish();
  }

  private void consumeCommandExtra() {
    String command = getIntent().getStringExtra(EXTRA_COMMAND);
    if (command == null || command.isEmpty()) return;
    getIntent().removeExtra(EXTRA_COMMAND);
    TerminalSession session = viewModel.getCurrentSession();
    if (session != null) viewModel.writeCommandWhenReady(session, command);
  }

  private boolean hasCommandExtra() {
    String command = getIntent().getStringExtra(EXTRA_COMMAND);
    return command != null && !command.isEmpty();
  }

  // ─── InputDock ───────────────────────────────────────────────────────

  private void setupInputDock() {
    inputDock =
        new TerminalInputDock(
            b.inputDock,
            b.dockPages,
            b.extraKeysScroll,
            b.commandInputRow,
            b.commandInput,
            b.commandInputLayout,
            b.dragHandle,
            b.handleChevron,
            this::currentSession);
    inputDock.attach();
    b.commandInput.setHorizontallyScrolling(false);
    b.commandInput.setMaxLines(COMMAND_INPUT_MAX_LINES);
  }

  public TerminalInputDock getInputDock() {
    return inputDock;
  }

  @Nullable
  private TerminalSession currentSession() {
    return viewModel.getCurrentSession();
  }

  @Nullable
  private TerminalSessionFragment getCurrentFragment() {
    int position = b.viewPager.getCurrentItem();
    List<TerminalTab> tabs = viewModel.getSessionList();
    if (position < 0 || position >= tabs.size()) return null;
    long itemId = tabs.get(position).id;
    String tag = "f" + itemId;
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
    return fragment instanceof TerminalSessionFragment ? (TerminalSessionFragment) fragment : null;
  }

  // ─── Edge to edge ────────────────────────────────────────────────────

  /**
   * The window is edge-to-edge: the toolbar pads under the status bar, the dock pads above the
   * navigation bar or the keyboard (whichever is taller), side insets keep landscape content out of
   * cutouts, and the IME visibility from the same insets drives the dock.
   */
  private void setupEdgeToEdgeInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(
        b.coordinator,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
          b.mainContent.setPadding(bars.left, 0, bars.right, 0);
          b.toolbar.setPadding(
              b.toolbar.getPaddingLeft(),
              bars.top,
              b.toolbar.getPaddingRight(),
              b.toolbar.getPaddingBottom());
          b.inputDock.setPadding(
              b.inputDock.getPaddingLeft(),
              b.inputDock.getPaddingTop(),
              b.inputDock.getPaddingRight(),
              Math.max(bars.bottom, ime.bottom));
          if (inputDock != null) {
            inputDock.setKeyboardVisible(insets.isVisible(WindowInsetsCompat.Type.ime()));
          }
          return insets;
        });
  }

  // ─── Toolbar ─────────────────────────────────────────────────────────

  private void setupToolbar() {
    setSupportActionBar(b.toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.terminal_title);
    }
    b.toolbar.setNavigationOnClickListener(v -> finish());
    b.btnMoreMenu.setOnClickListener(this::showMoreMenu);
    styleToolbarChrome();
  }

  private void styleToolbarChrome() {
    Integer onSurface = M3Theme.onSurface();
    if (onSurface == null) return;
    b.toolbar.setTitleTextColor(onSurface);
    b.toolbar.setSubtitleTextColor(onSurface);
    b.toolbar.setNavigationIconTint(onSurface);
    tintIcon(b.btnMoreMenu, onSurface);
    tintIcon(b.btnNewSession, onSurface);
  }

  private void tintIcon(ImageButton button, int color) {
    Drawable drawable = button.getDrawable();
    if (drawable == null) return;
    DrawableCompat.setTint(drawable.mutate(), color);
  }

  private void showMoreMenu(View anchor) {
    List<String> items = new ArrayList<>();
    items.add(
        getString(
            DebianBootstrap.isInstalled(this)
                ? R.string.terminal_remove_debian
                : R.string.terminal_install_debian));
    items.add(getString(R.string.terminal_backup_menu));
    items.add(getString(R.string.terminal_restore_menu));
    ObjectUtil.showGlassMenu(
        this,
        anchor,
        items,
        (index, title) -> {
          switch (index) {
            case 0 -> {
              if (DebianBootstrap.isInstalled(this)) {
                confirmAndRemoveDebian();
              } else {
                startDebianInstall();
              }
            }
            case 1 -> startTerminalBackup();
            case 2 -> showRestorePicker();
            default -> {
            }
          }
        });
  }

  private void showNewSessionMenu(View anchor) {
    if (!DebianBootstrap.isInstalled(this)) {
      GhostToast.makeText(
              this,
              getString(
                  R.string.terminal_debian_rootfs_not_found_fallback,
                  DebianBootstrap.getRootfsDir(this).getAbsolutePath()),
              GhostToast.LENGTH_LONG)
          .show();
      viewModel.addSession(null);
      return;
    }
    List<String> items =
        Arrays.asList(
            getString(R.string.terminal_session_shell), getString(R.string.terminal_session_debian));
    ObjectUtil.showGlassMenu(
        this,
        anchor,
        items,
        (index, title) -> {
          if (index == 1) {
            addNewDebianSession();
          } else {
            String workingDir = getIntent().getStringExtra(EXTRA_WORKING_DIR);
            viewModel.addSession(workingDir);
          }
        });
  }

  // ─── Extra keys ──────────────────────────────────────────────────────

  private void setupExtraKeys() {
    extraKeys =
        new MaterialButton[] {
          b.keyEsc, b.keyTab, b.keyCtrl, b.keyAlt, b.keyUp, b.keyDown, b.keyLeft, b.keyRight,
          b.keyHome, b.keyEnd, b.keySlash, b.keyDash, b.keyPipe, b.keyTilde, b.keyPaste
        };
    styleExtraKeys();

    b.keyEsc.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_ESCAPE));
    b.keyTab.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_TAB));
    b.keyUp.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP));
    b.keyDown.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN));
    b.keyLeft.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT));
    b.keyRight.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT));
    b.keyHome.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_MOVE_HOME));
    b.keyEnd.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_MOVE_END));
    b.keySlash.setOnClickListener(v -> typeText("/"));
    b.keyDash.setOnClickListener(v -> typeText("-"));
    b.keyPipe.setOnClickListener(v -> typeText("|"));
    b.keyTilde.setOnClickListener(v -> typeText("~"));
    b.keyPaste.setOnClickListener(v -> pasteIntoSession());
    b.keyCtrl.setContentDescription(getString(R.string.terminal_key_ctrl_sticky));
    b.keyAlt.setContentDescription(getString(R.string.terminal_key_alt_sticky));
    b.keyCtrl.setOnClickListener(v -> viewModel.toggleCtrl());
    b.keyAlt.setOnClickListener(v -> viewModel.toggleAlt());
    viewModel.getModifierState().observe(this, this::renderModifiers);
  }

  /**
   * The sticky CTRL/ALT state lives in the ViewModel and is cleared there when the next key
   * consumes it, so the checked state of the keys always mirrors what the next key press will do.
   */
  private void renderModifiers(@Nullable Integer state) {
    int bits = state != null ? state : 0;
    b.keyCtrl.setChecked((bits & TerminalViewModel.MODIFIER_CTRL) != 0);
    b.keyAlt.setChecked((bits & TerminalViewModel.MODIFIER_ALT) != 0);
  }

  private void styleExtraKeys() {
    if (extraKeys == null) return;
    int onSurface = fallback(M3Theme.onSurface(), Color.WHITE);
    int keyBackground =
        fallback(M3Theme.surfaceContainerHighest(), fallback(M3Theme.surfaceVariant(), 0));
    int checkedBackground = fallback(M3Theme.primary(), onSurface);
    int checkedText = fallback(M3Theme.onPrimary(), keyBackground);
    int[][] states = {
      new int[] {android.R.attr.state_checked},
      new int[] {-android.R.attr.state_enabled},
      new int[] {}
    };
    ColorStateList background =
        new ColorStateList(
            states,
            new int[] {
              checkedBackground, ColorUtils.setAlphaComponent(keyBackground, 0x61), keyBackground
            });
    ColorStateList text =
        new ColorStateList(
            states,
            new int[] {checkedText, ColorUtils.setAlphaComponent(onSurface, 0x61), onSurface});
    ColorStateList ripple =
        ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(onSurface, Math.round(255 * KEY_RIPPLE_ALPHA)));
    for (MaterialButton key : extraKeys) {
      key.setBackgroundTintList(background);
      key.setTextColor(text);
      key.setIconTint(text);
      key.setRippleColor(ripple);
      key.setStrokeWidth(0);
    }
  }

  private void sendKeyEvent(int keyCode) {
    TerminalSessionFragment fragment = getCurrentFragment();
    if (fragment != null) fragment.sendKeyEvent(keyCode);
  }

  private void pasteIntoSession() {
    TerminalSession session = currentSession();
    if (session != null) session.onPasteTextFromClipboard();
  }

  private void typeText(String text) {
    TerminalSession session = currentSession();
    if (session == null) return;
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    session.write(bytes, 0, bytes.length);
  }

  // ─── Sessions ────────────────────────────────────────────────────────

  private void addNewDebianSession() {
    viewModel.addDebianSession();
  }

  private void addNewShellSession() {
    String workingDir = getIntent().getStringExtra(EXTRA_WORKING_DIR);
    viewModel.addSession(workingDir);
  }

  // ─── Theme ───────────────────────────────────────────────────────────

  private void setupBackgroundBlur() {
    b.inputDock.setElevation(0f);
    setupBackgroundBlur(b.backgroundIconTerminal, b.toolbar, b.tabsBar, b.inputDock);
  }

  /**
   * Chrome (toolbar, tabs, dock) is one tonal surface. The terminal output has no fill of its own:
   * it is drawn straight over the root, which shows the wallpaper when one is enabled and the theme
   * surface (coordinator and the theme's activity background) otherwise.
   */
  private void applyJsonTheme() {
    boolean hasBackgroundImage = isBackgroundImageEnabled();

    if (hasBackgroundImage) {
      b.coordinator.setBackgroundColor(Color.TRANSPARENT);
    } else {
      Integer chrome = M3Theme.surfaceContainer();
      if (chrome == null) chrome = M3Theme.surface();
      if (chrome != null) {
        b.coordinator.setBackgroundColor(chrome);
        applyViewColor(b.toolbar, chrome);
        applyViewColor(b.tabsBar, chrome);
        applyViewColor(b.inputDock, chrome);
      }
    }

    Integer onSurface = M3Theme.onSurface();
    Integer onSurfaceVariant = M3Theme.onSurfaceVariant();
    Integer outlineVariant = M3Theme.outlineVariant();
    Integer primary = M3Theme.primary();

    M3Theme.toolbar(b.toolbar);
    styleTerminalTabs();
    styleExtraKeys();

    if (onSurface != null) b.handleChevron.setColorFilter(onSurface);
    if (primary != null) b.commandInputLayout.setEndIconTintList(ColorStateList.valueOf(primary));
    if (onSurfaceVariant != null) b.commandInput.setHintTextColor(onSurfaceVariant);
    if (outlineVariant != null) {
      applyViewColor(b.dividerTop, outlineVariant);
      applyViewColor(b.dragHandlePill, outlineVariant);
    }
    styleInstallBanner();

    applyColorBackground();
  }

  @Override
  protected void applyOwnTheme(ThemeUtils themeUtils) {
    super.applyOwnTheme(themeUtils);
    if (b != null) {
      if (b.tabLayout != null) {
        styleTerminalTabs();
      }
      styleExtraKeys();
      styleInstallBanner();
      applyColorBackground();
      themeUtils.applyImageBackground(b.backgroundIconTerminal);
    }
  }

  /**
   * Matches the tabs with the rest of the M3 surface container system (like ThemeEditor), fading
   * alpha over the background image.
   */
  private void styleTerminalTabs() {
    Integer low = fallback(M3Theme.surfaceContainerLow(), M3Theme.surfaceContainer());
    if (low != null) {
      b.tabLayout.setBackgroundColor(0);
    }
    M3Theme.tabs(b.tabLayout);
    Integer primary = M3Theme.primary();
    if (primary != null) b.tabLayout.setSelectedTabIndicatorColor(primary);
    for (int i = 0; i < b.tabLayout.getTabCount(); i++) {
      updateTabView(b.tabLayout.getTabAt(i), i);
    }
  }
  private void applyColorBackground() {
    try {
      ThemeUtils themeUtils = new ThemeUtils(new ThemeManager(this));
      GhostTheme theme = themeUtils.getTheme();
      if (b == null || b.mainContent == null || theme == null || theme.getActivity() == null) {
        return;
      }
      String background = theme.getActivity().getBackground();
      if (background != null && !background.isEmpty()) {
        b.mainContent.setBackgroundColor(Color.parseColor(background));
      }
    } catch (Throwable ignored) {
    }
  }

  private boolean isBackgroundImageEnabled() {
    boolean showBg = new PreferencesUtils(this).isShowBackground();
    try {
      GhostTheme theme = new ThemeUtils(new ThemeManager(this)).getTheme();
      return showBg
          && theme != null
          && theme.getWidget() != null
          && theme.getWidget().getImagepath() != null
          && !theme.getWidget().getImagepath().isEmpty();
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void applyViewColor(View view, int color) {
    if (view == null) return;
    Drawable background = view.getBackground();
    if (background != null) {
      background.mutate().setTint(color);
    } else {
      view.setBackgroundColor(color);
    }
  }

  // ─── Init overlay ────────────────────────────────────────────────────

  private void setupInitOverlay() {
    initOverlay = b.getRoot().findViewById(R.id.initOverlay);
    initOverlayProgress = b.getRoot().findViewById(R.id.initOverlayProgress);
    TextView versionText = b.getRoot().findViewById(R.id.initOverlayVersion);
    GLSLShaderView shader = b.getRoot().findViewById(R.id.initOverlayShader);
    if (shader != null) {
      if (new ComponentsPrefs(this).isGlslEnabled()) {
        int[] colors = GlslPalette.accent();
        shader.setColors(colors[0], colors[1], colors[2]);
      } else {
        shader.setVisibility(View.GONE);
      }
    }
    String version;
    try {
      version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (PackageManager.NameNotFoundException e) {
      version = null;
    }
    versionText.setText(
        version != null ? getString(R.string.terminal_version_label, "v" + version) : null);
    viewModel.setInitOverlayShower(this::showInitOverlay);
    viewModel.setInitOverlayHider(this::hideInitOverlay);
  }

  private void showInitOverlay() {
    if (initOverlay == null || isDestroyed()) return;
    initOverlay.setVisibility(View.VISIBLE);
    initOverlay.setAlpha(0f);
    InstallProgressStyle.apply(initOverlayProgress, true);
    initOverlay
        .animate()
        .alpha(1f)
        .setDuration(400)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }

  private void hideInitOverlay() {
    if (initOverlay == null || isDestroyed()) return;
    initOverlay
        .animate()
        .alpha(0f)
        .setDuration(500)
        .setInterpolator(new DecelerateInterpolator())
        .withEndAction(
            () -> {
              initOverlay.setVisibility(View.GONE);
              InstallProgressStyle.apply(initOverlayProgress, false);
            })
        .start();
  }

  // ─── Back ────────────────────────────────────────────────────────────

  private void setupBackHandler() {
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (inputDock != null && inputDock.isExpanded()) {
                  inputDock.collapse();
                  return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
              }
            });
  }

  // ─── Debian install ──────────────────────────────────────────────────

  private void confirmAndRemoveDebian() {
    new DialogCompat(this)
        .setTitle(getString(R.string.terminal_remove_debian))
        .setMessage(getString(R.string.terminal_confirm_remove_debian_message))
        .setPositiveButton(
            getString(R.string.terminal_action_remove),
            (dialog, which) ->
                DebianBootstrap.uninstall(
                    this,
                    () -> {
                      GhostToast.makeText(
                              this,
                              getString(R.string.terminal_debian_removed),
                              GhostToast.LENGTH_SHORT)
                          .show();
                    }))
        .setNegativeButton(getString(R.string.terminal_action_cancel), null)
        .show();
  }

  private enum InstallBannerState {
    HIDDEN,
    RUNNING,
    FAILED,
    READY
  }

  private InstallBannerState bannerState = InstallBannerState.HIDDEN;

  private void setupInstallBanner() {
    b.installBannerAction.setOnClickListener(v -> onInstallBannerAction());
    b.installBannerDismiss.setOnClickListener(v -> hideInstallBanner());
    if (TextUtils.getLayoutDirectionFromLocale(getResources().getConfiguration().getLocales().get(0))
        == View.LAYOUT_DIRECTION_RTL) {
      mirrorInstallBannerForRtl();
    }
    styleInstallBanner();
  }

  /**
   * The app declares supportsRtl=false, so layout direction never resolves to RTL. For a
   * right-to-left locale the banner row is mirrored by hand: children reversed, start/end margins
   * and paddings swapped, and the progress fills from the right.
   */
  private void mirrorInstallBannerForRtl() {
    LinearLayout row = b.installBannerRow;
    int count = row.getChildCount();
    View[] children = new View[count];
    for (int i = 0; i < count; i++) children[i] = row.getChildAt(i);
    row.removeAllViews();
    for (int i = count - 1; i >= 0; i--) {
      View child = children[i];
      LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
      int start = lp.getMarginStart();
      lp.setMarginStart(lp.getMarginEnd());
      lp.setMarginEnd(start);
      row.addView(child, lp);
    }
    b.installBanner.setPaddingRelative(
        b.installBanner.getPaddingEnd(),
        b.installBanner.getPaddingTop(),
        b.installBanner.getPaddingStart(),
        b.installBanner.getPaddingBottom());
    LinearLayout.LayoutParams progressLp =
        (LinearLayout.LayoutParams) b.installBannerProgress.getLayoutParams();
    int progressStart = progressLp.getMarginStart();
    progressLp.setMarginStart(progressLp.getMarginEnd());
    progressLp.setMarginEnd(progressStart);
    b.installBannerProgress.setLayoutParams(progressLp);
    b.installBannerProgress.setIndicatorDirection(
        LinearProgressIndicator.INDICATOR_DIRECTION_RIGHT_TO_LEFT);
  }

  private void styleInstallBanner() {
    int onSurface = fallback(M3Theme.onSurface(), Color.WHITE);
    int primary = fallback(M3Theme.primary(), onSurface);
    Integer container = M3Theme.surfaceContainerHigh();
    if (container != null) b.installBanner.setBackgroundColor(container);
    b.installBannerTitle.setTextColor(onSurface);
    b.installBannerStatus.setTextColor(onSurface);
    b.installBannerDismiss.setImageTintList(ColorStateList.valueOf(onSurface));
    b.installBannerAction.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
    b.installBannerAction.setStrokeWidth(0);
    b.installBannerAction.setTextColor(primary);
    b.installBannerAction.setRippleColor(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 0x33)));
    int iconColor =
        bannerState == InstallBannerState.FAILED ? fallback(M3Theme.error(), primary) : primary;
    b.installBannerIcon.setImageTintList(ColorStateList.valueOf(iconColor));
    InstallProgressStyle.apply(
        b.installBannerProgress, bannerState == InstallBannerState.RUNNING);
  }

  /**
   * Shows the non-blocking install progress above the terminal.
   *
   * @param percent download percentage, or a negative value for an indeterminate phase
   */
  private void showInstallRunning(String status, int percent) {
    bannerState = InstallBannerState.RUNNING;
    b.installBanner.setVisibility(View.VISIBLE);
    b.installBannerTitle.setText(R.string.terminal_install_banner_title);
    b.installBannerStatus.setText(status);
    b.installBannerAction.setText(R.string.terminal_action_cancel);
    b.installBannerAction.setVisibility(View.VISIBLE);
    b.installBannerDismiss.setVisibility(View.GONE);
    b.installBannerIcon.setImageResource(R.drawable.ic_download);
    b.installBannerProgress.setVisibility(View.VISIBLE);
    if (percent < 0) {
      if (!b.installBannerProgress.isIndeterminate()) {
        b.installBannerProgress.setVisibility(View.INVISIBLE);
        b.installBannerProgress.setIndeterminate(true);
        b.installBannerProgress.setVisibility(View.VISIBLE);
      }
    } else {
      if (b.installBannerProgress.isIndeterminate()) {
        b.installBannerProgress.setVisibility(View.INVISIBLE);
        b.installBannerProgress.setIndeterminate(false);
        b.installBannerProgress.setVisibility(View.VISIBLE);
      }
      b.installBannerProgress.setProgressCompat(percent, true);
    }
    styleInstallBanner();
  }

  private void showInstallFailed(String message) {
    bannerState = InstallBannerState.FAILED;
    b.installBanner.setVisibility(View.VISIBLE);
    b.installBannerTitle.setText(R.string.terminal_install_failed_title);
    b.installBannerStatus.setText(message);
    b.installBannerAction.setText(R.string.terminal_action_retry);
    b.installBannerAction.setVisibility(View.VISIBLE);
    b.installBannerDismiss.setVisibility(View.VISIBLE);
    b.installBannerIcon.setImageResource(R.drawable.ic_info);
    b.installBannerProgress.setVisibility(View.GONE);
    styleInstallBanner();
  }

  private void showInstallReady() {
    bannerState = InstallBannerState.READY;
    b.installBanner.setVisibility(View.VISIBLE);
    b.installBannerTitle.setText(R.string.terminal_install_ready_title);
    b.installBannerStatus.setText(R.string.terminal_install_ready_message);
    b.installBannerAction.setText(R.string.terminal_action_open_debian);
    b.installBannerAction.setVisibility(View.VISIBLE);
    b.installBannerDismiss.setVisibility(View.VISIBLE);
    b.installBannerIcon.setImageResource(R.drawable.check_24px);
    b.installBannerProgress.setVisibility(View.GONE);
    styleInstallBanner();
  }

  private void hideInstallBanner() {
    bannerState = InstallBannerState.HIDDEN;
    b.installBanner.setVisibility(View.GONE);
    InstallProgressStyle.apply(b.installBannerProgress, false);
  }

  private void onInstallBannerAction() {
    switch (bannerState) {
      case RUNNING -> {
        DebianInstaller.cancelInstall();
        hideInstallBanner();
        GhostToast.makeText(
                this, getString(R.string.terminal_install_cancelled), GhostToast.LENGTH_SHORT)
            .show();
      }
      case FAILED -> startDebianInstall();
      case READY -> {
        hideInstallBanner();
        addNewDebianSession();
      }
      default -> hideInstallBanner();
    }
  }

  private DebianInstaller.InstallListener getOrCreateInstallListener() {
    if (installListener != null) return installListener;
    installListener =
        new DebianInstaller.InstallListener() {
          @Override
          public void onDownloadProgress(int percent) {
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  showInstallRunning(
                      getString(R.string.terminal_status_downloading, percent), percent);
                });
          }

          @Override
          public void onExtractProgress(int extractedEntries) {
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  showInstallRunning(
                      getString(R.string.terminal_status_extracting, extractedEntries), -1);
                });
          }

          @Override
          public void onSuccess() {
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  showInstallReady();
                  GhostToast.makeText(
                          TerminalActivity.this,
                          getString(R.string.terminal_debian_installed_success),
                          GhostToast.LENGTH_LONG)
                      .show();
                  viewModel.bindService();
                  viewModel.setServiceListener();
                });
          }

          @Override
          public void onError(String message) {
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  showInstallFailed(message);
                });
          }
        };
    return installListener;
  }

  private void startDebianInstall() {
    showInstallRunning(
        getString(R.string.terminal_status_starting_download)
            + " · "
            + getString(R.string.terminal_install_banner_hint),
        -1);
    DebianInstaller.installDebian(getApplicationContext(), getOrCreateInstallListener());
  }

  private void attachToRunningInstall() {
    showInstallRunning(getString(R.string.terminal_status_starting_download), -1);
    DebianInstaller.attach(getOrCreateInstallListener());
  }

  private void startTerminalBackup() {
    if (TerminalBackupManager.isRunning()) {
      GhostToast.makeText(this, R.string.terminal_backup_busy, GhostToast.LENGTH_SHORT).show();
      return;
    }
    showTransferProgress(true);
    TerminalBackupManager.backup(this, createTransferListener());
  }

  private void showRestorePicker() {
    if (TerminalBackupManager.isRunning()) {
      GhostToast.makeText(this, R.string.terminal_backup_busy, GhostToast.LENGTH_SHORT).show();
      return;
    }
    List<File> backups = TerminalBackupManager.listBackups(this);
    if (backups.isEmpty()) {
      GhostToast.makeText(this, R.string.terminal_backup_none, GhostToast.LENGTH_LONG).show();
      return;
    }
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    List<String> labels = new ArrayList<>();
    for (File file : backups) {
      labels.add(
          getString(
              R.string.terminal_backup_item,
              format.format(new Date(file.lastModified())),
              StorageUtils.formatSize(file.length())));
    }
    new LiquidGlassDialogBuilderJava(this)
        .setTitle(R.string.terminal_restore_pick)
        .setItems(
            labels.toArray(new String[0]),
            (dialog, which) -> confirmRestore(backups.get(which)))
        .setNegativeButton(R.string.terminal_action_cancel, null)
        .show();
  }

  private void confirmRestore(File archive) {
    new LiquidGlassDialogBuilderJava(this)
        .setTitle(R.string.terminal_restore_menu)
        .setMessage(getString(R.string.terminal_confirm_restore_message, archive.getName()))
        .setNegativeButton(R.string.terminal_action_cancel, null)
        .setPositiveButton(
            R.string.terminal_restore_confirm,
            (dialog, which) -> {
              viewModel.unbindService();
              showTransferProgress(false);
              TerminalBackupManager.restore(TerminalActivity.this, archive, createTransferListener());
            })
        .show();
  }

  private void showTransferProgress(boolean backup) {
    transferIsBackup = backup;
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (16 * getResources().getDisplayMetrics().density);
    layout.setPadding(pad, pad, pad, pad);

    transferStatusText = new TextView(this);
    transferStatusText.setText(R.string.terminal_transfer_scanning);

    transferProgressBar =
        new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    transferProgressBar.setMax(100);
    transferProgressBar.setIndeterminate(true);

    transferPathText = new TextView(this);
    transferPathText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
    transferPathText.setMaxLines(1);
    transferPathText.setEllipsize(TextUtils.TruncateAt.MIDDLE);

    layout.addView(transferStatusText);
    layout.addView(transferProgressBar);
    layout.addView(transferPathText);

    transferDialog =
        new LiquidGlassDialogBuilderJava(this)
            .setTitle(
                backup ? R.string.terminal_backup_title : R.string.terminal_restore_title)
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(
                R.string.terminal_action_cancel,
                (dialog, which) -> TerminalBackupManager.cancel())
            .create();
    transferDialog.show();
  }

  private TerminalBackupManager.ProgressListener createTransferListener() {
    return new TerminalBackupManager.ProgressListener() {
      @Override
      public void onProgress(
          long processedBytes, long totalBytes, int percent, String currentPath) {
        if (transferStatusText == null) {
          return;
        }
        if (percent < 0) {
          transferStatusText.setText(R.string.terminal_transfer_scanning);
          transferProgressBar.setIndeterminate(true);
        } else {
          transferProgressBar.setIndeterminate(false);
          transferProgressBar.setProgress(percent);
          transferStatusText.setText(
              getString(
                  transferIsBackup
                      ? R.string.terminal_backup_working
                      : R.string.terminal_restore_working,
                  StorageUtils.formatSize(processedBytes),
                  StorageUtils.formatSize(totalBytes),
                  percent));
        }
        if (currentPath != null) {
          transferPathText.setText(currentPath);
        }
      }

      @Override
      public void onSuccess(File archive, long sizeBytes) {
        if (transferDialog != null) {
          transferDialog.dismiss();
        }
        if (transferIsBackup) {
          GhostToast.makeText(
                  TerminalActivity.this,
                  getString(
                      R.string.terminal_backup_done,
                      StorageUtils.formatSize(sizeBytes),
                      archive.getAbsolutePath()),
                  GhostToast.LENGTH_LONG)
              .show();
        } else {
          viewModel.bindService();
          viewModel.setServiceListener();
          GhostToast.makeText(
                  TerminalActivity.this,
                  getString(R.string.terminal_restore_done, StorageUtils.formatSize(sizeBytes)),
                  GhostToast.LENGTH_LONG)
              .show();
        }
      }

      @Override
      public void onCancelled() {
        if (transferDialog != null) {
          transferDialog.dismiss();
        }
        if (!transferIsBackup) {
          viewModel.bindService();
          viewModel.setServiceListener();
        }
        GhostToast.makeText(
                TerminalActivity.this,
                transferIsBackup
                    ? R.string.terminal_backup_cancelled
                    : R.string.terminal_restore_cancelled,
                GhostToast.LENGTH_SHORT)
            .show();
      }

      @Override
      public void onError(String message, Throwable error) {
        if (transferDialog != null) {
          transferDialog.dismiss();
        }
        if (!transferIsBackup) {
          viewModel.bindService();
          viewModel.setServiceListener();
        }
        GhostToast.makeText(
                TerminalActivity.this,
                getString(
                    transferIsBackup
                        ? R.string.terminal_backup_failed
                        : R.string.terminal_restore_failed,
                    message),
                GhostToast.LENGTH_LONG)
            .show();
      }
    };
  }

  // ─── Pager adapter ───────────────────────────────────────────────────

  private class SessionPagerAdapter extends FragmentStateAdapter {

    SessionPagerAdapter(TerminalActivity activity) {
      super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
      TerminalTab tab = viewModel.getSessionList().get(position);
      return TerminalSessionFragment.newInstance(tab.id);
    }

    @Override
    public int getItemCount() {
      return viewModel.getSessionList().size();
    }

    @Override
    public long getItemId(int position) {
      TerminalTab tab = viewModel.getSessionList().get(position);
      return tab.id;
    }

    @Override
    public boolean containsItem(long itemId) {
      return viewModel.containsSessionId((int) itemId);
    }
  }

  private static int fallback(Integer value, int def) {
    return value != null ? value : def;
  }
}
