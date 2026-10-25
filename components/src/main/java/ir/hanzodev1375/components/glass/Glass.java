package ir.hanzodev1375.components.glass;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.example.liquidglass.LiquidGlassChipGroup;
import com.example.liquidglass.LiquidGlassView;
import ir.hanzodev1375.components.childern.ViewChilder;
import ir.hanzodev1375.components.colors.AccentPalette;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.FabGlass;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.GlassCompat;
import ir.hanzodev1375.components.utils.ComponentsPrefs;
import ir.theme.M3Theme;
import java.util.ArrayList;

/**
 * Shared plumbing for the Liquid Glass compat views in this package.
 *
 * <p>Glass widgets sample whatever is painted behind them. Inside an Activity the default (the
 * direct parent) is enough, but in a Dialog / BottomSheetDialog the widget lives in its own window,
 * so the parent is transparent and the capture comes back empty. Every one of those widgets has to
 * be pointed at {@code android.R.id.content} of the host Activity by hand, which is exactly the
 * boilerplate this class removes.
 */
public final class Glass {

  private Glass() {}

  static {
    // Hands the glass widgets to M3Theme's traversal so they are re-themed with everything else on
    // every theme change. M3Theme cannot import them (that would be a module cycle), hence the
    // hook.
    M3Theme.setGlassThemeApplier(
        new M3Theme.GlassThemeApplier() {
          @Override
          public boolean applyGlassView(View v) {
            if (v instanceof GlassCompat) {
              ((GlassCompat) v).refresh();
              return true;
            }
            if (v instanceof FabGlass) {
              ((FabGlass) v).refresh();
              return true;
            }
            if (v instanceof TabBarCompat) {
              ((TabBarCompat) v).refresh();
              return true;
            }
            return applyGlassChip(v) || applyGlassButton(v);
          }

          @Override
          public boolean applyGlassChip(View v) {
            if (!(v instanceof ChipCompat)) {
              return false;
            }
            applyChip((ChipCompat) v);
            return true;
          }

          @Override
          public boolean applyGlassButton(View v) {
            if (!(v instanceof ButtonCompat)) {
              return false;
            }
            applyButton((ButtonCompat) v);
            return true;
          }
        });
  }

  /**
   * Applies a chip's material, then the accent of the tone it was given.
   *
   * <p>Tones are the only place a gold/green accent enters the app: callers just ask for {@link
   * ChipCompat.Tone#GOLD} and the colour is resolved from the active theme, so nothing has to be
   * hard-coded per screen and light/dark stay in sync.
   */
  public static void applyChip(ChipCompat chip) {
    if (!isGlassEnabled(chip.getContext())
        || !new ComponentsPrefs(chip.getContext()).isGlassMaterialColor()) {
      chip.applyMaterialFallback();
      return;
    }
    chip.setChipIconTintEnabled(chip.getTone() == ChipCompat.Tone.DEFAULT);
    applyThemeTint(chip);
    applyChipTone(chip);
  }

  /**
   * Applies a glass button's material and adaptive foreground.
   *
   * <p>An {@code ERROR} tone overrides the plain surface with the theme's error colour. The tint is
   * applied as an opaque colour and left to the library to blend, so a theme that lowers the glass
   * tint strength cannot wash the destructive action out.
   */
  public static void applyButton(ButtonCompat button) {
    if (!isGlassEnabled(button.getContext())) {
      button.applyMaterialFallback();
      return;
    }
    button.setEnableAdaptiveTint(true);
    if (button.getTone() == ButtonCompat.Tone.ERROR) {
      applyErrorTint(button);
      return;
    }
    applyThemeTint(button);
  }

  private static void applyErrorTint(ButtonCompat button) {
    Context context = button.getContext();
    Integer error = M3Theme.error();
    if (context == null || error == null) {
      applyThemeTint(button);
      return;
    }
    button.setGlassTint(error);
    if (!new ComponentsPrefs(context).isGlassMaterialColor()) {
      // Without the surface tint the label would sit on a plain error-coloured panel, so it needs
      // the error container's foreground to stay readable.
      Integer onError = M3Theme.onError();
      if (onError != null) {
        button.setTextColor(onError);
      }
    }
  }

  private static void applyChipTone(ChipCompat chip) {
    float strength = toneTintStrength(chip);
    switch (chip.getTone()) {
      case GOLD:
        chip.setTextColor(gold());
        chip.setGlassTint(gold(), strength);
        // The library's adaptive icon tint would repaint the star with the plain foreground, so it
        // is turned off and the accent is baked into the drawable instead.
        applyIconTint(chip, gold());
        break;
      case GREEN:
        chip.setTextColor(green());
        chip.setGlassTint(green(), strength);
        break;
      case DEFAULT:
      default:
        Integer onSurface = M3Theme.onSurface();
        if (onSurface != null) {
          chip.setTextColor(onSurface);
        }
        break;
    }
  }

  private static void applyIconTint(ChipCompat chip, @ColorInt int color) {
    int iconRes = chip.getIconRes();
    if (iconRes == 0) {
      return;
    }
    Drawable drawable = AppCompatResources.getDrawable(chip.getContext(), iconRes);
    if (drawable == null) {
      return;
    }
    DrawableCompat.setTintList(drawable, ColorStateList.valueOf(color));
    chip.setChipIcon(drawable);
  }

  /** Amber accent, brightened for dark themes. */
  public static int gold() {
    return isDarkTheme() ? 0xFFFFD54F : 0xFFB8860B;
  }

  /** Green accent, lightened for dark themes. */
  public static int green() {
    return isDarkTheme() ? 0xFF81C784 : 0xFF2E7D32;
  }

  /**
   * Accent tones are weaker than the plain surface tint on purpose: the colour should tint the
   * glass, not replace it, so the backdrop still shows through.
   */
  private static float toneTintStrength(View view) {
    Context context = view.getContext();
    float strength = context == null ? 0.35f : new ComponentsPrefs(context).getGlassTint();
    return Math.min(strength, 0.35f);
  }

  private static boolean isDarkTheme() {
    Integer surface = M3Theme.surfaceContainer();
    if (surface == null) {
      Integer background = M3Theme.background();
      surface = background;
    }
    if (surface == null) {
      return false;
    }
    return AccentPalette.perceivedBrightness(surface) < 0.5f;
  }

  /** Unwraps {@code context} until the hosting {@link Activity} is found, or null. */
  @Nullable
  public static Activity findActivity(@Nullable Context context) {
    Context current = context;
    while (current instanceof ContextWrapper) {
      if (current instanceof Activity) {
        return (Activity) current;
      }
      current = ((ContextWrapper) current).getBaseContext();
      if (current == null) {
        return null;
      }
    }
    return null;
  }

  /**
   * The view a glass widget should capture: the host Activity's content view.
   *
   * <p>Always resolved through an explicit {@link Activity} reference on purpose. Inside a
   * receiver-like scope such as {@code view.apply { ... }} the bare {@code findViewById} call binds
   * to {@link View#findViewById} and silently yields null.
   */
  @Nullable
  public static View resolveBackdrop(@Nullable Context context) {
    Activity activity = findActivity(context);
    return activity == null ? null : activity.findViewById(android.R.id.content);
  }

  /**
   * The wallpaper layer drawn behind the ViewPager2 that hosts {@code view}, if there is one.
   *
   * <p>A glass view inside a ViewPager2 page cannot sample its page: the page root is transparent,
   * and the library's lens shader keeps only the captured RGB and returns an opaque result, so the
   * transparent capture turns into a near-black panel. It cannot sample an ancestor of the pager
   * either: that capture re-records the sibling pages, whose own glass captures the same ancestor
   * again. The wallpaper layer is what is really visible behind the page and it holds no glass, so
   * it is the one safe live backdrop.
   *
   * <p>Only views drawn before the pager's branch (lower child index) are considered, at every
   * level up to the window root.
   *
   * @return a visible, laid-out {@link ViewChilder} behind the pager, or null when {@code view} is
   *     not inside a ViewPager2 or the pager sits over a plain background.
   */
  @Nullable
  public static View findWallpaperBehindPager(@Nullable View view) {
    View pager = findPagerAncestor(view);
    if (pager == null) {
      return null;
    }
    View child = pager;
    ViewParent parent = pager.getParent();
    while (parent instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) parent;
      int end = group.indexOfChild(child);
      for (int i = 0; i < end; i++) {
        View candidate = group.getChildAt(i);
        if (candidate instanceof ViewChilder
            && candidate.getVisibility() == View.VISIBLE
            && candidate.getWidth() > 0
            && candidate.getHeight() > 0) {
          return candidate;
        }
      }
      child = group;
      parent = group.getParent();
    }
    return null;
  }

  /** The closest {@link ViewPager2} that contains {@code view}, or null. */
  @Nullable
  public static View findPagerAncestor(@Nullable View view) {
    ViewParent parent = view != null ? view.getParent() : null;
    while (parent instanceof View) {
      if (parent instanceof ViewPager2) {
        return (View) parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  /**
   * The flat colour painted behind {@code view}: the plain colour backgrounds of all its ancestors
   * (window decor first) composited in drawing order. Non-colour backgrounds are skipped.
   *
   * @return the composited colour, {@link Color#TRANSPARENT} when no ancestor paints a colour.
   */
  @ColorInt
  public static int colorBehind(@Nullable View view) {
    ArrayList<View> chain = new ArrayList<>();
    ViewParent parent = view != null ? view.getParent() : null;
    while (parent instanceof View) {
      chain.add((View) parent);
      parent = parent.getParent();
    }
    int result = Color.TRANSPARENT;
    for (int i = chain.size() - 1; i >= 0; i--) {
      Drawable background = chain.get(i).getBackground();
      if (background instanceof ColorDrawable) {
        ColorDrawable colorDrawable = (ColorDrawable) background;
        int color = colorDrawable.getColor();
        int alpha = Color.alpha(color) * colorDrawable.getAlpha() / 255;
        if (alpha > 0) {
          result = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(color, alpha), result);
        }
      }
    }
    return result;
  }

  /**
   * Points {@code glass} at the host Activity's content view and keeps the backdrop live.
   *
   * @return true when a backdrop was found and applied.
   */
  public static boolean autoBackdrop(LiquidGlassView glass) {
    if (!isGlassEnabled(glass.getContext())) {
      return false;
    }
    View source = resolveBackdrop(glass.getContext());
    if (source == null) {
      return false;
    }
    glass.setBackdropSource(source);
    glass.setEnableDynamicBackground(true);
    return true;
  }

  /**
   * Same as {@link #autoBackdrop(LiquidGlassView)} for a chip group, which forwards the source to
   * every chip it holds (including chips added later).
   */
  public static boolean autoBackdrop(LiquidGlassChipGroup group) {
    if (!isGlassEnabled(group.getContext())) {
      return false;
    }
    View source = resolveBackdrop(group.getContext());
    if (source == null) {
      return false;
    }
    group.setBackdropSource(source);
    group.setEnableDynamicBackground(true);
    return true;
  }

  /**
   * Applies the user's glass material colour (M3 surface tinted at the configured strength) so
   * compat widgets stay consistent with the rest of the app's glass surfaces.
   */
  public static void applyThemeTint(LiquidGlassView glass) {
    Context context = glass.getContext();
    if (context == null) {
      return;
    }
    if (!isGlassEnabled(context)) {
      applyFallback(glass);
      return;
    }
    ComponentsPrefs prefs = new ComponentsPrefs(context);
    if (!prefs.isGlassMaterialColor()) {
      return;
    }
    Integer surface = M3Theme.primary();
    if (surface != null) {
      glass.setGlassTint(surface, prefs.getGlassTint());
    }
  }

  // ---------------------------------------------------------------------
  // Material 3 fallback (glass disabled)
  // ---------------------------------------------------------------------

  /** Corner radius that always renders as a pill, whatever the view's height. */
  public static final float PILL_RADIUS_PX = 999f;

  /**
   * Whether the liquid-glass look is active. Off for devices the user turned it off, or that never
   * turned it on and did not have enough RAM to run the effect comfortably.
   */
  public static boolean isGlassEnabled(@Nullable Context context) {
    if (context == null) {
      return true;
    }
    return new ComponentsPrefs(context).isGlassEnabled();
  }

  /** Makes a glass view behave and look like a plain Material 3 rounded surface. */
  public static void applyFallback(LiquidGlassView glass) {
    stripGlassEffects(glass);
    glass.setBackground(
        roundedSurface(glass.getContext(), surfaceColor(), glass.getCornerRadius(), true));
  }

  /**
   * Turns every glass effect off (shader pipeline, blur, dispersion, highlight, backdrop capture)
   * so the view costs nothing while a real Material widget is drawn on top of it.
   */
  public static void stripGlassEffects(LiquidGlassView glass) {
    disableGlassEffects(glass);
    glass.setBackground(null);
  }

  /**
   * Hides every child a glass view draws so the Material 3 fallback can take its place.
   *
   * <p>The compat widgets extend {@code LiquidGlassView}, which is a {@link
   * android.widget.FrameLayout}. With the glass children {@code GONE} they no longer take part in
   * measurement or drawing, so the fallback that is added afterwards is the sole visible child and
   * the container collapses to its size — the widget reads as a genuine Material component rather
   * than a glass panel with a Material overlay.
   */
  public static void hideGlassChildren(ViewGroup glass, @Nullable View keep) {
    for (int i = 0; i < glass.getChildCount(); i++) {
      View child = glass.getChildAt(i);
      if (child != keep) {
        child.setVisibility(View.GONE);
      }
    }
  }

  /** Same as {@link #applyFallback(LiquidGlassView)} but renders a filled circle/oval. */
  public static void applyOvalFallback(LiquidGlassView glass, @ColorInt int color) {
    disableGlassEffects(glass);
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.OVAL);
    shape.setColor(color);
    glass.setBackground(shape);
  }

  private static void disableGlassEffects(LiquidGlassView glass) {
    glass.setUseShaderPipeline(false);
    glass.setUseHardwareBlurWhenPossible(false);
    glass.setEnableBackdropBlur(false);
    glass.setEnableChromaticAberration(false);
    glass.setEnableChromaticDispersion(false);
    glass.setEnableEdgeHighlight(false);
    glass.setEnableShadow(false);
    glass.setEnableSensorHighlight(false);
    glass.setEnableAdaptiveTint(false);
    glass.setEnableDynamicBackground(false);
    glass.setBlurAmount(0f);
    glass.setGlassTint(Color.TRANSPARENT);
  }

  /** A rounded-rect surface drawable in the active theme's surface colour. */
  public static Drawable roundedSurface(
      @Nullable Context context, @ColorInt int color, float radiusPx, boolean outline) {
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(radiusPx);
    shape.setColor(color);
    if (outline && context != null) {
      shape.setStroke(dp(context, 1f), outlineColor());
    }
    return shape;
  }

  public static int surfaceColor() {
    return or(M3Theme.surfaceContainer(), 0xFF211F26);
  }

  public static int surfaceHighColor() {
    return or(M3Theme.surfaceContainerHigh(), 0xFF2B2930);
  }

  public static int primaryColor() {
    return or(M3Theme.primary(), 0xFFD0BCFF);
  }

  public static int onPrimaryColor() {
    return or(M3Theme.onPrimary(), 0xFF381E72);
  }

  public static int primaryContainerColor() {
    return or(M3Theme.primaryContainer(), 0xFF4F378B);
  }

  public static int onPrimaryContainerColor() {
    return or(M3Theme.onPrimaryContainer(), 0xFFEADDFF);
  }

  public static int onSurfaceColor() {
    return or(M3Theme.onSurface(), 0xFFE6E1E5);
  }

  public static int onSurfaceVariantColor() {
    return or(M3Theme.onSurfaceVariant(), 0xFFCAC4D0);
  }

  public static int outlineColor() {
    return or(M3Theme.outlineVariant(), 0xFF49454F);
  }

  public static int errorColor() {
    return or(M3Theme.error(), 0xFFB3261E);
  }

  public static int onErrorColor() {
    return or(M3Theme.onError(), 0xFFFFFFFF);
  }

  private static int or(@Nullable Integer value, int fallback) {
    return value != null ? value : fallback;
  }

  /** Density independent pixels for {@code view}. */
  public static int dp(View view, float value) {
    return Math.round(value * view.getResources().getDisplayMetrics().density);
  }

  /** Density independent pixels for {@code context}. */
  public static int dp(Context context, float value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
