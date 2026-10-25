package ir.hanzodev1375.ghostide.store;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.graphics.ColorUtils;
import ir.theme.ActivityTheme;
import ir.theme.EditorTheme;
import ir.theme.GhostTheme;
import ir.theme.M3Theme;
import ir.theme.MaterialTheme;
import ir.theme.ThemeMediaPath;
import ir.theme.WidgetTheme;
import java.io.File;

/**
 * Immutable snapshot of the colours a store theme preview paints, extracted once from the parsed
 * theme so switching between previews only rebinds ints and never re-parses JSON.
 *
 * <p>The values mirror what the app does once the theme is applied: the window shows the
 * wallpaper with {@code activity.background} on top, or {@code material3.surface} when there is no
 * wallpaper; the editor paints {@code editor.wholeBackground} only when there is no wallpaper.
 */
final class ThemePreviewData {

  /** Fixed per-entry overhead used by the cache next to the wallpaper bytes. */
  private static final int BASE_BYTES = 1024;

  private static final double DARK_TEXT_LUMINANCE = 0.5;

  @Nullable final Bitmap wallpaper;
  @ColorInt final int windowBackground;
  @ColorInt final int wallpaperScrim;

  @ColorInt final int primary;
  @ColorInt final int onPrimary;
  @ColorInt final int primaryContainer;
  @ColorInt final int onPrimaryContainer;
  @ColorInt final int secondary;
  @ColorInt final int secondaryContainer;
  @ColorInt final int onSecondaryContainer;
  @ColorInt final int tertiary;
  @ColorInt final int surface;
  @ColorInt final int surfaceContainer;
  @ColorInt final int onSurface;
  @ColorInt final int onSurfaceVariant;
  @ColorInt final int error;

  @ColorInt final int editorBackground;
  @ColorInt final int gutterBackground;
  @ColorInt final int lineNumber;
  @ColorInt final int lineNumberCurrent;
  @ColorInt final int lineDivider;
  @ColorInt final int currentLine;
  @ColorInt final int selection;
  @ColorInt final int cursor;
  @ColorInt final int textNormal;
  @ColorInt final int keyword;
  @ColorInt final int comment;
  @ColorInt final int operator;
  @ColorInt final int literal;
  @ColorInt final int identifierVar;
  @ColorInt final int identifierName;
  @ColorInt final int functionName;
  @ColorInt final int annotation;

  /** True when the theme's foreground is light, i.e. the theme is a dark theme. */
  final boolean dark;

  private ThemePreviewData(GhostTheme theme, @Nullable Bitmap wallpaper) {
    MaterialTheme m = theme.getMaterial3() != null ? theme.getMaterial3() : new MaterialTheme();
    EditorTheme e = theme.getEditor() != null ? theme.getEditor() : new EditorTheme();
    ActivityTheme a = theme.getActivity() != null ? theme.getActivity() : new ActivityTheme();
    this.wallpaper = wallpaper;

    onSurface = color(m.getOnSurface(), Color.WHITE);
    surface = color(m.getSurface(), Color.BLACK);
    surfaceContainer = color(m.getSurfaceContainer(), surface);
    onSurfaceVariant = color(m.getOnSurfaceVariant(), onSurface);
    primary = color(m.getPrimary(), onSurface);
    onPrimary = color(m.getOnPrimary(), surface);
    primaryContainer = color(m.getPrimaryContainer(), primary);
    onPrimaryContainer = color(m.getOnPrimaryContainer(), onPrimary);
    secondary = color(m.getSecondary(), primary);
    secondaryContainer = color(m.getSecondaryContainer(), surfaceContainer);
    onSecondaryContainer = color(m.getOnSecondaryContainer(), onSurface);
    tertiary = color(m.getTertiary(), secondary);
    error = color(m.getError(), Color.RED);

    windowBackground = ColorUtils.setAlphaComponent(surface, 255);
    wallpaperScrim = color(a.getBackground(), Color.TRANSPARENT);

    textNormal = color(e.getTextNormal(), onSurface);
    editorBackground = wallpaper != null ? Color.TRANSPARENT : color(e.getWholeBackground(), surface);
    gutterBackground = color(e.getLineNumberBackground(), Color.TRANSPARENT);
    lineNumber = color(e.getLineNumber(), onSurfaceVariant);
    lineNumberCurrent = color(e.getLineNumberCurrent(), textNormal);
    lineDivider = color(e.getLineDivider(), Color.TRANSPARENT);
    currentLine = color(e.getCurrentLine(), Color.TRANSPARENT);
    selection = color(e.getSelectedTextBackground(), primaryContainer);
    cursor = color(e.getSelectionInsert(), primary);
    keyword = color(e.getKeyword(), textNormal);
    comment = color(e.getComment(), textNormal);
    operator = color(e.getOperator(), textNormal);
    literal = color(e.getLiteral(), textNormal);
    identifierVar = color(e.getIdentifierVar(), textNormal);
    identifierName = color(e.getIdentifierName(), textNormal);
    functionName = color(e.getFunctionName(), textNormal);
    annotation = color(e.getAnnotation(), textNormal);

    dark = ColorUtils.calculateLuminance(ColorUtils.setAlphaComponent(onSurface, 255))
        > DARK_TEXT_LUMINANCE;
  }

  /**
   * Builds the snapshot and decodes the wallpaper, if the theme has one, scaled down to roughly
   * {@code targetWidthPx} so a phone-sized wallpaper never costs more than a preview-sized bitmap.
   *
   * @param theme a theme parsed with {@code ThemeManager.parseDetached}.
   * @param themeFile the downloaded {@code .gth}; relative wallpaper paths resolve next to it.
   * @param targetWidthPx the width the preview is drawn at; values below 1 skip the wallpaper.
   */
  @WorkerThread
  static ThemePreviewData create(GhostTheme theme, File themeFile, int targetWidthPx) {
    WidgetTheme widget = theme.getWidget();
    String imagePath = widget != null ? widget.getImagepath() : null;
    Bitmap wallpaper = null;
    if (imagePath != null && !imagePath.isEmpty() && targetWidthPx > 0) {
      String resolved = ThemeMediaPath.resolve(themeFile.getAbsolutePath(), imagePath);
      if (resolved != null) {
        wallpaper = decodeScaled(new File(resolved), targetWidthPx);
      }
    }
    return new ThemePreviewData(theme, wallpaper);
  }

  /** Approximate memory held by this entry, for the preview cache's size accounting. */
  int byteCount() {
    return BASE_BYTES + (wallpaper != null ? wallpaper.getAllocationByteCount() : 0);
  }

  @Nullable
  private static Bitmap decodeScaled(File file, int targetWidthPx) {
    if (!file.isFile()) {
      return null;
    }
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      return null;
    }
    int sample = 1;
    while (bounds.outWidth / (sample * 2) >= targetWidthPx) {
      sample *= 2;
    }
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
  }

  @ColorInt
  private static int color(@Nullable String value, @ColorInt int fallback) {
    Integer parsed = M3Theme.color(value);
    return parsed != null ? parsed : fallback;
  }
}
