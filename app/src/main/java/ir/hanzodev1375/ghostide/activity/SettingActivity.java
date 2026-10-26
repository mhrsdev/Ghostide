package ir.hanzodev1375.ghostide.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
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
import java.util.Locale;

/**
 * Thin coordinator for the settings screen. Every section (Editor/App/AI/LSP) lives in
 * {@code ir.hanzodev1375.ghostide.settings} and owns its own rows, clicks and dialogs; this
 * activity only wires views, search and theme together.
 *
 * <p>Search filters every section at once: a section whose title matches shows all of its rows,
 * otherwise only the matching rows are shown. Sections without matches are hidden, the others are
 * opened and their headers show the match count. Clearing the query restores the sections that
 * were open before the search. The open sections, the scroll position and an open search survive
 * rotation and activity recreation.
 */
public class SettingActivity extends BaseCompat {

  private static final int MIN_POOLED_ROWS = 30;
  private static final int WARM_UP_ROWS = 48;
  private static final int WARM_UP_BATCH = 3;
  private static final long FILTER_DELAY_MS = 200L;
  private static final int SEARCH_BOTTOM_GAP_DP = 4;
  private static final int LIST_END_CLEARANCE_DP = 80;
  private static final String STATE_SEARCH_VISIBLE = "settings_search_visible";
  private static final String STATE_PRE_SEARCH_EXPANDED = "settings_pre_search_expanded";

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
  private ExpandableLayout expandEditor, expandApp, expandLsp, expandAi, expandRunner,
      expandIconPack, expandBackgroundPlugin, expandPlugins;
  private ExpandableLayout[] cards;
  private SettingSection[] sections;
  private RecyclerView rvEditor;
  private SearchLayout ser;
  private View searchEmptyView;
  private Runnable filterRunnable;
  private String appliedQuery = "";
  private boolean[] preSearchExpanded;
  private RecyclerView.RecycledViewPool rowPool;
  private MessageQueue.IdleHandler rowPoolWarmUp;
  private final OnBackPressedCallback closeSearchOnBack =
      new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
          toggleSearch();
        }
      };

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
    searchEmptyView = findViewById(R.id.settings_search_empty);

    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.settings_title);
    }

    expandEditor = findViewById(R.id.expandEditor);
    expandApp = findViewById(R.id.expandApp);
    expandAi = findViewById(R.id.expandAi);
    expandLsp = findViewById(R.id.lspsetting);
    expandRunner = findViewById(R.id.expandRunner);
    expandIconPack = findViewById(R.id.expandIconPack);
    expandBackgroundPlugin = findViewById(R.id.expandBackgroundPlugin);
    expandPlugins = findViewById(R.id.expandPlugins);
    cards =
        new ExpandableLayout[] {
          expandEditor,
          expandApp,
          expandAi,
          expandLsp,
          expandRunner,
          expandIconPack,
          expandBackgroundPlugin,
          expandPlugins
        };

    editorSection = new EditorSettingSection(this, prefs);
    appSection = new AppSettingSection(this, prefs);
    aiSection = new AiSettingSection(this, aiPrefs);
    lspSection = new LspSettingSection(this, prefs);
    runnerSection = new CodeRunnerSetting(this, prefs);
    iconPackSection = new IconPackSettingSection(this, prefs);
    backgroundPluginSection = new BackgroundPluginSettingSection(this);
    pluginSection = new PluginSettingSection(this);
    sections =
        new SettingSection[] {
          editorSection,
          appSection,
          aiSection,
          lspSection,
          runnerSection,
          iconPackSection,
          backgroundPluginSection,
          pluginSection
        };

    setExpandableBackdrop(findViewById(R.id.backgroundIconSetting));
    rvEditor = expandEditor.getRecyclerView();
    RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();
    sharedPool.setMaxRecycledViews(0, MIN_POOLED_ROWS);
    setupSection(expandEditor, editorSection, sharedPool, R.string.section_editor,
        R.string.settings_summary_editor, R.drawable.ic_plugin_editor);
    setupSection(expandApp, appSection, sharedPool, R.string.section_app,
        R.string.settings_summary_app, R.drawable.ic_phone_android);
    setupSection(expandAi, aiSection, sharedPool, R.string.ai_section_title,
        R.string.settings_summary_ai, R.drawable.ic_star);
    setupSection(expandLsp, lspSection, sharedPool, R.string.lsptitles,
        R.string.settings_summary_lsp, R.drawable.ic_plugin_lsp);
    setupSection(expandRunner, runnerSection, sharedPool, R.string.runner_section_title,
        R.string.settings_summary_runner, R.drawable.play_arrow);
    setupSection(expandIconPack, iconPackSection, sharedPool, R.string.icon_pack_title,
        R.string.settings_summary_icon_pack, R.drawable.outline_color_lens);
    setupSection(expandBackgroundPlugin, backgroundPluginSection, sharedPool,
        R.string.background_plugin_title, R.string.settings_summary_background_plugin,
        R.drawable.ic_photo);
    setupSection(expandPlugins, pluginSection, sharedPool, R.string.plugin_settings_title,
        R.string.settings_summary_plugins, R.drawable.ic_plugin_misc);
    sharedPool.setMaxRecycledViews(0, Math.max(MIN_POOLED_ROWS, totalRowCount()));
    rowPool = sharedPool;
    warmUpRowPool();
    updateSectionVisibility();

    ser.setIconClose(R.drawable.ic_close);
    ser.setIconSearch(R.drawable.outline_search);
    ser.setHint(getString(R.string.settings_search_hint));
    getOnBackPressedDispatcher().addCallback(this, closeSearchOnBack);

    View root = findViewById(R.id.settingRoot);
    setupInsets(root);

    ser.setOnTextChangedListener(
        (item) -> {
          ser.removeCallbacks(filterRunnable);
          filterRunnable = () -> applyQuery(item);
          ser.postDelayed(filterRunnable, FILTER_DELAY_MS);
        });

    if (savedInstanceState == null
        && "githublogin".equals(getIntent().getStringExtra("open_section"))) {
      ThreadUtils.runOnUiThreadDelayed(
          () -> {
            if (isFinishing() || isDestroyed()) return;
            expandApp.expand();
            appSection.openGitHubAccount();
          },
          500);
    }

    M3Theme.applyTopLevel(root);
    themeSearchEmptyView();
  }

  /**
   * Applies the system bar, display cutout and keyboard insets once, at the root: the app bar
   * background runs under the status bar while the toolbar sits below it, the cards and the
   * floating search bar clear the side bars, and both the list end and the search bar clear the
   * taller of the navigation bar or the keyboard. The insets are consumed so no child applies them
   * a second time.
   */
  private void setupInsets(View root) {
    View appbar = findViewById(R.id.appbar);
    NestedScrollView scrollView = findViewById(R.id.scrollView);
    ViewGroup.MarginLayoutParams searchParams =
        (ViewGroup.MarginLayoutParams) ser.getLayoutParams();
    int searchLeft = searchParams.leftMargin;
    int searchRight = searchParams.rightMargin;
    float density = getResources().getDisplayMetrics().density;
    int searchGap = Math.round(SEARCH_BOTTOM_GAP_DP * density);
    int listEndGap = Math.round(LIST_END_CLEARANCE_DP * density);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
          int bottomInset = Math.max(bars.bottom, ime.bottom);
          appbar.setPadding(bars.left, bars.top, bars.right, 0);
          searchParams.leftMargin = searchLeft + bars.left;
          searchParams.rightMargin = searchRight + bars.right;
          searchParams.bottomMargin = bottomInset + searchGap;
          ser.setLayoutParams(searchParams);
          scrollView.setPadding(bars.left, 0, bars.right, bottomInset + listEndGap);
          return WindowInsetsCompat.CONSUMED;
        });
  }

  private void setupSection(
      ExpandableLayout card,
      SettingSection section,
      RecyclerView.RecycledViewPool pool,
      int titleRes,
      int summaryRes,
      int iconRes) {
    card.setTitle(getString(titleRes));
    card.setSummary(getString(summaryRes));
    card.setIcon(iconRes);
    RecyclerView list = card.getRecyclerView();
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setRecycledViewPool(pool);
    list.setItemAnimator(null);
    list.setAdapter(section.createAdapter());
  }

  private void themeSearchEmptyView() {
    if (searchEmptyView == null) return;
    M3Theme.apply(searchEmptyView);
    M3Theme.imageView((ImageView) findViewById(R.id.settings_search_empty_icon));
  }

  /**
   * Filters every section with {@code rawQuery}. An empty query ends the search and restores the
   * sections that were open before it began.
   */
  private void applyQuery(String rawQuery) {
    if (isFinishing() || isDestroyed() || sections == null) return;
    String query = rawQuery == null ? "" : rawQuery.trim();
    if (query.isEmpty()) {
      endSearch();
      return;
    }
    if (preSearchExpanded == null) preSearchExpanded = expandedStates();
    Locale locale = Locale.getDefault();
    String lowerQuery = query.toLowerCase(locale);
    int total = 0;
    for (int i = 0; i < sections.length; i++) {
      SettingSection section = sections[i];
      ExpandableLayout card = cards[i];
      SettingsAdapter adapter = section.getAdapter();
      if (adapter == null || adapter.getFullItemCount() == 0) {
        card.setVisibility(View.GONE);
        continue;
      }
      boolean titleMatches = card.getTitle().toString().toLowerCase(locale).contains(lowerQuery);
      if (titleMatches) {
        section.resetToFull();
      } else {
        section.filter(query);
      }
      int shown = adapter.getItemCount();
      total += shown;
      card.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
      card.setSummaryOverride(
          shown > 0
              ? getResources().getQuantityString(R.plurals.settings_search_matches, shown, shown)
              : null);
      if (shown > 0) card.expand();
      else card.collapse();
    }
    searchEmptyView.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
    if (appliedQuery != null && !appliedQuery.equals(query)) {
      NestedScrollView scrollView = findViewById(R.id.scrollView);
      if (scrollView != null) scrollView.scrollTo(0, 0);
    }
    appliedQuery = query;
  }

  private void endSearch() {
    resetAll();
    searchEmptyView.setVisibility(View.GONE);
    for (ExpandableLayout card : cards) {
      card.setSummaryOverride(null);
    }
    updateSectionVisibility();
    if (preSearchExpanded != null && preSearchExpanded.length == cards.length) {
      for (int i = 0; i < cards.length; i++) {
        if (preSearchExpanded[i]) cards[i].expand();
        else cards[i].collapse();
      }
    }
    preSearchExpanded = null;
    appliedQuery = "";
  }

  private boolean[] expandedStates() {
    boolean[] states = new boolean[cards.length];
    for (int i = 0; i < cards.length; i++) {
      states[i] = cards[i].isExpanded();
    }
    return states;
  }

  private void resetAll() {
    for (SettingSection section : sections) {
      section.resetToFull();
    }
  }

  private int totalRowCount() {
    int total = 0;
    for (SettingSection section : sections) {
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
  private void warmUpRowPool() {
    SettingsAdapter adapter = editorSection.getAdapter();
    if (adapter == null) return;
    cancelRowPoolWarmUp();
    RecyclerView.RecycledViewPool pool = rowPool;
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

  private void cancelRowPoolWarmUp() {
    if (rowPoolWarmUp != null) {
      Looper.myQueue().removeIdleHandler(rowPoolWarmUp);
      rowPoolWarmUp = null;
    }
  }

  /**
   * Pooled rows were themed when they were built, so a theme change would leave them with stale
   * colours. Once the final theme is applied the pool is emptied and refilled with rows built under
   * it; the per-frame calls of a theme transition are deliberately ignored.
   */
  private void rebuildRowPool() {
    if (rowPool == null || editorSection == null) return;
    rowPool.clear();
    warmUpRowPool();
  }

  @Override
  protected void onDestroy() {
    cancelRowPoolWarmUp();
    if (ser != null) ser.removeCallbacks(filterRunnable);
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (ser != null) outState.putBoolean(STATE_SEARCH_VISIBLE, ser.isShow());
    if (preSearchExpanded != null) {
      outState.putBooleanArray(STATE_PRE_SEARCH_EXPANDED, preSearchExpanded);
    }
  }

  @Override
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    boolean[] expanded = savedInstanceState.getBooleanArray(STATE_PRE_SEARCH_EXPANDED);
    if (expanded != null && cards != null && expanded.length == cards.length) {
      preSearchExpanded = expanded;
    }
    if (savedInstanceState.getBoolean(STATE_SEARCH_VISIBLE, false) && ser != null) {
      ser.setVisibility(View.VISIBLE);
      closeSearchOnBack.setEnabled(true);
      if (!ser.getQuery().trim().isEmpty()) appliedQuery = null;
    }
  }

  /** Shows a card when its section has rows and hides it when it has none. */
  private void updateSectionVisibility() {
    for (int i = 0; i < cards.length; i++) {
      SettingsAdapter adapter = sections[i].getAdapter();
      boolean hasRows = adapter != null && adapter.getFullItemCount() > 0;
      cards[i].setVisibility(hasRows ? View.VISIBLE : View.GONE);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (iconPackSection == null || ser == null || !ser.getQuery().trim().isEmpty()) return;
    iconPackSection.reload();
    backgroundPluginSection.reload();
    pluginSection.reload();
    updateSectionVisibility();
  }

  private ExpandableLayout[] expandableCards() {
    return cards != null ? cards : new ExpandableLayout[0];
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
    themeSearchEmptyView();
  }

  @Override
  protected void onThemeReapplied() {
    rebuildRowPool();
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
      closeSearchOnBack.setEnabled(false);
    } else {
      ser.show();
      ser.requestFocusForEditText();
      closeSearchOnBack.setEnabled(true);
    }
  }
}
