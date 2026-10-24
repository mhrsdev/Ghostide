package ir.hanzodev1375.ghostide.customui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.example.liquidglass.GlassAccessibilityMode;
import com.example.liquidglass.GlassMaterial;
import ir.hanzodev1375.components.animators.AnimationManager;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.GlassCompat;
import ir.hanzodev1375.ghostide.R;
import ir.theme.M3Theme;

/**
 * Collapsible settings card rendered as a {@link GlassCompat} surface.
 *
 * <p>The class is itself the glass card: {@code GlassCompat} is a {@code LiquidGlassView} (i.e. a
 * {@link android.widget.FrameLayout}), so the header and the section list live inside a vertical
 * container inflated from {@code layout_expandable}. The glass samples a backdrop, so the host must
 * call {@link #setBackdropSource(View)} with the view painted behind this card.
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
 */
public class ExpandableLayout extends GlassCompat {

  private static final int ANIM_DURATION = 200;
  private static final float CORNER_RADIUS_DP = 12f;

  private TextView titleView;
  private ImageView arrowIcon;
  private RecyclerView recyclerView;
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
    refresh();

    LayoutInflater.from(context).inflate(R.layout.layout_expandable, this, true);
    titleView = findViewById(R.id.expandable_title);
    arrowIcon = findViewById(R.id.expandable_arrow);
    recyclerView = findViewById(R.id.expandable_recycler);
    recyclerView.setNestedScrollingEnabled(false);
    recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
    findViewById(R.id.expandable_header).setOnClickListener(v -> toggle());
    recyclerView.setVisibility(GONE);

    M3Theme.textView(titleView);
    M3Theme.imageView(arrowIcon);
  }

  public void setTitle(String title) {
    titleView.setText(title);
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
    isExpanded = true;
    recyclerView.animate().cancel();
    recyclerView.setAlpha(1f);
    recyclerView.setTranslationY(0f);
    recyclerView.setVisibility(View.VISIBLE);
    updateGlassMaterial();

    if (!AnimationManager.getInstance(getContext()).areAnimationsEnabled()) {
      arrowIcon.setRotation(90f);
      return;
    }

    arrowIcon.animate().rotation(90f).setDuration(ANIM_DURATION).start();
  }

  public void collapse() {
    if (!isExpanded) return;
    isExpanded = false;
    recyclerView.animate().cancel();
    recyclerView.setAlpha(1f);
    recyclerView.setTranslationY(0f);
    recyclerView.setVisibility(View.GONE);
    updateGlassMaterial();

    if (!AnimationManager.getInstance(getContext()).areAnimationsEnabled()) {
      arrowIcon.setRotation(0f);
      return;
    }

    arrowIcon.animate().rotation(0f).setDuration(ANIM_DURATION).start();
  }

  public boolean isExpanded() {
    return isExpanded;
  }

  private void updateGlassMaterial() {
    setAccessibilityMode(
        isExpanded ? GlassAccessibilityMode.FORCE_OPAQUE : GlassAccessibilityMode.AUTO);
  }
}
