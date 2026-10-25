package ir.hanzodev1375.ghostide.helper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.components.sheet.BaseSheet;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.adapters.InstalledPluginAdapter;
import ir.hanzodev1375.ghostide.databinding.SheetInstalledPluginBinding;
import ir.hanzodev1375.ghostide.models.InstalledPluginInfo;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;
import ir.hanzodev1375.ghostide.utils.InfoRows;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;
import java.util.List;

/**
 * Details of one installed plugin: identity, package, state with the last error, setup actions and
 * what the package format can say about capabilities. The switch, retry, setup and uninstall
 * buttons only report to {@link Actions}; the host screen owns every state change and pushes the
 * new snapshot back through {@link #update}.
 */
public final class InstalledPluginSheet extends BaseSheet {

  /** What the user asked for from the sheet. */
  public interface Actions {
    void onToggle(InstalledPluginInfo plugin, boolean enabled);

    void onRetry(InstalledPluginInfo plugin);

    void onUninstall(InstalledPluginInfo plugin);

    void onRunSetup(InstalledPluginInfo plugin, List<PluginSetupAction> actions);
  }

  private final SheetInstalledPluginBinding binding;
  private final Actions actions;
  private InstalledPluginInfo plugin;

  public InstalledPluginSheet(
      @NonNull Context context,
      @NonNull InstalledPluginInfo plugin,
      @Nullable List<PluginSetupAction> setupActions,
      @NonNull Actions actions) {
    super(context);
    this.actions = actions;
    binding = SheetInstalledPluginBinding.inflate(LayoutInflater.from(context));
    setContentView(binding.getRoot());
    setHasPeekMod(false);
    applyDirection(context);
    M3Theme.apply(binding.getRoot());
    update(plugin, setupActions);
  }

  /** Manifest id of the plugin shown. */
  public String pluginId() {
    return plugin.manifest().id();
  }

  /** Shows a newer snapshot of the same plugin. */
  public void update(@NonNull InstalledPluginInfo next, @Nullable List<PluginSetupAction> setup) {
    plugin = next;
    Context context = getContext();
    binding.sheetName.setText(next.manifest().name());
    String description = next.manifest().description();
    binding.sheetDescription.setText(description);
    binding.sheetDescription.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
    bindIcon();
    bindState(context);
    bindInfo(context);
    bindSetup(context, setup);
    int errorContainer =
        M3Theme.errorContainer() != null ? M3Theme.errorContainer() : Glass.errorColor();
    int onErrorContainer =
        M3Theme.onErrorContainer() != null ? M3Theme.onErrorContainer() : Glass.onSurfaceColor();
    binding.sheetUninstall.setBackgroundTintList(ColorStateList.valueOf(errorContainer));
    binding.sheetUninstall.setTextColor(onErrorContainer);
    binding.sheetUninstall.setOnClickListener(v -> actions.onUninstall(plugin));
  }

  private void applyDirection(Context context) {
    LocaleDirection.mirrorRow(binding.sheetHeader);
    LocaleDirection.mirrorRow(binding.sheetStateRow);
    LocaleDirection.mirrorRow(binding.sheetActions);
    int start = LocaleDirection.startGravity(context);
    binding.sheetName.setGravity(start);
    binding.sheetStateText.setGravity(start | Gravity.CENTER_VERTICAL);
    binding.sheetDescription.setGravity(start);
    binding.sheetPausedNote.setGravity(start);
    binding.sheetErrorTitle.setGravity(start);
    binding.sheetErrorText.setGravity(start);
    binding.sheetSetupTitle.setGravity(start);
    binding.sheetSetupText.setGravity(start);
    binding.sheetPermissionsTitle.setGravity(start);
    binding.sheetPermissionsText.setGravity(start);
    binding.sheetErrorBox.setGravity(start);
    float radius = Glass.dp(context, 14);
    binding.sheetIconTile.setBackground(
        InstalledPluginAdapter.rounded(
            M3Theme.surfaceContainerHighest() != null
                ? M3Theme.surfaceContainerHighest()
                : Glass.surfaceHighColor(),
            radius));
    binding.sheetPausedNote.setBackground(
        InstalledPluginAdapter.rounded(Glass.surfaceHighColor(), Glass.dp(context, 12)));
    int errorContainer =
        M3Theme.errorContainer() != null ? M3Theme.errorContainer() : Glass.errorColor();
    binding.sheetErrorBox.setBackground(
        InstalledPluginAdapter.rounded(
            ColorUtils.setAlphaComponent(errorContainer, 0xCC), Glass.dp(context, 12)));
  }

  private void bindIcon() {
    if (plugin.icon() != null) {
      binding.sheetIcon.setImageTintList(null);
      binding.sheetIcon.setImageBitmap(plugin.icon());
    } else {
      binding.sheetIcon.setImageResource(R.drawable.ic_outline_extension);
      binding.sheetIcon.setImageTintList(ColorStateList.valueOf(Glass.onSurfaceVariantColor()));
    }
  }

  private void bindState(Context context) {
    InstalledPluginInfo.State state = plugin.state();
    int color = InstalledPluginAdapter.stateColor(state);
    binding.sheetStateText.setText(InstalledPluginAdapter.stateLabel(state));
    binding.sheetStateText.setTextColor(color);
    binding.sheetStateIcon.setImageResource(InstalledPluginAdapter.stateIcon(state));
    binding.sheetStateIcon.setImageTintList(ColorStateList.valueOf(color));

    binding.sheetSwitch.setOnCheckedChangeListener(null);
    binding.sheetSwitch.setChecked(plugin.enabled());
    binding.sheetSwitch.setAlpha(plugin.masterEnabled() ? 1f : 0.55f);
    binding.sheetSwitch.setContentDescription(
        context.getString(R.string.plugin_manager_switch_desc, plugin.manifest().name()));
    binding.sheetSwitch.setOnCheckedChangeListener(
        (button, checked) -> actions.onToggle(plugin, checked));

    binding.sheetPausedNote.setVisibility(plugin.masterEnabled() ? View.GONE : View.VISIBLE);

    boolean failed =
        plugin.failure() != null
            && (state == InstalledPluginInfo.State.FAILED_START
                || state == InstalledPluginInfo.State.FAILED_STOP);
    binding.sheetErrorBox.setVisibility(failed ? View.VISIBLE : View.GONE);
    if (failed) {
      int onError =
          M3Theme.onErrorContainer() != null ? M3Theme.onErrorContainer() : Glass.onSurfaceColor();
      binding.sheetErrorTitle.setTextColor(onError);
      binding.sheetErrorText.setTextColor(onError);
      binding.sheetRetry.setTextColor(onError);
      binding.sheetErrorText.setText(plugin.failure().message());
      binding.sheetRetry.setText(
          state == InstalledPluginInfo.State.FAILED_START
              ? R.string.plugin_manager_retry
              : R.string.plugin_manager_dismiss);
      binding.sheetRetry.setOnClickListener(v -> actions.onRetry(plugin));
    }
  }

  private void bindInfo(Context context) {
    binding.sheetInfo.removeAllViews();
    InfoRows.add(binding.sheetInfo, R.string.plugin_detail_id, plugin.manifest().id());
    InfoRows.add(binding.sheetInfo, R.string.plugin_detail_version, plugin.manifest().version());
    InfoRows.add(
        binding.sheetInfo,
        R.string.plugin_detail_package,
        plugin.file().getName());
    InfoRows.add(
        binding.sheetInfo,
        R.string.plugin_detail_size,
        Formatter.formatShortFileSize(context, plugin.sizeBytes()));
    int minHost = plugin.manifest().minHostVersion();
    InfoRows.add(
        binding.sheetInfo,
        R.string.plugin_detail_min_host,
        minHost > 0 ? String.valueOf(minHost) : context.getString(R.string.plugin_detail_min_host_any));
    M3Theme.apply(binding.sheetInfo);
  }

  private void bindSetup(Context context, @Nullable List<PluginSetupAction> setup) {
    binding.sheetRunSetup.setVisibility(View.GONE);
    binding.sheetRunSetup.setOnClickListener(null);
    if (setup == null) {
      binding.sheetSetupText.setText(R.string.plugin_detail_setup_inactive);
      binding.sheetSetupText.setAlpha(0.8f);
      return;
    }
    if (setup.isEmpty()) {
      binding.sheetSetupText.setText(R.string.plugin_detail_setup_none);
      binding.sheetSetupText.setAlpha(0.8f);
      return;
    }
    StringBuilder text = new StringBuilder();
    for (PluginSetupAction action : setup) {
      if (text.length() > 0) {
        text.append("\n\n");
      }
      text.append(action.label()).append('\n').append(action.command());
    }
    binding.sheetSetupText.setText(text);
    binding.sheetSetupText.setAlpha(1f);
    binding.sheetRunSetup.setVisibility(View.VISIBLE);
    binding.sheetRunSetup.setOnClickListener(v -> actions.onRunSetup(plugin, setup));
  }
}
