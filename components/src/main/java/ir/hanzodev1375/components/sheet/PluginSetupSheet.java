package ir.hanzodev1375.components.sheet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import com.bumptech.glide.Glide;
import ir.hanzodev1375.components.R;
import ir.hanzodev1375.components.databinding.SheetPluginSetupBinding;
import ir.hanzodev1375.components.store.model.PluginSetupActionData;
import ir.hanzodev1375.components.utils.LabelEllipsizer;
import ir.theme.M3Theme;
import java.util.List;

public class PluginSetupSheet extends BaseSheet {

  public interface OnRunListener {
    void onRun(String combinedCommand);
  }

  private static final int TITLE_MAX_GRAPHEMES = 28;

  private final SheetPluginSetupBinding binding;
  private final List<PluginSetupActionData> actions;
  private final OnRunListener runListener;

  public PluginSetupSheet(
      Context context,
      String name,
      String iconUrl,
      List<PluginSetupActionData> actions,
      OnRunListener runListener) {
    super(context);
    this.actions = actions != null ? actions : List.of();
    this.runListener = runListener;
    binding = SheetPluginSetupBinding.inflate(LayoutInflater.from(context));
    setContentView(binding.getRoot());
    setHasPeekMod(false);
    build(name, iconUrl);
    M3Theme.applyTopLevel(binding.getRoot());
  }

  private void build(String name, String iconUrl) {
    String fullName = name != null ? name : "";
    binding.pluginSetupName.setText(LabelEllipsizer.ellipsize(fullName, TITLE_MAX_GRAPHEMES));
    binding.pluginSetupName.setContentDescription(fullName);
    binding.pluginSetupName.setTooltipText(fullName.isEmpty() ? null : fullName);
    renderDescription();
    renderCommand();
    renderIcon(iconUrl);
    M3Theme.text(binding.pluginSetupName, binding.pluginSetupDesc, binding.pluginSetupCommand);
    M3Theme.card(binding.pluginSetupCard);
    M3Theme.button(binding.pluginSetupRun, binding.pluginSetupCancel);
    binding.pluginSetupRun.setOnClickListener(this::onRunClick);
    binding.pluginSetupCancel.setOnClickListener(v -> dismiss());
  }

  /**
   * Shows the action labels as one short line: each label is limited to {@link
   * LabelEllipsizer#DEFAULT_MAX_GRAPHEMES} visible characters and isolated so mixed Persian and
   * English labels keep their own direction. The full labels stay in the content description, the
   * long-press tooltip and the command card below; the executed command is built separately.
   */
  private void renderDescription() {
    StringBuilder display = new StringBuilder();
    StringBuilder full = new StringBuilder();
    for (PluginSetupActionData action : actions) {
      if (action.label().isEmpty()) continue;
      if (display.length() > 0) {
        display.append(" · ");
        full.append(", ");
      }
      display.append(
          LabelEllipsizer.isolate(
              LabelEllipsizer.ellipsize(action.label(), LabelEllipsizer.DEFAULT_MAX_GRAPHEMES)));
      full.append(action.label());
    }
    boolean hasLabels = display.length() > 0;
    binding.pluginSetupDesc.setText(display.toString());
    binding.pluginSetupDesc.setContentDescription(hasLabels ? full.toString() : null);
    binding.pluginSetupDesc.setTooltipText(hasLabels ? full.toString() : null);
    binding.pluginSetupDesc.setVisibility(hasLabels ? View.VISIBLE : View.GONE);
  }

  private void renderCommand() {
    StringBuilder command = new StringBuilder();
    for (PluginSetupActionData action : actions) {
      if (command.length() > 0) command.append('\n').append('\n');
      if (!action.label().isEmpty()) command.append(action.label()).append('\n');
      if (!action.description().isEmpty()) command.append(action.description()).append('\n');
      command.append(action.command());
    }
    binding.pluginSetupCommand.setText(command.toString());
  }

  private void renderIcon(String iconUrl) {
    if (iconUrl == null || iconUrl.isEmpty()) {
      binding.pluginSetupIcon.setImageResource(R.drawable.ic_outline_extension);
      return;
    }
    Glide.with(binding.pluginSetupIcon)
        .load(iconUrl)
        .placeholder(R.drawable.ic_outline_extension)
        .error(R.drawable.ic_outline_extension)
        .into(binding.pluginSetupIcon);
  }

  private void onRunClick(View view) {
    StringBuilder combinedCommand = new StringBuilder();
    for (PluginSetupActionData action : actions) {
      if (combinedCommand.length() > 0) combinedCommand.append(" && ");
      combinedCommand.append(action.command());
    }
    if (runListener != null && combinedCommand.length() > 0) {
      runListener.onRun(combinedCommand.toString());
    }
    dismiss();
  }
}