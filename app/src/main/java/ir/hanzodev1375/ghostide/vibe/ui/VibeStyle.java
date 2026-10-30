package ir.hanzodev1375.ghostide.vibe.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;
import com.google.android.material.button.MaterialButton;

/**
 * Applies a {@link VibePalette} to buttons and icons. {@code M3Theme} paints every plain {@code
 * MaterialButton} as a filled primary button, so the tonal and text variants used by this feature
 * are restored here after each theme pass.
 */
public final class VibeStyle {

  private static final int DISABLED_CONTENT_ALPHA = 97;
  private static final int DISABLED_CONTAINER_ALPHA = 31;
  private static final int RIPPLE_ALPHA = 31;

  private VibeStyle() {}

  /** Filled primary button. */
  public static void filled(@NonNull MaterialButton button, @NonNull VibePalette p) {
    paint(button, p, p.primary, p.onPrimary, p.onPrimary);
  }

  /** Tonal button on the secondary container. */
  public static void tonal(@NonNull MaterialButton button, @NonNull VibePalette p) {
    paint(button, p, p.secondaryContainer, p.onSecondaryContainer, p.onSecondaryContainer);
  }

  /** Text button without container. */
  public static void text(@NonNull MaterialButton button, @NonNull VibePalette p) {
    paint(button, p, Color.TRANSPARENT, p.primary, p.primary);
  }

  /**
   * Button placed on the error container: filled with the container's own content colour so it
   * stays visible against it.
   */
  public static void onError(@NonNull MaterialButton button, @NonNull VibePalette p) {
    paint(button, p, p.onErrorContainer, p.errorContainer, p.errorContainer);
  }

  /** Text button whose label uses {@code color}, for banners on a coloured container. */
  public static void textOn(
      @NonNull MaterialButton button, @NonNull VibePalette p, @ColorInt int color) {
    paint(button, p, Color.TRANSPARENT, color, color);
  }

  private static void paint(
      MaterialButton button,
      VibePalette p,
      @ColorInt int container,
      @ColorInt int content,
      @ColorInt int ripple) {
    button.setStrokeWidth(0);
    button.setBackgroundTintList(
        disabledAware(container, ColorUtils.setAlphaComponent(p.onSurface, DISABLED_CONTAINER_ALPHA)));
    ColorStateList contentList =
        disabledAware(content, ColorUtils.setAlphaComponent(p.onSurface, DISABLED_CONTENT_ALPHA));
    button.setTextColor(contentList);
    button.setIconTint(contentList);
    button.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(ripple, RIPPLE_ALPHA)));
  }

  /** Colours an icon. */
  public static void tint(@NonNull ImageView icon, @ColorInt int color) {
    ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
  }

  /**
   * Gives an icon button a filled circle of {@code container} that is smaller than its 48dp touch
   * area, so the visible control looks compact while the target stays large.
   */
  public static void circleButton(
      @NonNull ImageView button, @ColorInt int container, @ColorInt int content, int insetPx) {
    GradientDrawable circle = new GradientDrawable();
    circle.setShape(GradientDrawable.OVAL);
    circle.setColor(container);
    GradientDrawable mask = new GradientDrawable();
    mask.setShape(GradientDrawable.OVAL);
    mask.setColor(content);
    ColorStateList ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(content, RIPPLE_ALPHA));
    button.setBackground(
        new RippleDrawable(ripple, new InsetDrawable(circle, insetPx), new InsetDrawable(mask, insetPx)));
    tint(button, content);
  }

  /** Enables or dims a view in one call; dimmed views also stop reacting to touches. */
  public static void setEnabledLook(@NonNull View view, boolean enabled) {
    view.setEnabled(enabled);
    view.setAlpha(enabled ? 1f : 0.38f);
  }

  private static ColorStateList disabledAware(@ColorInt int enabled, @ColorInt int disabled) {
    return new ColorStateList(
        new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
        new int[] {disabled, enabled});
  }
}
