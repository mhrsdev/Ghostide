package ir.hanzodev1375.ghostide.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.example.liquidglass.LiquidGlassTabBar;
import com.google.android.material.appbar.MaterialToolbar;
import ir.hanzodev1375.components.glass.TabBarCompat;
import ir.hanzodev1375.components.sheet.PluginSetupSheet;
import ir.hanzodev1375.components.store.adapter.ViewPagerAdapter;
import ir.hanzodev1375.components.store.event.PluginSetupEvent;
import ir.hanzodev1375.components.store.fragments.PluginStoreFragment;
import ir.hanzodev1375.ghostide.store.ThemeStoreFragment;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity;
import ir.theme.ThemeManager;
import ir.theme.ThemeUtils;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import ir.hanzodev1375.ghostide.plugin.install.GplPluginInstallerHost;
import ir.theme.M3Theme;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class StoreActivity extends BaseCompat {

  private ViewPager2 viewPager;
  private TabBarCompat bottomNav;
  private MaterialToolbar toolbar;
  private GplPluginInstallerHost installerHost;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_store);

    View root = findViewById(R.id.mainRoot);
    View appBarLayout = findViewById(R.id.appBarLayout);
    View storeContent = findViewById(R.id.storeContent);
    toolbar = findViewById(R.id.toolbar);
    viewPager = findViewById(R.id.viewPager);
    bottomNav = findViewById(R.id.bottomNav);

    setSupportActionBar(toolbar);
    toolbar.setNavigationOnClickListener(v -> finish());

    setupBackgroundBlur(findViewById(R.id.backgroundIconStore), appBarLayout, storeContent);
    tintContentForBackground(appBarLayout, storeContent);

    applyWindowInsets(root, appBarLayout, storeContent);

    ViewPagerAdapter adapter =
        new ViewPagerAdapter(this, new PluginStoreFragment(), new ThemeStoreFragment());
    viewPager.setAdapter(adapter);
    viewPager.setUserInputEnabled(true);
    viewPager.setOffscreenPageLimit(ViewPagerAdapter.PAGE_COUNT);
    viewPager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            syncNavItem(position);
            updateTitle(position);
          }
        });

    setupTabBar();
    M3Theme.applyTopLevel(root);
  }

  /**
   * Applies the system bar and display cutout insets once, from the root. The app bar is padded by
   * the status bar, the content column by the side insets, and the floating tab bar is lifted by
   * the navigation bar through its margin (padding would stretch the glass panel and wipe the
   * padding that keeps the end tabs clear of its corner curve). The insets are consumed at the tab bar: its Material
   * fallback navigation view would otherwise pad itself by the navigation bar a second time and
   * grow an empty band under the tabs. The content column always ends above the tab bar so the
   * lists never scroll underneath it.
   */
  private void applyWindowInsets(View root, View appBarLayout, View storeContent) {
    ViewGroup.MarginLayoutParams barParams =
        (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
    int barBaseStart = barParams.getMarginStart();
    int barBaseEnd = barParams.getMarginEnd();
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          appBarLayout.setPadding(0, bars.top, 0, 0);
          storeContent.setPadding(bars.left, 0, bars.right, 0);
          return insets;
        });
    ViewCompat.setOnApplyWindowInsetsListener(
        bottomNav,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
          params.setMarginStart(barBaseStart + bars.left);
          params.setMarginEnd(barBaseEnd + bars.right);
          v.setLayoutParams(params);
          ((TabBarCompat) v).applySystemBottomInset(bars.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    bottomNav.addOnLayoutChangeListener(
        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            keepContentAboveTabBar(storeContent));
  }

  private void keepContentAboveTabBar(View storeContent) {
    ViewGroup.MarginLayoutParams bar = (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
    int reserved = bottomNav.getHeight() + bar.bottomMargin;
    ViewGroup.MarginLayoutParams content =
        (ViewGroup.MarginLayoutParams) storeContent.getLayoutParams();
    if (content.bottomMargin != reserved) {
      content.bottomMargin = reserved;
      storeContent.setLayoutParams(content);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    installerHost = new GplPluginInstallerHost(this);
    installerHost.register();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
  }

  @Override
  protected void onStop() {
    if (installerHost != null) {
      installerHost.unregister();
    }
    if (EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().unregister(this);
    }
    super.onStop();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onPluginSetup(PluginSetupEvent event) {
    if (event == null) {
      return;
    }
    new PluginSetupSheet(this, event.name, event.iconUrl, event.actions, this::runSetupCommand)
        .show();
  }

  private void runSetupCommand(String command) {
    if (command == null || command.trim().isEmpty()) {
      return;
    }
    Intent intent = new Intent(this, TerminalActivity.class);
    intent.putExtra(TerminalActivity.EXTRA_COMMAND, command);
    startActivity(intent);
  }

  private void syncNavItem(int position) {
    bottomNav.setCompatSelectedIndex(position);
  }

  private void setupTabBar() {
    // The backdrop, the live backdrop refresh and the theme tint are all handled by TabBarCompat.
    Integer primary = M3Theme.primary();
    if (primary != null) {
      bottomNav.setCompatSelectedTintColor(primary);
    }
    bottomNav.setCompatTabs(
        java.util.Arrays.asList(
            tabItem(R.drawable.ic_outline_project, R.string.store_tab_projects),
            tabItem(R.drawable.ic_outline_palette, R.string.store_tab_themes),
            tabItem(R.drawable.ic_outline_font, R.string.store_tab_fonts),
            tabItem(R.drawable.ic_outline_extension, R.string.store_tab_plugins),
            tabItem(R.drawable.ic_outline_grid, R.string.store_tab_icons)));
    bottomNav.setCompatOnTabSelected(
        index -> {
          if (index >= 0 && index < ViewPagerAdapter.PAGE_COUNT) {
            viewPager.setCurrentItem(index, true);
          }
          return kotlin.Unit.INSTANCE;
        });
    // The ViewPager fires onPageSelected as soon as the adapter is attached, which happens
    // before the tabs exist, so that sync was dropped. Apply the initial selection here, after
    // setTabs(), otherwise the first tab renders without the glass selection indicator.
    bottomNav.setCompatSelectedIndex(viewPager.getCurrentItem());
  }

  private LiquidGlassTabBar.TabItem tabItem(int iconRes, int titleRes) {
    return new LiquidGlassTabBar.TabItem(
        getString(titleRes), ContextCompat.getDrawable(this, iconRes));
  }

  private void updateTitle(int position) {
    int res;
    switch (position) {
      case ViewPagerAdapter.PAGE_THEMES:
        res = R.string.store_tab_themes;
        break;
      case ViewPagerAdapter.PAGE_FONTS:
        res = R.string.store_tab_fonts;
        break;
      case ViewPagerAdapter.PAGE_PLUGINS:
        res = R.string.store_tab_plugins;
        break;
      case ViewPagerAdapter.PAGE_ICONS:
        res = R.string.store_tab_icons;
        break;
      case ViewPagerAdapter.PAGE_PROJECTS:
      default:
        res = R.string.store_tab_projects;
        break;
    }
    toolbar.setTitle(res);
  }

  private void tintContentForBackground(View... views) {
    if (!new PreferencesUtils(this).isShowBackground()) {
      return;
    }
    ThemeUtils themeUtil = new ThemeUtils(new ThemeManager(this));
    var theme = themeUtil.getTheme();
    if (theme == null
        || theme.getActivity() == null
        || theme.getActivity().getBackground() == null) {
      return;
    }
    int bg = Color.parseColor(theme.getActivity().getBackground());
    int tint = ColorUtils.setAlphaComponent(bg, 128);
    for (View v : views) {
      if (v != null) {
        v.setBackgroundColor(tint);
      }
    }
  }
}
