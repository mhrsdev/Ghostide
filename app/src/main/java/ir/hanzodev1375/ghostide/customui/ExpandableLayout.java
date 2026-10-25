package ir.hanzodev1375.ghostide.customui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.liquidglass.GlassAccessibilityMode;
import com.example.liquidglass.GlassMaterial;
import ir.hanzodev1375.components.animators.AnimationManager;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.GlassCompat;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;

/**
 * Collapsible settings card rendered as a {@link GlassCompat} surface.
 *
 * <p>The class is itself the glass card: {@code GlassCompat} is a {@code LiquidGlassView} (i.e. a
 * {@link android.widget.FrameLayout}), so the header and the section list live inside a vertical
 * container inflated from {@code layout_expandable}. The glass samples a backdrop, so the host must
 * call {@link #setBackdropSource(View)} with the view painted behind this card.
 *
 * <p>The header shows an optional icon tile, the section title, a one-line summary of what the
 * section holds and an expand indicator. The header row is mirrored for right-to-left languages
 * because the app declares {@code supportsRtl="false"}. It is exposed to accessibility services as
 * a heading with an expanded/collapsed state and expand/collapse actions.
 *
 * <p>The section list is a plain {@link RecyclerView} sized to its whole content, so expanding a
 * section shows every row and the surrounding {@code NestedScrollView} stays the only scrolling
 * container. Nested scrolling is disabled on purpose: an inner scroll viewport inside the glass
 * card caused gesture hand-off jank and hid part of the list behind an invisible scroll area.
 *
 * <p>Glass re-captures and re-processes the backdrop behind the whole card whenever anything inside
 * it redraws, which is affordable for the header-only card but not for one that wraps dozens of
 * rows. While expanded the card therefore draws the library's opaque material (a tinted, outlined
 * surface that captures nothing) and goes back to full glass when collapsed. The system
 * reduce-transparency behaviour is kept for the collapsed state.
 *
 * <p>The expanded state is saved with the view hierarchy, so it survives rotation and activity
 * recreation when the card has an id.
 */
public class ExpandableLayout extends GlassCompat {

  private static final int ANIM_DURATION = 200;
  private static final float CORNER_RADIUS_DP = 12f;
  private static final float EXPANDED_ARROW_ROTATION = 180f;

  private LinearLayout header;
  private TextView titleView;
  private TextView summaryView;
  private ImageView iconView;
  private ImageView arrowIcon;
  private RecyclerView recyclerView;
  private GradientDrawable iconTile;
  private CharSequence summary;
  private CharSequence summaryOverride;
  private boolean isExpanded = false;

  public ExpandableLayout(@NonNull Context context) {
    super(context);
    init(context);
  }

  public ExpandableLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public ExpandableLayout(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  private void init(Context context) {
    float density = getResources().getDisplayMetrics().density;
    setCornerRadius(CORNER_RADIUS_DP * density);
    setRefractionHeight(40f * density);
    setBevelWidth(8f * density);
    setDispersionStrength(0.1f);
    setMaterial(GlassMaterial.REGULAR);
    setEnableDynamicBackground(false);
    setEnableSensorHighlight(false);
    setEnableAdaptiveTint(true);

    LayoutInflater.from(context).inflate(R.layout.layout_expandable, this, true);
    header = findViewById(R.id.expandable_header);
    titleView = findViewById(R.id.expandable_title);
    summaryView = findViewById(R.id.expandable_summary);
    iconView = findViewById(R.id.expandable_icon);
    arrowIcon = findViewById(R.id.expandable_arrow);
    recyclerView = findViewById(R.id.expandable_recycler);
    recyclerView.setNestedScrollingEnabled(false);
    recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
    recyclerView.setSaveEnabled(false);
    recyclerView.setVisibility(GONE);

    iconTile = new GradientDrawable();
    iconTile.setShape(GradientDrawable.OVAL);
    iconView.setBackground(iconTile);

    int startGravity = LocaleDirection.startGravity(context);
    titleView.setGravity(startGravity);
    summaryView.setGravity(startGravity);
    LocaleDirection.mirrorRow(header);

    header.setOnClickListener(v -> toggle());
    ViewCompat.setAccessibilityHeading(header, true);
    ViewCompat.setAccessibilityDelegate(header, new HeaderAccessibilityDelegate());
    refresh();
    updateHeaderState();
  }

  /**
   * Re-applies the glass tint and recolours the header from the current {@link M3Theme} tokens.
   * Safe to call during construction, before the header is inflated.
   */
  @Override
  public void refresh() {
    super.refresh();
    if (titleView == null) return;
    M3Theme.textView(titleView);
    M3Theme.textView(summaryView);
    M3Theme.imageView(arrowIcon);
    Integer tile = M3Theme.secondaryContainer();
    Integer onTile = M3Theme.onSecondaryContainer();
    if (tile != null) iconTile.setColor(tile);
    if (onTile != null) iconView.setColorFilter(onTile);
  }

  public void setTitle(String title) {
    titleView.setText(title == null ? null : title.trim());
    updateHeaderState();
  }

  /** @return the title shown in the header, never null. */
  @NonNull
  public CharSequence getTitle() {
    CharSequence text = titleView.getText();
    return text == null ? "" : text;
  }

  /** Sets the short line under the title that tells what the section holds. */
  public void setSummary(@Nullable CharSequence summary) {
    this.summary = summary;
    showSummary();
  }

  /**
   * Temporarily replaces the summary, for example with the number of search matches. Pass {@code
   * null} to show the regular summary again.
   */
  public void setSummaryOverride(@Nullable CharSequence override) {
    if (TextUtils.equals(summaryOverride, override)) return;
    summaryOverride = override;
    showSummary();
  }

  /** Shows {@code iconRes} in a tonal tile at the reading start of the header. */
  public void setIcon(@DrawableRes int iconRes) {
    iconView.setImageResource(iconRes);
    iconView.setVisibility(iconRes == 0 ? GONE : VISIBLE);
    refresh();
  }

  public RecyclerView getRecyclerView() {
    return recyclerView;
  }

  public void toggle() {
    if (isExpanded) collapse();
    else expand();
  }

  public void expand() {
    if (isExpanded) return;
    setExpandedInternal(true, true);
  }

  public void collapse() {
    if (!isExpanded) return;
    setExpandedInternal(false, true);
  }

  public boolean isExpanded() {
    return isExpanded;
  }

  private void setExpandedInternal(boolean expanded, boolean animate) {
    isExpanded = expanded;
    recyclerView.animate().cancel();
    recyclerView.setAlpha(1f);
    recyclerView.setTranslationY(0f);
    recyclerView.setVisibility(expanded ? View.VISIBLE : View.GONE);
    updateGlassMaterial();
    updateHeaderState();

    float rotation = expanded ? EXPANDED_ARROW_ROTATION : 0f;
    arrowIcon.animate().cancel();
    if (!animate || !AnimationManager.getInstance(getContext()).areAnimationsEnabled()) {
      arrowIcon.setRotation(rotation);
      return;
    }
    arrowIcon.animate().rotation(rotation).setDuration(ANIM_DURATION).start();
  }

  private void showSummary() {
    CharSequence shown = summaryOverride != null ? summaryOverride : summary;
    summaryView.setText(shown);
    summaryView.setVisibility(TextUtils.isEmpty(shown) ? GONE : VISIBLE);
    updateHeaderState();
  }

  private void updateHeaderState() {
    if (header == null) return;
    ViewCompat.setStateDescription(
        header, getContext().getString(isExpanded ? R.string.about_expanded : R.string.about_collapsed));
    CharSequence shown = summaryView.getVisibility() == VISIBLE ? summaryView.getText() : null;
    header.setContentDescription(
        TextUtils.isEmpty(shown) ? getTitle() : TextUtils.concat(getTitle(), ", ", shown));
  }

  private void updateGlassMaterial() {
    setAccessibilityMode(
        isExpanded ? GlassAccessibilityMode.FORCE_OPAQUE : GlassAccessibilityMode.AUTO);
  }

  @Nullable
  @Override
  protected Parcelable onSaveInstanceState() {
    SavedState state = new SavedState(super.onSaveInstanceState());
    state.expanded = isExpanded;
    return state;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (!(state instanceof SavedState)) {
      super.onRestoreInstanceState(state);
      return;
    }
    SavedState saved = (SavedState) state;
    super.onRestoreInstanceState(saved.getSuperState());
    if (saved.expanded != isExpanded) setExpandedInternal(saved.expanded, false);
  }

  private final class HeaderAccessibilityDelegate extends AccessibilityDelegateCompat {

    @Override
    public void onInitializeAccessibilityNodeInfo(
        @NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
      super.onInitializeAccessibilityNodeInfo(host, info);
      info.setClassName(Button.class.getName());
      info.addAction(isExpanded ? AccessibilityActionCompat.ACTION_COLLAPSE : AccessibilityActionCompat.ACTION_EXPAND);
    }

    @Override
    public boolean performAccessibilityAction(
        @NonNull View host, int action, @Nullable Bundle args) {
      if (action == AccessibilityNodeInfoCompat.ACTION_EXPAND && !isExpanded) {
        expand();
        return true;
      }
      if (action == AccessibilityNodeInfoCompat.ACTION_COLLAPSE && isExpanded) {
        collapse();
        return true;
      }
      return super.performAccessibilityAction(host, action, args);
    }
  }

  /** Hierarchy state of the card: whether its section list is open. */
  static final class SavedState extends BaseSavedState {

    boolean expanded;

    SavedState(Parcelable superState) {
      super(superState);
    }

    private SavedState(Parcel in) {
      super(in);
      expanded = in.readInt() != 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeInt(expanded ? 1 : 0);
    }

    public static final Parcelable.Creator<SavedState> CREATOR =
        new Parcelable.Creator<>() {
          @Override
          public SavedState createFromParcel(Parcel in) {
            return new SavedState(in);
          }

          @Override
          public SavedState[] newArray(int size) {
            return new SavedState[size];
          }
        };
  }
}
