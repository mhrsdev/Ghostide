package ir.hanzodev1375.ghostide.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import ir.hanzodev1375.components.views.GhostToast;

import java.util.List;

import androidx.fragment.app.Fragment;

import ir.hanzodev1375.components.childern.ViewChilder;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginScreen;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.PluginUsage;
import ir.hanzodev1375.ghostide.plugin.api.ExtensionRegistration;
import ir.hanzodev1375.ghostide.plugin.api.GlobalRegistry;
import ir.hanzodev1375.ghostide.plugin.gpl.GplPluginLoader;
import ir.theme.M3Theme;

/**
 * Hosts one {@link PluginScreen} at a time, chosen by {@link #EXTRA_SCREEN_ID}. This is the only
 * Activity a plugin's screen ever runs inside; the plugin itself never declares one.
 */
public class PluginScreenActivity extends BaseCompat {

  public static final String EXTRA_SCREEN_ID = "ir.hanzodev1375.ghostide.extra.PLUGIN_SCREEN_ID";

  private String ownerPluginId;
  private boolean ownedByGplPlugin;

  public static Intent createIntent(Context context, String screenId) {
    Intent intent = new Intent(context, PluginScreenActivity.class);
    intent.putExtra(EXTRA_SCREEN_ID, screenId);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_plugin_screen);
    setupBackgroundBlur();
    applySafeAreaPadding(findViewById(R.id.plugin_screen_container), true);

    String screenId = getIntent().getStringExtra(EXTRA_SCREEN_ID);
    ExtensionRegistration<PluginScreen> registration = findScreen(screenId);
    PluginScreen screen = registration == null ? null : registration.extension();
    if (screen == null) {
      GhostToast.makeText(this, "Plugin screen not found: " + screenId, GhostToast.LENGTH_SHORT).show();
      finish();
      return;
    }

    ownerPluginId = registration.ownerPluginId();
    ownedByGplPlugin = GplPluginLoader.getInstance(this).isLoaded(ownerPluginId);
    PluginUsage.screenOpened(ownerPluginId);
    setTitle(screen.getTitle());
    if (savedInstanceState == null) {
      Fragment fragment = screen.createFragment();
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.plugin_screen_container, fragment)
          .commit();
    }

    M3Theme.applyTopLevel(findViewById(R.id.plugin_screen_container));
  }

  /**
   * Closes the screen when its plugin was turned off while this screen was in the background, so
   * no code of a stopped plugin keeps running in front of the user.
   */
  @Override
  protected void onResume() {
    super.onResume();
    if (ownedByGplPlugin
        && ownerPluginId != null
        && !GplPluginLoader.getInstance(this).isLoaded(ownerPluginId)) {
      GhostToast.makeText(this, R.string.plugin_screen_plugin_stopped, GhostToast.LENGTH_SHORT)
          .show();
      finish();
    }
  }

  @Override
  protected void onDestroy() {
    PluginUsage.screenClosed(ownerPluginId);
    ownerPluginId = null;
    super.onDestroy();
  }

  private void setupBackgroundBlur() {
    ViewChilder background = findViewById(R.id.backgroundIconPluginScreen);
    View container = findViewById(R.id.plugin_screen_container);
    if (background == null || container == null) return;
    setupBackgroundBlur(background, container);
  }

  private static ExtensionRegistration<PluginScreen> findScreen(String screenId) {
    if (screenId == null) {
      return null;
    }
    List<ExtensionRegistration<PluginScreen>> screens =
        GlobalRegistry.extensions().registrations(PluginUiExtensionPoints.PLUGIN_SCREEN);
    for (ExtensionRegistration<PluginScreen> registration : screens) {
      if (screenId.equals(registration.extension().getId())) {
        return registration;
      }
    }
    return null;
  }
}
