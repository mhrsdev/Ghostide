package ir.hanzodev1375.ghostide.vibe.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;
import ir.hanzodev1375.ghostide.R;
import ir.theme.M3Theme;

/**
 * The colours the Vibe Coding screens paint themselves with, read once from {@link M3Theme} (the
 * single colour authority of the app). Where the active theme does not define a role the colour of
 * the Material theme attribute of the same name is used. Read a new palette in {@code onThemeReapplied}; instances are immutable.
 */
public final class VibePalette {

  @ColorInt public final int surface;
  @ColorInt public final int containerLowest;
  @ColorInt public final int containerLow;
  @ColorInt public final int container;
  @ColorInt public final int containerHigh;
  @ColorInt public final int containerHighest;
  @ColorInt public final int onSurface;
  @ColorInt public final int onSurfaceVariant;
  @ColorInt public final int primary;
  @ColorInt public final int onPrimary;
  @ColorInt public final int primaryContainer;
  @ColorInt public final int onPrimaryContainer;
  @ColorInt public final int secondaryContainer;
  @ColorInt public final int onSecondaryContainer;
  @ColorInt public final int tertiary;
  @ColorInt public final int error;
  @ColorInt public final int onError;
  @ColorInt public final int errorContainer;
  @ColorInt public final int onErrorContainer;
  @ColorInt public final int outlineVariant;

  private VibePalette(Context c) {
    surface = pick(M3Theme.surface(), c, R.attr.colorSurface);
    containerLowest = pick(M3Theme.surfaceContainerLowest(), c, R.attr.colorSurfaceContainerLowest);
    containerLow = pick(M3Theme.surfaceContainerLow(), c, R.attr.colorSurfaceContainerLow);
    container = pick(M3Theme.surfaceContainer(), c, R.attr.colorSurfaceContainer);
    containerHigh = pick(M3Theme.surfaceContainerHigh(), c, R.attr.colorSurfaceContainerHigh);
    containerHighest =
        pick(M3Theme.surfaceContainerHighest(), c, R.attr.colorSurfaceContainerHighest);
    onSurface = pick(M3Theme.onSurface(), c, R.attr.colorOnSurface);
    onSurfaceVariant = pick(M3Theme.onSurfaceVariant(), c, R.attr.colorOnSurfaceVariant);
    primary = pick(M3Theme.primary(), c, R.attr.colorPrimary);
    onPrimary = pick(M3Theme.onPrimary(), c, R.attr.colorOnPrimary);
    primaryContainer = pick(M3Theme.primaryContainer(), c, R.attr.colorPrimaryContainer);
    onPrimaryContainer = pick(M3Theme.onPrimaryContainer(), c, R.attr.colorOnPrimaryContainer);
    secondaryContainer = pick(M3Theme.secondaryContainer(), c, R.attr.colorSecondaryContainer);
    onSecondaryContainer =
        pick(M3Theme.onSecondaryContainer(), c, R.attr.colorOnSecondaryContainer);
    tertiary = pick(M3Theme.tertiary(), c, R.attr.colorTertiary);
    error = pick(M3Theme.error(), c, R.attr.colorError);
    onError = pick(M3Theme.onError(), c, R.attr.colorOnError);
    errorContainer = pick(M3Theme.errorContainer(), c, R.attr.colorErrorContainer);
    onErrorContainer = pick(M3Theme.onErrorContainer(), c, R.attr.colorOnErrorContainer);
    outlineVariant = pick(M3Theme.outlineVariant(), c, R.attr.colorOutlineVariant);
  }

  /** Reads the palette of the active theme. */
  @NonNull
  public static VibePalette read(@NonNull Context context) {
    return new VibePalette(context);
  }

  /** A rounded rectangle filled with {@code color}. */
  @NonNull
  public static GradientDrawable rounded(@ColorInt int color, float radiusPx) {
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    shape.setCornerRadius(radiusPx);
    shape.setColor(color);
    return shape;
  }

  /** Converts density independent pixels to pixels. */
  public static int dp(@NonNull Context context, float value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  @ColorInt
  private static int pick(@Nullable Integer themed, Context context, @AttrRes int attr) {
    if (themed != null) {
      return themed;
    }
    return MaterialColors.getColor(context, attr, Color.TRANSPARENT);
  }
}
