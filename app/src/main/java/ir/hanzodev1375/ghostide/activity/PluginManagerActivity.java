package ir.hanzodev1375.ghostide.activity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.components.utils.ParticleItemAnimator;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.adapters.InstalledPluginAdapter;
import ir.hanzodev1375.ghostide.databinding.ActivityPluginManagerBinding;
import ir.hanzodev1375.ghostide.helper.InstalledPluginSheet;
import ir.hanzodev1375.ghostide.models.InstalledPluginInfo;
import ir.hanzodev1375.ghostide.plugin.PluginActivationController;
import ir.hanzodev1375.ghostide.plugin.PluginAvailability;
import ir.hanzodev1375.ghostide.plugin.PluginUsage;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;
import ir.hanzodev1375.ghostide.plugin.gpl.GplInstalledPlugins;
import ir.hanzodev1375.ghostide.plugin.gpl.GplManifest;
import ir.hanzodev1375.ghostide.plugin.gpl.GplManifestReader;
import ir.hanzodev1375.ghostide.plugin.gpl.GplPluginLoader;
import ir.hanzodev1375.ghostide.plugin.gpl.LoadedGplPlugin;
import ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets the user install a {@code .gpl} file, turn all plugins or single plugins on and off,
 * inspect, retry and uninstall them.
 *
 * <p>The screen never loads or unloads a plugin itself: every transition goes through {@link
 * PluginActivationController}, which serializes them on its own worker. This screen only reads
 * the plugins folder on a background thread and renders the snapshot it gets back.
 */
public class PluginManagerActivity extends BaseCompat
    implements PluginActivationController.Listener {

  private static final String GPL_EXTENSION = ".gpl";
  private static final String TAG = "PluginManagerActivity";
  private static final int ICON_MAX_PX = 144;
  private static final int COPY_BUFFER_SIZE = 8192;

  private ActivityPluginManagerBinding bind;
  private GplPluginLoader loader;
  private PluginActivationController controller;
  private InstalledPluginAdapter adapter;
  private ExecutorService scanExecutor;
  private InstalledPluginSheet detailSheet;
  private final AtomicInteger scanGeneration = new AtomicInteger();
  private final Map<String, CachedPackage> packageCache = new HashMap<>();
  private List<InstalledPluginInfo> plugins = Collections.emptyList();
  private boolean scanned;
  private String scanError;

  private final ActivityResultLauncher<String[]> openDocumentLauncher =
      registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onDocumentPicked);

  /** Manifest and decoded icon of one package file, valid while the file is unchanged. */
  private record CachedPackage(long lastModified, long length, GplManifest manifest, Bitmap icon) {}

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    bind = ActivityPluginManagerBinding.inflate(getLayoutInflater());
    setContentView(bind.getRoot());
    setupBackgroundBlur();

    loader = GplPluginLoader.getInstance(this);
    controller = PluginActivationController.get(this);
    scanExecutor = Executors.newSingleThreadExecutor();

    adapter = new InstalledPluginAdapter(new RowActions());
    bind.rvPlugins.setLayoutManager(new LinearLayoutManager(this));
    bind.rvPlugins.setAdapter(adapter);
    ParticleItemAnimator animator = new ParticleItemAnimator(this);
    animator.setSupportsChangeAnimations(false);
    bind.rvPlugins.setItemAnimator(animator);

    bind.btnBack.setOnClickListener(v -> finish());
    bind.fab.setEnableDynamicBackground(true);
    bind.fab.setOnClickListener(v -> openDocumentLauncher.launch(new String[] {"*/*"}));

    bind.searchLayout.setHint(getString(R.string.plugin_manager_search_hint));
    bind.searchLayout.setOnTextChangedListener(this::onSearchTextChanged);
    bind.searchLayout.show();

    bind.masterRow.setOnClickListener(v -> bind.masterSwitch.toggle());
    applyDirection();
    setupInsets();
    M3Theme.applyTopLevel(bind.getRoot());
    styleChrome();
    renderAll();

    controller.addListener(this);
    controller.reconcileAll();
    refresh();
  }

  private void setupBackgroundBlur() {
    setupBackgroundBlur(bind.backgroundIconPlugins, bind.headtop, bind.pluginContent);
    if (Glass.isGlassEnabled(this) && bind.backgroundIconPlugins.getVisibility() == View.VISIBLE) {
      bind.masterCard.setBackdropSource(bind.backgroundIconPlugins);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (scanned) {
      refresh();
    }
  }

  @Override
  protected void onDestroy() {
    controller.removeListener(this);
    scanGeneration.incrementAndGet();
    scanExecutor.shutdownNow();
    if (detailSheet != null) {
      detailSheet.dismiss();
      detailSheet = null;
    }
    bind = null;
    super.onDestroy();
  }

  @Override
  public void onPluginStatesChanged() {
    refresh();
  }

  private void applyDirection() {
    boolean rtl = LocaleDirection.isRtl(this);
    LocaleDirection.mirrorRow(bind.headtop);
    LocaleDirection.mirrorRow(bind.masterRow);
    if (rtl) {
      bind.btnBack.setScaleX(-1f);
      bind.headtop.setPadding(
          bind.headtop.getPaddingRight(),
          bind.headtop.getPaddingTop(),
          bind.headtop.getPaddingLeft(),
          bind.headtop.getPaddingBottom());
      bind.masterRow.setPadding(
          bind.masterRow.getPaddingRight(),
          bind.masterRow.getPaddingTop(),
          bind.masterRow.getPaddingLeft(),
          bind.masterRow.getPaddingBottom());
    }
    int start = LocaleDirection.startGravity(this);
    bind.titleText.setGravity(start);
    bind.subtitleText.setGravity(start);
    bind.masterTitle.setGravity(start);
    bind.masterState.setGravity(start);
    bind.stateBox.setGravity(start);
    bind.stateTitle.setGravity(start);
    bind.emptyText.setGravity(start);
    ((CoordinatorLayout.LayoutParams) bind.fab.getLayoutParams()).gravity =
        Gravity.BOTTOM | (rtl ? Gravity.LEFT : Gravity.RIGHT);
  }

  private void styleChrome() {
    bind.masterIconTile.setBackground(
        InstalledPluginAdapter.rounded(Glass.primaryContainerColor(), Glass.dp(this, 12)));
    bind.masterIcon.setImageTintList(
        ColorStateList.valueOf(
            M3Theme.onPrimaryContainer() != null
                ? M3Theme.onPrimaryContainer()
                : Glass.onSurfaceColor()));
    bind.stateIcon.setImageTintList(ColorStateList.valueOf(Glass.onSurfaceVariantColor()));
    M3Theme.materialSwitch(bind.masterSwitch);
  }

  private void setupInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(
        bind.coordinator,
        (view, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          bind.headtop.setPadding(
              bind.headtop.getPaddingLeft(),
              systemBars.top,
              bind.headtop.getPaddingRight(),
              bind.headtop.getPaddingBottom());
          bind.rvPlugins.setPadding(
              bind.rvPlugins.getPaddingLeft(),
              bind.rvPlugins.getPaddingTop(),
              bind.rvPlugins.getPaddingRight(),
              systemBars.bottom + dp(88));
          bind.fab.post(
              () -> {
                if (bind == null) {
                  return;
                }
                ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) bind.fab.getLayoutParams();
                params.bottomMargin = dp(16) + systemBars.bottom;
                bind.fab.setLayoutParams(params);
              });
          return insets;
        });
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void onSearchTextChanged(String text) {
    adapter.filter(text);
    renderState();
  }

  /** Re-reads the plugins folder off the main thread; stale results are dropped. */
  private void refresh() {
    if (bind == null) {
      return;
    }
    int generation = scanGeneration.incrementAndGet();
    PluginAvailability availability = controller.availability();
    Context appContext = getApplicationContext();
    try {
      scanExecutor.execute(
          () -> {
            if (generation != scanGeneration.get()) {
              return;
            }
            File dir = GplInstalledPlugins.installDir(appContext);
            List<InstalledPluginInfo> result = new ArrayList<>();
            String error = null;
            File[] files = dir.listFiles((d, name) -> name.endsWith(GPL_EXTENSION));
            if (files == null) {
              error = dir.getAbsolutePath();
            } else {
              boolean master = availability.isMasterEnabled();
              for (File file : files) {
                CachedPackage cached = readPackage(file);
                if (cached == null) {
                  continue;
                }
                String id = cached.manifest().id();
                result.add(
                    new InstalledPluginInfo(
                        file,
                        cached.manifest(),
                        loader.isLoaded(id),
                        availability.isPluginEnabled(id),
                        master,
                        controller.isPending(id),
                        controller.failureOf(id),
                        cached.length(),
                        cached.icon()));
              }
              result.sort(
                  (a, b) -> a.manifest().name().compareToIgnoreCase(b.manifest().name()));
            }
            String scanFailure = error;
            runOnUiThread(() -> onScanned(generation, result, scanFailure));
          });
    } catch (RejectedExecutionException e) {
      Log.w(TAG, "Plugin scan skipped: screen is closing", e);
    }
  }

  @Nullable
  private CachedPackage readPackage(File file) {
    String key = file.getAbsolutePath();
    long lastModified = file.lastModified();
    long length = file.length();
    CachedPackage cached = packageCache.get(key);
    if (cached != null && cached.lastModified() == lastModified && cached.length() == length) {
      return cached;
    }
    GplManifest manifest = GplManifestReader.read(file);
    if (manifest == null) {
      Log.w(TAG, "Skipping plugin with unreadable manifest: " + file);
      packageCache.remove(key);
      return null;
    }
    CachedPackage fresh =
        new CachedPackage(lastModified, length, manifest, decodeIcon(file, manifest));
    packageCache.put(key, fresh);
    return fresh;
  }

  @Nullable
  private static Bitmap decodeIcon(File file, GplManifest manifest) {
    byte[] bytes = GplManifestReader.readIconBytes(file, manifest);
    if (bytes == null) {
      return null;
    }
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    int sample = 1;
    while (bounds.outWidth / (sample * 2) >= ICON_MAX_PX
        && bounds.outHeight / (sample * 2) >= ICON_MAX_PX) {
      sample *= 2;
    }
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
  }

  private void onScanned(int generation, List<InstalledPluginInfo> result, String error) {
    if (bind == null || isDestroyed() || generation != scanGeneration.get()) {
      return;
    }
    scanned = true;
    scanError = error;
    plugins = result;
    adapter.submit(result);
    renderAll();
    updateOpenSheet();
  }

  private void renderAll() {
    renderHeader();
    renderState();
  }

  private void renderHeader() {
    int total = plugins.size();
    int active = 0;
    for (InstalledPluginInfo plugin : plugins) {
      if (plugin.loaded()) {
        active++;
      }
    }
    boolean master = controller.availability().isMasterEnabled();
    bind.subtitleText.setText(
        scanned ? getString(R.string.plugin_manager_summary, total, active) : "");
    String state;
    if (!scanned) {
      state =
          getString(master ? R.string.plugin_manager_loading : R.string.plugin_manager_master_off);
    } else if (controller.isMasterPending()) {
      state = getString(R.string.plugin_manager_master_applying);
    } else if (!master) {
      state = getString(R.string.plugin_manager_master_off);
    } else if (total == 0) {
      state = getString(R.string.plugin_manager_master_on_empty);
    } else {
      state = getString(R.string.plugin_manager_master_on, active, total);
    }
    bind.masterState.setText(state);
    bind.masterSwitch.setOnCheckedChangeListener(null);
    bind.masterSwitch.setChecked(master);
    bind.masterSwitch.setOnCheckedChangeListener((button, checked) -> onMasterToggled(checked));
    bind.masterIconTile.setAlpha(master ? 1f : 0.5f);
  }

  private void renderState() {
    if (bind == null) {
      return;
    }
    boolean showBox;
    bind.stateProgress.setVisibility(View.GONE);
    bind.stateIcon.setVisibility(View.VISIBLE);
    bind.emptyText.setVisibility(View.VISIBLE);
    if (!scanned) {
      showBox = true;
      bind.stateProgress.setVisibility(View.VISIBLE);
      bind.stateIcon.setVisibility(View.GONE);
      bind.emptyText.setVisibility(View.GONE);
      bind.stateTitle.setText(R.string.plugin_manager_loading);
    } else if (scanError != null) {
      showBox = true;
      bind.stateIcon.setImageResource(R.drawable.ic_plugin_state_error);
      bind.stateIcon.setImageTintList(ColorStateList.valueOf(Glass.errorColor()));
      bind.stateTitle.setText(R.string.plugin_manager_list_error);
      bind.emptyText.setText(scanError);
    } else if (!adapter.hasPlugins()) {
      showBox = true;
      bind.stateIcon.setImageResource(R.drawable.ic_outline_extension);
      bind.stateIcon.setImageTintList(ColorStateList.valueOf(Glass.onSurfaceVariantColor()));
      bind.stateTitle.setText(R.string.plugin_manager_empty_title);
      bind.emptyText.setText(R.string.plugin_manager_empty);
    } else if (adapter.isEmpty()) {
      showBox = true;
      bind.stateIcon.setImageResource(R.drawable.outline_search);
      bind.stateIcon.setImageTintList(ColorStateList.valueOf(Glass.onSurfaceVariantColor()));
      bind.stateTitle.setText(
          getString(R.string.plugin_manager_search_empty, currentQuery()));
      bind.emptyText.setText(R.string.plugin_manager_search_empty_hint);
    } else {
      showBox = false;
    }
    bind.stateBox.setVisibility(showBox ? View.VISIBLE : View.GONE);
  }

  private String currentQuery() {
    String query = bind.searchLayout.getQuery();
    return query == null ? "" : query.trim();
  }

  private void onMasterToggled(boolean enabled) {
    if (enabled) {
      controller.setMasterEnabled(true);
      renderHeader();
      return;
    }
    List<String> inUse = new ArrayList<>();
    for (InstalledPluginInfo plugin : plugins) {
      if (!plugin.loaded()) {
        continue;
      }
      List<String> uses = PluginUsage.describeUse(this, plugin.manifest().id());
      if (!uses.isEmpty()) {
        inUse.add("• " + plugin.manifest().name() + ": " + String.join(", ", uses));
      }
    }
    if (inUse.isEmpty()) {
      controller.setMasterEnabled(false);
      renderHeader();
      return;
    }
    confirm(
        getString(R.string.plugin_manager_confirm_master_title),
        getString(R.string.plugin_manager_confirm_master_message, String.join("\n", inUse)),
        R.string.plugin_manager_pause,
        () -> {
          controller.setMasterEnabled(false);
          renderHeader();
        },
        this::renderHeader);
  }

  private void onPluginToggled(InstalledPluginInfo plugin, boolean enabled) {
    String id = plugin.manifest().id();
    if (enabled || !plugin.loaded()) {
      controller.setPluginEnabled(id, enabled);
      return;
    }
    List<String> uses = PluginUsage.describeUse(this, id);
    if (uses.isEmpty()) {
      controller.setPluginEnabled(id, false);
      return;
    }
    confirm(
        getString(R.string.plugin_manager_confirm_off_title, plugin.manifest().name()),
        getString(R.string.plugin_manager_confirm_off_message, "• " + String.join("\n• ", uses)),
        R.string.plugin_manager_turn_off,
        () -> controller.setPluginEnabled(id, false),
        () -> {
          adapter.refreshItem(id);
          updateOpenSheet();
        });
  }

  private void onUninstall(InstalledPluginInfo plugin) {
    String id = plugin.manifest().id();
    StringBuilder message =
        new StringBuilder(getString(R.string.plugin_manager_confirm_uninstall_message));
    List<String> uses = plugin.loaded() ? PluginUsage.describeUse(this, id) : List.of();
    if (!uses.isEmpty()) {
      message.append("\n\n• ").append(String.join("\n• ", uses));
    }
    confirm(
        getString(R.string.plugin_manager_confirm_uninstall_title, plugin.manifest().name()),
        message.toString(),
        R.string.plugin_manager_uninstall,
        () -> {
          if (detailSheet != null && id.equals(detailSheet.pluginId())) {
            detailSheet.dismiss();
            detailSheet = null;
          }
          controller.uninstall(
              id,
              plugin.file(),
              (deleted, suspendFailure) -> onUninstalled(plugin, deleted, suspendFailure));
        },
        null);
  }

  private void onUninstalled(
      InstalledPluginInfo plugin,
      boolean deleted,
      @Nullable PluginActivationController.Failure suspendFailure) {
    if (bind == null || isDestroyed()) {
      return;
    }
    String name = plugin.manifest().name();
    if (!deleted) {
      toast(getString(R.string.plugin_manager_uninstall_failed, name), GhostToast.LENGTH_LONG);
    } else if (suspendFailure != null) {
      toast(
          getString(R.string.plugin_manager_uninstall_stop_error, name, suspendFailure.message()),
          GhostToast.LENGTH_LONG);
    } else {
      toast(getString(R.string.plugin_manager_uninstalled_toast, name), GhostToast.LENGTH_SHORT);
    }
    refresh();
  }

  private void showDetails(InstalledPluginInfo plugin) {
    if (detailSheet != null) {
      detailSheet.dismiss();
    }
    InstalledPluginSheet sheet =
        new InstalledPluginSheet(this, plugin, setupActionsOf(plugin), new SheetActions());
    sheet.setOnDismissListener(
        dialog -> {
          if (detailSheet == sheet) {
            detailSheet = null;
          }
        });
    detailSheet = sheet;
    sheet.show();
  }

  private void updateOpenSheet() {
    if (detailSheet == null) {
      return;
    }
    String id = detailSheet.pluginId();
    for (InstalledPluginInfo plugin : plugins) {
      if (plugin.manifest().id().equals(id)) {
        detailSheet.update(plugin, setupActionsOf(plugin));
        return;
      }
    }
    detailSheet.dismiss();
    detailSheet = null;
  }

  /** Setup actions of a running plugin; {@code null} while it is not running. */
  @Nullable
  private List<PluginSetupAction> setupActionsOf(InstalledPluginInfo plugin) {
    LoadedGplPlugin loaded = loader.getLoaded(plugin.manifest().id());
    if (loaded == null) {
      return null;
    }
    try {
      List<PluginSetupAction> actions = loaded.getPlugin().getSetupActions();
      return actions == null ? List.of() : actions;
    } catch (RuntimeException e) {
      Log.e(TAG, "getSetupActions() failed for " + plugin.manifest().id(), e);
      return List.of();
    }
  }

  private void confirm(
      CharSequence title,
      CharSequence message,
      int positive,
      Runnable onConfirm,
      @Nullable Runnable onCancel) {
    boolean[] confirmed = {false};
    new DialogCompat(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(
            positive,
            (dialog, which) -> {
              confirmed[0] = true;
              onConfirm.run();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .setOnDismissListener(
            dialog -> {
              if (!confirmed[0] && onCancel != null && bind != null) {
                onCancel.run();
              }
            })
        .show();
  }

  private void toast(String message, int duration) {
    GhostToast.makeText(this, message, duration).show();
  }

  private void onDocumentPicked(Uri uri) {
    if (uri == null) {
      return;
    }
    String displayName = queryDisplayName(uri);
    if (displayName != null && !displayName.toLowerCase(Locale.ROOT).endsWith(GPL_EXTENSION)) {
      GhostToast.makeText(this, R.string.plugin_manager_not_gpl_file, GhostToast.LENGTH_SHORT).show();
      return;
    }
    ContentResolver resolver = getApplicationContext().getContentResolver();
    File cacheDir = getCacheDir();
    Context appContext = getApplicationContext();
    controller.install(
        () -> placePackage(resolver, uri, cacheDir, GplInstalledPlugins.installDir(appContext)),
        new PluginActivationController.InstallCallback() {
          @Override
          public void onInstalled(
              PluginActivationController.PlacedPlugin placed,
              boolean active,
              @Nullable PluginActivationController.Failure failure) {
            onPackageInstalled(placed, active, failure);
          }

          @Override
          public void onInstallFailed(String message) {
            if (bind == null || isDestroyed()) {
              return;
            }
            toast(getString(R.string.plugin_manager_install_error, message), GhostToast.LENGTH_LONG);
          }
        });
  }

  @NonNull
  private static PluginActivationController.PlacedPlugin placePackage(
      ContentResolver resolver, Uri uri, File cacheDir, File installDir) throws IOException {
    File tempFile = new File(cacheDir, "incoming.gpl");
    try {
      copyUriToFile(resolver, uri, tempFile);
      GplManifest manifest = GplManifestReader.read(tempFile);
      if (manifest == null) {
        throw new IOException("Could not read plugin manifest from " + uri);
      }
      File installedFile = new File(installDir, manifest.id() + GPL_EXTENSION);
      boolean fresh = !installedFile.exists();
      if (!tempFile.renameTo(installedFile)) {
        copyFile(tempFile, installedFile);
      }
      return new PluginActivationController.PlacedPlugin(
          manifest.id(), manifest.name(), installedFile, fresh);
    } finally {
      if (tempFile.exists() && !tempFile.delete()) {
        Log.w(TAG, "Could not delete temporary package " + tempFile);
      }
    }
  }

  private void onPackageInstalled(
      PluginActivationController.PlacedPlugin placed,
      boolean active,
      @Nullable PluginActivationController.Failure failure) {
    if (bind == null || isDestroyed()) {
      return;
    }
    if (failure != null) {
      toast(
          getString(R.string.plugin_manager_installed_failed_start, placed.name(), failure.message()),
          GhostToast.LENGTH_LONG);
    } else if (!active && !controller.availability().isMasterEnabled()) {
      toast(getString(R.string.plugin_manager_installed_paused, placed.name()), GhostToast.LENGTH_LONG);
    } else {
      toast(getString(R.string.plugin_manager_installed_toast, placed.name()), GhostToast.LENGTH_SHORT);
    }
    refresh();
    LoadedGplPlugin loadedPlugin = active ? loader.getLoaded(placed.id()) : null;
    if (loadedPlugin != null) {
      showSetupActionsIfAny(loadedPlugin);
    }
  }

  private void showSetupActionsIfAny(LoadedGplPlugin loadedPlugin) {
    List<PluginSetupAction> actions;
    try {
      actions = loadedPlugin.getPlugin().getSetupActions();
    } catch (RuntimeException e) {
      Log.e(TAG, "getSetupActions() failed for " + loadedPlugin.getDescriptor().getId(), e);
      return;
    }
    if (actions == null || actions.isEmpty()) {
      return;
    }
    StringBuilder message = new StringBuilder();
    for (PluginSetupAction action : actions) {
      message.append(action.label()).append(": ").append(action.command()).append('\n');
    }
    String combinedCommand = combinedCommand(actions);
    new DialogCompat(this)
        .setTitle(loadedPlugin.getDescriptor().getName())
        .setMessage(message.toString().trim())
        .setPositiveButton(
            R.string.plugin_manager_run_setup, (dialog, which) -> runInTerminal(combinedCommand))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private static String combinedCommand(List<PluginSetupAction> actions) {
    StringBuilder combined = new StringBuilder();
    for (PluginSetupAction action : actions) {
      if (combined.length() > 0) {
        combined.append(" && ");
      }
      combined.append(action.command());
    }
    return combined.toString();
  }

  private void runInTerminal(String command) {
    Intent intent = new Intent(this, TerminalActivity.class);
    intent.putExtra(TerminalActivity.EXTRA_COMMAND, command);
    startActivity(intent);
  }

  private String queryDisplayName(Uri uri) {
    try (Cursor cursor =
        getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) {
          return cursor.getString(index);
        }
      }
    } catch (RuntimeException e) {
      Log.w(TAG, "Could not read the display name of " + uri, e);
    }
    return null;
  }

  private static void copyUriToFile(ContentResolver resolver, Uri uri, File destination)
      throws IOException {
    try (InputStream input = resolver.openInputStream(uri);
        FileOutputStream output = new FileOutputStream(destination)) {
      if (input == null) {
        throw new IOException("Could not open " + uri);
      }
      byte[] buffer = new byte[COPY_BUFFER_SIZE];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
    }
  }

  private static void copyFile(File source, File destination) throws IOException {
    try (FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(destination)) {
      byte[] buffer = new byte[COPY_BUFFER_SIZE];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
    }
  }

  private final class RowActions implements InstalledPluginAdapter.Listener {
    @Override
    public void onToggle(InstalledPluginInfo plugin, boolean enabled) {
      onPluginToggled(plugin, enabled);
    }

    @Override
    public void onUninstall(InstalledPluginInfo plugin) {
      PluginManagerActivity.this.onUninstall(plugin);
    }

    @Override
    public void onRetry(InstalledPluginInfo plugin) {
      controller.retry(plugin.manifest().id());
    }

    @Override
    public void onDetails(InstalledPluginInfo plugin) {
      showDetails(plugin);
    }
  }

  private final class SheetActions implements InstalledPluginSheet.Actions {
    @Override
    public void onToggle(InstalledPluginInfo plugin, boolean enabled) {
      onPluginToggled(plugin, enabled);
    }

    @Override
    public void onRetry(InstalledPluginInfo plugin) {
      controller.retry(plugin.manifest().id());
    }

    @Override
    public void onUninstall(InstalledPluginInfo plugin) {
      PluginManagerActivity.this.onUninstall(plugin);
    }

    @Override
    public void onRunSetup(InstalledPluginInfo plugin, List<PluginSetupAction> actions) {
      runInTerminal(combinedCommand(actions));
    }
  }
}
