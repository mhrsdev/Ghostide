package ir.hanzodev1375.ghostide.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.ThreadUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import ir.hanzodev1375.components.SearchLayout;
import ir.hanzodev1375.components.childern.ViewChilder;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.adapters.SettingsAdapter;
import ir.hanzodev1375.ghostide.ai.utils.AiPreferencesUtils;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import ir.hanzodev1375.ghostide.customui.ExpandableLayout;
import ir.hanzodev1375.ghostide.settings.AiSettingSection;
import ir.hanzodev1375.ghostide.settings.AppSettingSection;
import ir.hanzodev1375.ghostide.settings.BackgroundPluginSettingSection;
import ir.hanzodev1375.ghostide.settings.CodeRunnerSetting;
import ir.hanzodev1375.ghostide.settings.EditorSettingSection;
import ir.hanzodev1375.ghostide.settings.IconPackSettingSection;
import ir.hanzodev1375.ghostide.settings.LspSettingSection;
import ir.hanzodev1375.ghostide.settings.PluginSettingSection;
import ir.hanzodev1375.ghostide.settings.SettingSection;
import ir.theme.M3Theme;
import ir.theme.ThemeUtils;

/**
 * Thin coordinator for the settings screen. Every section (Editor/App/AI/LSP) lives in
 * {@code ir.hanzodev1375.ghostide.settings} and owns its own rows, clicks and dialogs; this
 * activity only wires views, search and theme together.
 */
public class SettingActivity extends BaseCompat {

  private static final int MIN_POOLED_ROWS = 30;
  private static final int WARM_UP_ROWS = 48;
  private static final int WARM_UP_BATCH = 3;

  private PreferencesUtils prefs;
  private AiPreferencesUtils aiPrefs;
  private EditorSettingSection editorSection;
  private AppSettingSection appSection;
  private AiSettingSection aiSection;
  private LspSettingSection lspSection;
  private CodeRunnerSetting runnerSection;
  private IconPackSettingSection iconPackSection;
  private BackgroundPluginSettingSection backgroundPluginSection;
  private PluginSettingSection pluginSection;
  private ExpandableLayout expandEditor, expandApp, LspView, expandAi, expandRunner, expandIconPack,
      expandBackgroundPlugin, expandPlugins;
  private RecyclerView rvEditor, rvApp, rvLsp, rvAi, rvRunner, rvIconPack, rvBackgroundPlugin,
      rvPlugins;
  private SearchLayout ser;
  private Runnable filterRunnable;
  private MessageQueue.IdleHandler rowPoolWarmUp;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_setting);
    aiPrefs = new AiPreferencesUtils(this);
    prefs = new PreferencesUtils(this);
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              if (!isFinishing()) setupBackgroundBlur();
            });
    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    ser = findViewById(R.id.searchitem);

    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.settings_title);
    }

    expandEditor = findViewById(R.id.expandEditor);
    expandApp = findViewById(R.id.expandApp);
    expandAi = findViewById(R.id.expandAi);
    LspView = findViewById(R.id.lspsetting);
    expandRunner = findViewById(R.id.expandRunner);
    expandIconPack = findViewById(R.id.expandIconPack);
    expandBackgroundPlugin = findViewById(R.id.expandBackgroundPlugin);
    expandPlugins = findViewById(R.id.expandPlugins);

    expandEditor.setTitle(getString(R.string.section_editor));
    expandApp.setTitle(getString(R.string.section_app));
    expandAi.setTitle(getString(R.string.ai_section_title));
    LspView.setTitle(getString(R.string.lsptitles));
    expandRunner.setTitle(getString(R.string.runner_section_title));
    expandIconPack.setTitle(getString(R.string.icon_pack_title));
    expandBackgroundPlugin.setTitle(getString(R.string.background_plugin_title));
    expandPlugins.setTitle(getString(R.string.plugin_settings_title));

    setExpandableBackdrop(findViewById(R.id.backgroundIconSetting));

    rvEditor = expandEditor.getRecyclerView();
    rvApp = expandApp.getRecyclerView();
    rvAi = expandAi.getRecyclerView();
    rvLsp = LspView.getRecyclerView();
    rvRunner = expandRunner.getRecyclerView();
    rvIconPack = expandIconPack.getRecyclerView();
    rvBackgroundPlugin = expandBackgroundPlugin.getRecyclerView();
    rvPlugins = expandPlugins.getRecyclerView();
    rvEditor.setLayoutManager(new LinearLayoutManager(this));
    rvApp.setLayoutManager(new LinearLayoutManager(this));
    rvAi.setLayoutManager(new LinearLayoutManager(this));
    rvLsp.setLayoutManager(new LinearLayoutManager(this));
    rvRunner.setLayoutManager(new LinearLayoutManager(this));
    rvIconPack.setLayoutManager(new LinearLayoutManager(this));
    rvBackgroundPlugin.setLayoutManager(new LinearLayoutManager(this));
    rvPlugins.setLayoutManager(new LinearLayoutManager(this));

    // Shared recycled view pool for all setting sections to reuse row views
    RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();
    sharedPool.setMaxRecycledViews(0, MIN_POOLED_ROWS);
    rvEditor.setRecycledViewPool(sharedPool);
    rvApp.setRecycledViewPool(sharedPool);
    rvAi.setRecycledViewPool(sharedPool);
    rvLsp.setRecycledViewPool(sharedPool);
    rvRunner.setRecycledViewPool(sharedPool);
    rvIconPack.setRecycledViewPool(sharedPool);
    rvBackgroundPlugin.setRecycledViewPool(sharedPool);
    rvPlugins.setRecycledViewPool(sharedPool);

    editorSection = new EditorSettingSection(this, prefs);
    appSection = new AppSettingSection(this, prefs);
    aiSection = new AiSettingSection(this, aiPrefs);
    lspSection = new LspSettingSection(this, prefs);
    runnerSection = new CodeRunnerSetting(this, prefs);
    iconPackSection = new IconPackSettingSection(this, prefs);
    backgroundPluginSection = new BackgroundPluginSettingSection(this);
    pluginSection = new PluginSettingSection(this);

    rvEditor.setAdapter(editorSection.createAdapter());
    rvApp.setAdapter(appSection.createAdapter());
    rvAi.setAdapter(aiSection.createAdapter());
    rvLsp.setAdapter(lspSection.createAdapter());
    rvRunner.setAdapter(runnerSection.createAdapter());
    rvIconPack.setAdapter(iconPackSection.createAdapter());
    rvBackgroundPlugin.setAdapter(backgroundPluginSection.createAdapter());
    rvPlugins.setAdapter(pluginSection.createAdapter());
    sharedPool.setMaxRecycledViews(0, Math.max(MIN_POOLED_ROWS, totalRowCount()));
    warmUpRowPool(sharedPool);
    hideSectionIfEmpty(expandEditor, editorSection);
    hideSectionIfEmpty(expandApp, appSection);
    hideSectionIfEmpty(expandAi, aiSection);
    hideSectionIfEmpty(LspView, lspSection);
    hideSectionIfEmpty(expandRunner, runnerSection);
    hideSectionIfEmpty(expandIconPack, iconPackSection);
    hideSectionIfEmpty(expandBackgroundPlugin, backgroundPluginSection);
    hideSectionIfEmpty(expandPlugins, pluginSection);

    ser.setIconClose(R.drawable.ic_close);
    ser.setIconSearch(R.drawable.outline_search);

    View root = findViewById(R.id.settingRoot);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
          findViewById(R.id.appbar).setPadding(0, systemBars.top, 0, 0);
          int bottomInset = Math.max(systemBars.bottom, ime.bottom);
          ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) ser.getLayoutParams();
          lp.bottomMargin = bottomInset + (int) (4 * getResources().getDisplayMetrics().density);
          ser.setLayoutParams(lp);
          NestedScrollView scrollView = findViewById(R.id.scrollView);
          scrollView.setPadding(
              0, 0, 0, bottomInset + (int) (80 * getResources().getDisplayMetrics().density));
          return insets;
        });

    // Prevents crash when DiffUtil updates lists while animations are running
    rvEditor.setItemAnimator(null);
    rvApp.setItemAnimator(null);
    rvAi.setItemAnimator(null);
    rvLsp.setItemAnimator(null);
    rvRunner.setItemAnimator(null);
    rvIconPack.setItemAnimator(null);
    rvBackgroundPlugin.setItemAnimator(null);
    rvPlugins.setItemAnimator(null);

    ser.setOnTextChangedListener(
        (item) -> {
          ser.removeCallbacks(filterRunnable);
          filterRunnable =
              () -> {
                if (item.isEmpty()) {
                  resetAll();
                  collapseAll();
                  return;
                }
                filterAll(item);
                expandMatching();
              };
          ser.postDelayed(filterRunnable, 200);
        });

    if ("githublogin".equals(getIntent().getStringExtra("open_section"))) {
      ThreadUtils.runOnUiThreadDelayed(
          () -> {
            expandApp.expand();
            appSection.openGitHubAccount();
          },
          500);
    }

    M3Theme.applyTopLevel(root);
  }

  private void filterAll(String query) {
    editorSection.filter(query);
    appSection.filter(query);
    lspSection.filter(query);
    aiSection.filter(query);
    runnerSection.filter(query);
    iconPackSection.filter(query);
    backgroundPluginSection.filter(query);
    pluginSection.filter(query);
  }

  private void resetAll() {
    for (SettingSection section : settingSections()) {
      section.resetToFull();
    }
  }

  private SettingSection[] settingSections() {
    return new SettingSection[] {
      editorSection,
      appSection,
      lspSection,
      aiSection,
      runnerSection,
      iconPackSection,
      backgroundPluginSection,
      pluginSection
    };
  }

  private int totalRowCount() {
    int total = 0;
    for (SettingSection section : settingSections()) {
      SettingsAdapter adapter = section.getAdapter();
      if (adapter != null) total += adapter.getItemCount();
    }
    return total;
  }

  /**
   * Builds row views while the main thread is idle and parks them in the shared pool.
   *
   * <p>Section lists are sized to their whole content inside the page scroller, so expanding a
   * section or matching a search query binds every row at once. Rows taken from the pool only need
   * binding, which keeps that frame short. The queue drains in small batches and stops as soon as
   * the pool holds {@link #WARM_UP_ROWS} views or the activity goes away.
   */
  private void warmUpRowPool(RecyclerView.RecycledViewPool pool) {
    SettingsAdapter adapter = editorSection.getAdapter();
    if (adapter == null) return;
    int target = Math.min(WARM_UP_ROWS, totalRowCount());
    rowPoolWarmUp =
        () -> {
          if (isFinishing() || isDestroyed()) {
            rowPoolWarmUp = null;
            return false;
          }
          for (int i = 0; i < WARM_UP_BATCH && pool.getRecycledViewCount(0) < target; i++) {
            pool.putRecycledView(adapter.createViewHolder(rvEditor, 0));
          }
          boolean hasMore = pool.getRecycledViewCount(0) < target;
          if (!hasMore) rowPoolWarmUp = null;
          return hasMore;
        };
    Looper.myQueue().addIdleHandler(rowPoolWarmUp);
  }

  @Override
  protected void onDestroy() {
    if (rowPoolWarmUp != null) {
      Looper.myQueue().removeIdleHandler(rowPoolWarmUp);
      rowPoolWarmUp = null;
    }
    super.onDestroy();
  }

  private void expandMatching() {
    expandIfMatching(expandEditor, editorSection);
    expandIfMatching(expandApp, appSection);
    expandIfMatching(expandAi, aiSection);
    expandIfMatching(LspView, lspSection);
    expandIfMatching(expandRunner, runnerSection);
    expandIfMatching(expandIconPack, iconPackSection);
    expandIfMatching(expandBackgroundPlugin, backgroundPluginSection);
    expandIfMatching(expandPlugins, pluginSection);
  }

  /** Opens a section only when the current filter left rows in it, so search never expands empties. */
  private void expandIfMatching(ExpandableLayout layout, SettingSection section) {
    if (layout.getVisibility() != View.VISIBLE) return;
    boolean hasMatches = section.getAdapter() != null && section.getAdapter().getItemCount() > 0;
    if (hasMatches) {
      if (!layout.isExpanded()) layout.expand();
    } else if (layout.isExpanded()) {
      layout.collapse();
    }
  }

  private void collapseAll() {
    for (ExpandableLayout layout : expandableCards()) {
      if (layout.isExpanded()) layout.collapse();
    }
  }

  /** Shows the card when the section has rows and hides it when it has none. */
  private void hideSectionIfEmpty(ExpandableLayout layout, SettingSection section) {
    boolean hasRows = section.getAdapter() != null && section.getAdapter().getItemCount() > 0;
    layout.setVisibility(hasRows ? View.VISIBLE : View.GONE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (iconPackSection == null || ser == null || !ser.getQuery().trim().isEmpty()) return;
    iconPackSection.reload();
    backgroundPluginSection.reload();
    pluginSection.reload();
    hideSectionIfEmpty(expandIconPack, iconPackSection);
    hideSectionIfEmpty(expandBackgroundPlugin, backgroundPluginSection);
    hideSectionIfEmpty(expandPlugins, pluginSection);
  }

  private ExpandableLayout[] expandableCards() {
    if (expandEditor == null) return new ExpandableLayout[0];
    return new ExpandableLayout[] {
      expandEditor,
      expandApp,
      expandAi,
      LspView,
      expandRunner,
      expandIconPack,
      expandBackgroundPlugin,
      expandPlugins
    };
  }

  /** Points every expandable glass card at the settings background so it has a backdrop to sample. */
  private void setExpandableBackdrop(View backdrop) {
    if (backdrop == null) return;
    for (ExpandableLayout layout : expandableCards()) {
      layout.setBackdropSource(backdrop);
    }
  }

  private final Runnable glassRefreshRunnable = this::refreshExpandableGlass;

  private void scheduleGlassRefresh() {
    View host = findViewById(R.id.settings_container);
    if (host == null) return;
    host.removeCallbacks(glassRefreshRunnable);
    host.postDelayed(glassRefreshRunnable, 250L);
    host.postDelayed(glassRefreshRunnable, 800L);
  }

  private void refreshExpandableGlass() {
    if (isFinishing()) return;
    for (ExpandableLayout layout : expandableCards()) {
      layout.refresh();
      layout.invalidate();
    }
  }

  /** Lets settings sections re-run the background blur after a theme/background change. */
  public void refreshBackgroundUi() {
    setupBackgroundBlur();
  }

  private void setupBackgroundBlur() {
    View appbar = findViewById(R.id.appbar);
    View toolbar = findViewById(R.id.toolbar);
    View settingRoot = findViewById(R.id.settingRoot);
    View scrollView = findViewById(R.id.scrollView);
    View settingsContainer = findViewById(R.id.settings_container);
    ViewChilder backgroundIcon = findViewById(R.id.backgroundIconSetting);
    appbar.setBackgroundColor(0);
    toolbar.setBackgroundColor(0);
    if (settingsContainer == null || backgroundIcon == null) return;
    setupBackgroundBlur(backgroundIcon, settingRoot, appbar, scrollView);
    scheduleGlassRefresh();
  }

  /**
   * Every theme change (dialog preview, store preview, applied theme) re-runs the background blur so
   * a backgrounded theme shows its image at once and a backgroundless theme hides the ViewChilder.
   */
  @Override
  protected void applyOwnTheme(ThemeUtils themeUtils) {
    setupBackgroundBlur();
    for (ExpandableLayout layout : expandableCards()) {
      layout.refresh();
      layout.invalidate();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_settings, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    if (item.getItemId() == R.id.action_search) {
      toggleSearch();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void toggleSearch() {
    if (ser == null) return;
    if (ser.isShow()) {
      ser.hide();
      KeyboardUtils.hideSoftInput(ser);
      if (!ser.getQuery().isEmpty()) ser.clear();
    } else {
      ser.show();
      ser.requestFocusForEditText();
    }
  }
}
