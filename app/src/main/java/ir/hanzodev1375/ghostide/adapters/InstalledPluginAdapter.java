package ir.hanzodev1375.ghostide.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.materialswitch.MaterialSwitch;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.models.InstalledPluginInfo;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Shows the installed plugins with their state, a per-plugin switch and uninstall, and filters them
 * by name or id. Rows are diffed by manifest id so an uninstall animates only the removed row and
 * a state change rebinds a row in place.
 */
public final class InstalledPluginAdapter
    extends RecyclerView.Adapter<InstalledPluginAdapter.ViewHolder> {

  /** User actions on a row. */
  public interface Listener {
    void onToggle(InstalledPluginInfo plugin, boolean enabled);

    void onUninstall(InstalledPluginInfo plugin);

    void onRetry(InstalledPluginInfo plugin);

    void onDetails(InstalledPluginInfo plugin);
  }

  private static final Object PAYLOAD_STATE = new Object();
  private static final int ROW_INDENT_DP = 56;

  private final List<InstalledPluginInfo> allPlugins = new ArrayList<>();
  private final List<InstalledPluginInfo> visiblePlugins = new ArrayList<>();
  private final Listener listener;
  private String query = "";

  public InstalledPluginAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<InstalledPluginInfo> plugins) {
    allPlugins.clear();
    allPlugins.addAll(plugins);
    applyFilter();
  }

  public void filter(String query) {
    this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    applyFilter();
  }

  /** The current, trimmed, lower-case query; empty when not searching. */
  public String query() {
    return query;
  }

  /** Rebinds the row of {@code pluginId}, e.g. to undo a switch flip the user cancelled. */
  public void refreshItem(String pluginId) {
    for (int i = 0; i < visiblePlugins.size(); i++) {
      if (visiblePlugins.get(i).manifest().id().equals(pluginId)) {
        notifyItemChanged(i, PAYLOAD_STATE);
        return;
      }
    }
  }

  private void applyFilter() {
    List<InstalledPluginInfo> next = new ArrayList<>();
    if (query.isEmpty()) {
      next.addAll(allPlugins);
    } else {
      for (InstalledPluginInfo plugin : allPlugins) {
        if (plugin.manifest().name().toLowerCase(Locale.ROOT).contains(query)
            || plugin.manifest().id().toLowerCase(Locale.ROOT).contains(query)) {
          next.add(plugin);
        }
      }
    }
    List<InstalledPluginInfo> previous = new ArrayList<>(visiblePlugins);
    DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RowDiff(previous, next));
    visiblePlugins.clear();
    visiblePlugins.addAll(next);
    diff.dispatchUpdatesTo(this);
  }

  public boolean isEmpty() {
    return visiblePlugins.isEmpty();
  }

  /** Whether any plugin is installed, regardless of the search query. */
  public boolean hasPlugins() {
    return !allPlugins.isEmpty();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_installed_plugin, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    InstalledPluginInfo plugin = visiblePlugins.get(position);
    M3Theme.listCard(holder.itemView);
    holder.applySurfaces();
    holder.name.setText(plugin.manifest().name());
    holder.version.setText(plugin.manifest().version());
    String description = plugin.manifest().description();
    holder.description.setText(description);
    holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
    if (plugin.icon() != null) {
      holder.icon.setImageTintList(null);
      holder.icon.setImageBitmap(plugin.icon());
    } else {
      holder.icon.setImageResource(R.drawable.ic_outline_extension);
      holder.icon.setImageTintList(ColorStateList.valueOf(onSurfaceVariant()));
    }
    bindState(holder, plugin);
  }

  @Override
  public void onBindViewHolder(
      @NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
      return;
    }
    bindState(holder, visiblePlugins.get(position));
  }

  private void bindState(ViewHolder holder, InstalledPluginInfo plugin) {
    Context context = holder.itemView.getContext();
    InstalledPluginInfo.State state = plugin.state();
    int stateColor = stateColor(state);
    holder.stateText.setText(stateLabel(state));
    holder.stateText.setTextColor(stateColor);
    holder.stateIcon.setImageResource(stateIcon(state));
    holder.stateIcon.setImageTintList(ColorStateList.valueOf(stateColor));

    holder.toggle.setOnCheckedChangeListener(null);
    holder.toggle.setChecked(plugin.enabled());
    holder.toggle.setAlpha(plugin.masterEnabled() ? 1f : 0.55f);
    holder.toggle.setContentDescription(
        context.getString(R.string.plugin_manager_switch_desc, plugin.manifest().name()));
    holder.toggle.setOnCheckedChangeListener((button, checked) -> listener.onToggle(plugin, checked));

    boolean dimmed =
        state == InstalledPluginInfo.State.OFF || state == InstalledPluginInfo.State.PAUSED;
    holder.iconTile.setAlpha(dimmed ? 0.55f : 1f);
    holder.name.setAlpha(dimmed ? 0.75f : 1f);

    boolean failed =
        state == InstalledPluginInfo.State.FAILED_START
            || state == InstalledPluginInfo.State.FAILED_STOP;
    holder.errorRow.setVisibility(failed ? View.VISIBLE : View.GONE);
    if (failed && plugin.failure() != null) {
      holder.errorText.setText(plugin.failure().message());
      holder.errorText.setTextColor(onErrorContainer());
      holder.retry.setText(
          state == InstalledPluginInfo.State.FAILED_START
              ? R.string.plugin_manager_retry
              : R.string.plugin_manager_dismiss);
      holder.retry.setTextColor(onErrorContainer());
      holder.retry.setOnClickListener(v -> listener.onRetry(plugin));
    } else {
      holder.retry.setOnClickListener(null);
    }

    holder.uninstall.setOnClickListener(v -> listener.onUninstall(plugin));
    holder.itemView.setOnClickListener(v -> listener.onDetails(plugin));
  }

  @Override
  public int getItemCount() {
    return visiblePlugins.size();
  }

  /** Short state word shown next to the state icon. */
  @StringRes
  public static int stateLabel(InstalledPluginInfo.State state) {
    switch (state) {
      case ACTIVE:
        return R.string.plugin_state_active;
      case PAUSED:
        return R.string.plugin_state_paused;
      case WAITING:
        return R.string.plugin_state_waiting;
      case STARTING:
        return R.string.plugin_state_starting;
      case STOPPING:
        return R.string.plugin_state_stopping;
      case FAILED_START:
        return R.string.plugin_state_failed_start;
      case FAILED_STOP:
        return R.string.plugin_state_failed_stop;
      case OFF:
      default:
        return R.string.plugin_state_off;
    }
  }

  /** State icon; each state has its own shape so it does not rely on colour alone. */
  @DrawableRes
  public static int stateIcon(InstalledPluginInfo.State state) {
    switch (state) {
      case ACTIVE:
        return R.drawable.ic_plugin_state_active;
      case PAUSED:
        return R.drawable.ic_plugin_state_paused;
      case FAILED_START:
      case FAILED_STOP:
        return R.drawable.ic_plugin_state_error;
      case OFF:
      case WAITING:
      case STARTING:
      case STOPPING:
      default:
        return R.drawable.ic_plugin_state_off;
    }
  }

  @ColorInt
  public static int stateColor(InstalledPluginInfo.State state) {
    switch (state) {
      case ACTIVE:
      case STARTING:
        return Glass.primaryColor();
      case FAILED_START:
      case FAILED_STOP:
        return Glass.errorColor();
      case PAUSED:
        return tone(M3Theme.tertiary(), Glass.onSurfaceVariantColor());
      case OFF:
      case WAITING:
      case STOPPING:
      default:
        return Glass.onSurfaceVariantColor();
    }
  }

  @ColorInt
  static int tone(@Nullable Integer value, @ColorInt int fallback) {
    return value != null ? value : fallback;
  }

  @ColorInt
  private static int onSurfaceVariant() {
    return Glass.onSurfaceVariantColor();
  }

  @ColorInt
  private static int onErrorContainer() {
    return tone(M3Theme.onErrorContainer(), Glass.onSurfaceColor());
  }

  /** Rounded rectangle in {@code color}, used for the small tonal parts of a row. */
  public static Drawable rounded(@ColorInt int color, float radiusPx) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(color);
    shape.setCornerRadius(radiusPx);
    return shape;
  }

  private static final class RowDiff extends DiffUtil.Callback {
    private final List<InstalledPluginInfo> oldList;
    private final List<InstalledPluginInfo> newList;

    RowDiff(List<InstalledPluginInfo> oldList, List<InstalledPluginInfo> newList) {
      this.oldList = oldList;
      this.newList = newList;
    }

    @Override
    public int getOldListSize() {
      return oldList.size();
    }

    @Override
    public int getNewListSize() {
      return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldPosition, int newPosition) {
      return oldList
          .get(oldPosition)
          .manifest()
          .id()
          .equals(newList.get(newPosition).manifest().id());
    }

    @Override
    public boolean areContentsTheSame(int oldPosition, int newPosition) {
      return oldList.get(oldPosition).equals(newList.get(newPosition));
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldPosition, int newPosition) {
      InstalledPluginInfo before = oldList.get(oldPosition);
      InstalledPluginInfo after = newList.get(newPosition);
      boolean sameContent =
          before.manifest().equals(after.manifest()) && Objects.equals(before.icon(), after.icon());
      return sameContent ? PAYLOAD_STATE : null;
    }
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {

    final View iconTile;
    final ImageView icon;
    final TextView name;
    final TextView version;
    final TextView description;
    final ImageView stateIcon;
    final TextView stateText;
    final MaterialSwitch toggle;
    final ImageButton uninstall;
    final View errorRow;
    final TextView errorText;
    final TextView retry;
    private int surfaceKey;

    ViewHolder(@NonNull View itemView) {
      super(itemView);
      Context context = itemView.getContext();
      iconTile = itemView.findViewById(R.id.pluginIconTile);
      icon = itemView.findViewById(R.id.pluginIcon);
      name = itemView.findViewById(R.id.pluginName);
      version = itemView.findViewById(R.id.pluginVersion);
      description = itemView.findViewById(R.id.pluginDescription);
      stateIcon = itemView.findViewById(R.id.pluginStateIcon);
      stateText = itemView.findViewById(R.id.pluginStateText);
      toggle = itemView.findViewById(R.id.pluginSwitch);
      uninstall = itemView.findViewById(R.id.btnUninstall);
      errorRow = itemView.findViewById(R.id.pluginErrorRow);
      errorText = itemView.findViewById(R.id.pluginErrorText);
      retry = itemView.findViewById(R.id.pluginRetry);
      applyDirection(context);
    }

    private void applyDirection(Context context) {
      boolean rtl = LocaleDirection.isRtl(context);
      LocaleDirection.mirrorRow(itemView.findViewById(R.id.pluginTopRow));
      LocaleDirection.mirrorRow(itemView.findViewById(R.id.pluginMetaRow));
      LocaleDirection.mirrorRow((LinearLayout) errorRow);
      int indent = Glass.dp(context, ROW_INDENT_DP);
      int start = itemView.getPaddingLeft();
      int end = itemView.getPaddingRight();
      if (rtl) {
        itemView.setPadding(end, itemView.getPaddingTop(), start, itemView.getPaddingBottom());
      }
      View meta = itemView.findViewById(R.id.pluginMetaRow);
      meta.setPadding(rtl ? 0 : indent, 0, rtl ? indent : 0, 0);
      ViewGroup.MarginLayoutParams errorParams =
          (ViewGroup.MarginLayoutParams) errorRow.getLayoutParams();
      int errorEnd = errorParams.rightMargin;
      errorParams.leftMargin = rtl ? errorEnd : indent;
      errorParams.rightMargin = rtl ? indent : errorEnd;
      errorParams.setMarginStart(errorParams.leftMargin);
      errorParams.setMarginEnd(errorParams.rightMargin);
      errorRow.setLayoutParams(errorParams);
      int textGravity = LocaleDirection.startGravity(context) | Gravity.TOP;
      name.setGravity(textGravity);
      description.setGravity(textGravity);
      errorText.setGravity(LocaleDirection.startGravity(context) | Gravity.CENTER_VERTICAL);
      stateText.setGravity(LocaleDirection.startGravity(context) | Gravity.CENTER_VERTICAL);
    }

    /** (Re)builds the row backgrounds when the theme surface changed since the last bind. */
    void applySurfaces() {
      int surface = Glass.surfaceColor();
      boolean imageMode = M3Theme.isBackgroundImageMode();
      int key = surface ^ (imageMode ? 1 : 0);
      if (key == surfaceKey && itemView.getBackground() != null) {
        return;
      }
      surfaceKey = key;
      Context context = itemView.getContext();
      float radius = Glass.dp(context, 18);
      int cardColor = tone(M3Theme.surfaceContainerLow(), surface);
      GradientDrawable card = new GradientDrawable();
      card.setCornerRadius(radius);
      card.setColor(imageMode ? ColorUtils.setAlphaComponent(cardColor, 200) : cardColor);
      card.setStroke(
          Glass.dp(context, 1),
          ColorUtils.setAlphaComponent(
              tone(M3Theme.outlineVariant(), Glass.outlineColor()), imageMode ? 140 : 255));
      GradientDrawable mask = new GradientDrawable();
      mask.setCornerRadius(radius);
      mask.setColor(Glass.onSurfaceColor());
      int ripple = ColorUtils.setAlphaComponent(Glass.onSurfaceColor(), 0x1F);
      itemView.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), card, mask));
      iconTile.setBackground(
          rounded(tone(M3Theme.surfaceContainerHighest(), Glass.surfaceHighColor()), Glass.dp(context, 12)));
      version.setBackground(
          rounded(tone(M3Theme.surfaceContainerHigh(), Glass.surfaceHighColor()), Glass.dp(context, 6)));
      errorRow.setBackground(
          rounded(
              ColorUtils.setAlphaComponent(tone(M3Theme.errorContainer(), Glass.errorColor()), 0xCC),
              Glass.dp(context, 12)));
      uninstall.setImageTintList(ColorStateList.valueOf(Glass.errorColor()));
    }
  }
}
