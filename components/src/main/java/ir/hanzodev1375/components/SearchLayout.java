package ir.hanzodev1375.components;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.example.liquidglass.GlassMaterial;
import com.google.android.material.transition.platform.MaterialSharedAxis;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.GlassCompat;
import ir.hanzodev1375.components.utils.ComponentsPrefs;
import ir.theme.M3Theme;

@MainThread
public class SearchLayout extends FrameLayout {

  private EditText editText;
  private ImageButton clearButton;
  private ImageView searchIcon;
  private OnSearchListener onSearchListener;
  private OnTextChangedListener onTextChangedListener;
  ComponentsPrefs setting;
  private static final float GLASS_CORNER_DP = 26f;
  @Nullable private SearchGlass glass;
  @Nullable private GradientDrawable backdropFill;
  private boolean backdropListenerRegistered;
  private boolean themeRefreshPending;
  private final ViewTreeObserver.OnGlobalLayoutListener backdropLayoutListener =
      () -> {
        if (updateBackdrop()) {
          removeBackdropListener();
        }
      };

  public SearchLayout(@NonNull Context context) {
    super(context);
    init(context);
  }

  public SearchLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    setting = new ComponentsPrefs(context);
    LayoutInflater.from(context).inflate(R.layout.search_layout, this, true);

    editText = findViewById(R.id.etSearch);
    clearButton = findViewById(R.id.btnClear);
    searchIcon = findViewById(R.id.ivSearchIcon);
    clearButton.setVisibility(View.INVISIBLE);
    clearButton.setAlpha(0f);

    setVisibility(GONE);
    setupListeners();
    applyGlassBackground();
    M3Theme.apply(this);
  }

  private void applyGlassBackground() {
    Activity activity = resolveActivity(getContext());
    if (activity == null) return;

    SearchGlass glass = new SearchGlass(activity);
    float density = getResources().getDisplayMetrics().density;
    glass.setCornerRadius(GLASS_CORNER_DP * density);
    glass.setRefractionHeight(48f);
    glass.setBevelWidth(8f);
    glass.setMaterial(GlassMaterial.REGULAR);
    glass.setDispersionStrength(0.1f);
    glass.setEnableDynamicBackground(false);
    glass.setEnableSensorHighlight(true);
    glass.setEnableAdaptiveTint(true);
    addView(
        glass,
        0,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    this.glass = glass;
    glass.onThemeRefresh = this::scheduleThemeRefresh;
  }

  /**
   * A theme pass refreshes the glass before the activity repaints its window background, so the
   * backdrop is re-resolved after the current message, once the new background is in place.
   */
  private void scheduleThemeRefresh() {
    if (themeRefreshPending || !isAttachedToWindow()) return;
    themeRefreshPending = true;
    post(
        () -> {
          themeRefreshPending = false;
          updateBackdrop();
        });
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (glass == null) return;
    // The wallpaper layer and the page only have their final size after the first layout pass
    // of the new window, so the backdrop is resolved there and again on every re-attach (pages
    // are detached and re-attached by ViewPager2/RecyclerView).
    if (!backdropListenerRegistered) {
      getViewTreeObserver().addOnGlobalLayoutListener(backdropLayoutListener);
      backdropListenerRegistered = true;
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    removeBackdropListener();
    super.onDetachedFromWindow();
  }

  private void removeBackdropListener() {
    if (!backdropListenerRegistered) return;
    ViewTreeObserver observer = getViewTreeObserver();
    if (observer.isAlive()) {
      observer.removeOnGlobalLayoutListener(backdropLayoutListener);
    }
    backdropListenerRegistered = false;
  }

  /**
   * Points the glass at what is really painted behind this bar.
   *
   * <ul>
   *   <li>Outside a ViewPager2: the host Activity's content view (unchanged behaviour).
   *   <li>Inside a page over a wallpaper: the wallpaper layer behind the pager, see {@link
   *       Glass#findWallpaperBehindPager(View)}.
   *   <li>Inside a page over a plain background: this bar itself, painted with that background
   *       colour. The page root is transparent, and the lens shader turns a transparent capture
   *       into an opaque near-black panel, so the colour that is visible behind the page has to be
   *       part of the capture. It is clipped to the bar, so it adds no overdraw outside it.
   * </ul>
   *
   * @return true when a backdrop was resolved and no further layout pass needs to retry.
   */
  private boolean updateBackdrop() {
    SearchGlass glass = this.glass;
    if (glass == null || !isAttachedToWindow()) return false;
    if (!Glass.isGlassEnabled(getContext())) {
      setBackdropFill(Color.TRANSPARENT);
      return true;
    }
    if (findPageRoot() == null) {
      View content = Glass.resolveBackdrop(getContext());
      if (content == null) return false;
      setBackdropFill(Color.TRANSPARENT);
      glass.setBackdropSource(content);
      return true;
    }
    View wallpaper = Glass.findWallpaperBehindPager(this);
    if (wallpaper != null) {
      setBackdropFill(Color.TRANSPARENT);
      glass.setBackdropSource(wallpaper);
      return true;
    }
    int behind = Glass.colorBehind(this);
    if (Color.alpha(behind) == 0) {
      // Nothing opaque is painted behind the page (translucent window): there is nothing to
      // refract, so the bar uses the opaque Material surface instead of a black lens.
      setBackdropFill(Color.TRANSPARENT);
      Glass.applyFallback(glass);
      return true;
    }
    setBackdropFill(ColorUtils.setAlphaComponent(behind, 255));
    glass.setBackdropSource(this);
    glass.invalidate();
    return true;
  }

  private void setBackdropFill(int color) {
    if (Color.alpha(color) == 0) {
      if (getBackground() == backdropFill && backdropFill != null) {
        setBackground(null);
      }
      return;
    }
    if (backdropFill == null) {
      backdropFill = new GradientDrawable();
      backdropFill.setCornerRadius(GLASS_CORNER_DP * getResources().getDisplayMetrics().density);
    }
    backdropFill.setColor(color);
    if (getBackground() != backdropFill) {
      setBackground(backdropFill);
    }
  }

  /**
   * The page this bar is displayed on: the direct child of the ViewPager2's internal RecyclerView
   * that contains this view, or null when the bar is not inside a ViewPager2.
   */
  @Nullable
  private View findPageRoot() {
    ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
    while (parent != null) {
      ViewParent grandParent = parent.getParent();
      if (grandParent instanceof RecyclerView && ((RecyclerView) grandParent).getParent() instanceof ViewPager2) {
        return parent;
      }
      if (!(grandParent instanceof ViewGroup)) {
        return null;
      }
      parent = (ViewGroup) grandParent;
    }
    return null;
  }

  /** The bar's glass panel; re-resolves the backdrop whenever the theme pass refreshes it. */
  private static final class SearchGlass extends GlassCompat {

    @Nullable Runnable onThemeRefresh;

    SearchGlass(Context context) {
      super(context);
    }

    @Override
    public void refresh() {
      super.refresh();
      Runnable callback = onThemeRefresh;
      if (callback != null) {
        callback.run();
      }
    }
  }

  private static Activity resolveActivity(Context context) {
    while (context instanceof ContextWrapper) {
      if (context instanceof Activity) {
        return (Activity) context;
      }
      context = ((ContextWrapper) context).getBaseContext();
    }
    return null;
  }

  private void setupListeners() {
    editText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            String text = s.toString();
            showClearButton(!text.isEmpty());
            if (onTextChangedListener != null) {
              onTextChangedListener.onTextChanged(text);
            }
          }

          @Override
          public void afterTextChanged(Editable s) {}
        });

    clearButton.setOnClickListener(
        v -> {
          clear();
          requestFocusForEditText();
        });

    editText.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            performSearch();
            return true;
          }
          return false;
        });

    searchIcon.setOnClickListener(v -> performSearch());
  }

  private void showClearButton(boolean show) {
    clearButton.setAlpha(1f);
    clearButton.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
  }

  private void performSearch() {
    String query = editText.getText().toString();
    if (onSearchListener != null && !query.trim().isEmpty()) {
      onSearchListener.onSearch(query);
    }
  }

  // متدهای عمومی
  public void setOnSearchListener(OnSearchListener listener) {
    this.onSearchListener = listener;
  }

  public void setOnTextChangedListener(OnTextChangedListener listener) {
    this.onTextChangedListener = listener;
  }

  public String getQuery() {
    return editText.getText().toString();
  }

  public void setHint(CharSequence hint) {
    editText.setHint(hint);
  }

  public void setQuery(String query) {
    editText.setText(query);
    editText.setSelection(query.length());
  }

  public void clear() {
    editText.getText().clear();
  }

  public void requestFocusForEditText() {
    editText.requestFocus();
  }

  public interface OnSearchListener {
    void onSearch(String query);
  }

  public interface OnTextChangedListener {
    void onTextChanged(String text);
  }

  public void setIconClose(int icon) {
    if (icon == 0) {
      throw new IllegalArgumentException("icon res not found call setIconClose(#int.class)");
    } else {
      clearButton.setImageResource(icon);
    }
  }

  public void setIconSearch(int icon) {
    if (icon == 0) {
      throw new IllegalArgumentException("icon res not found call setIconSearch(#int.class)");
    } else searchIcon.setImageResource(icon);
  }

  public boolean isShow() {
    return getVisibility() == VISIBLE;
  }

  public void show() {
    var material = new MaterialSharedAxis(MaterialSharedAxis.Z, true);
    if (getParent() instanceof ViewGroup) {
      TransitionManager.beginDelayedTransition((ViewGroup) getParent(), material);
    }
    setVisibility(VISIBLE);
  }

  public void hide() {
    var material = new MaterialSharedAxis(MaterialSharedAxis.Z, false);
    if (getParent() instanceof ViewGroup) {
      TransitionManager.beginDelayedTransition((ViewGroup) getParent(), material);
    }
    setVisibility(GONE);
  }

}