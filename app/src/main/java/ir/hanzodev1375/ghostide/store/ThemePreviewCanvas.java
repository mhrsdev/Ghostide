package ir.hanzodev1375.ghostide.store;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import ir.hanzodev1375.ghostide.R;

/**
 * Draws a miniature Ghost IDE screen in the colours of a store theme: window background or
 * wallpaper, app bar, an editor with gutter, current line, selection and syntax colours, Material
 * controls, the glass tab bar and the theme's key palette.
 *
 * <p>It is a single view that is rebound with {@link #setData(ThemePreviewData)}, so switching
 * between themes costs one invalidate: nothing is inflated, no global theme state is touched (the
 * real code editor would install its colour scheme in the process-wide TextMate registry) and the
 * app's M3Theme traversal cannot repaint it, because it holds no themed child views. Text
 * positions are measured when the size or the data changes, never in {@link #onDraw(Canvas)}.
 */
public class ThemePreviewCanvas extends View {

  private static final int ROLE_TEXT = 0;
  private static final int ROLE_KEYWORD = 1;
  private static final int ROLE_TYPE = 2;
  private static final int ROLE_FUNCTION = 3;
  private static final int ROLE_VARIABLE = 4;
  private static final int ROLE_LITERAL = 5;
  private static final int ROLE_COMMENT = 6;
  private static final int ROLE_OPERATOR = 7;
  private static final int ROLE_ANNOTATION = 8;
  private static final int ROLE_COUNT = 9;

  /** Sample source as {text, role} pairs per line. Code is always laid out left to right. */
  private static final String[][] CODE = {
    {"@Preview", "8"},
    {"public", "1", " ", "0", "class", "1", " ", "0", "Greeter", "2", " {", "7"},
    {"  ", "0", "// one theme, every screen", "6"},
    {"  ", "0", "private", "1", " ", "0", "String", "2", " ", "0", "name", "4", " = ", "7",
      "\"Ghost\"", "5", ";", "7"},
    {"  ", "0", "int", "1", " ", "0", "count", "4", " = ", "7", "42", "5", ";", "7"},
    {"", "0"},
    {"  ", "0", "String", "2", " ", "0", "greet", "3", "() {", "7"},
    {"    ", "0", "return", "1", " ", "0", "\"Hi \"", "5", " + ", "7", "name", "4", ";", "7"},
    {"  }", "7"},
    {"}", "7"},
  };

  private static final int CURRENT_LINE = 7;
  private static final int SELECTED_LINE = 6;
  private static final int SELECTED_TOKEN = 3;

  private static final float ASPECT = 1.24f;
  private static final float SCREEN_HEIGHT_RATIO = 0.9f;
  private static final float GLASS_TINT_STRENGTH = 0.6f;
  private static final int SELECTED_TAB_ALPHA = 56;
  private static final int RIM_ALPHA_DARK = 70;
  private static final int RIM_ALPHA_LIGHT = 40;
  private static final int SWATCH_COUNT = 7;
  private static final int TAB_COUNT = 3;

  private final float dp;
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint code = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final RectF screen = new RectF();
  private final RectF rect = new RectF();
  private final Path screenClip = new Path();
  private final Matrix wallpaperMatrix = new Matrix();
  private final int[] roleColors = new int[ROLE_COUNT];
  private final int[] swatches = new int[SWATCH_COUNT];
  private final float[][] tokenX = new float[CODE.length][];
  private final String title;
  private final String runLabel;
  private final String debugLabel;
  private final String[] tabLabels;
  private final float[] tabLabelWidth = new float[TAB_COUNT];
  private final float[] lineNumberWidth = new float[CODE.length];
  private float runWidth;
  private float debugWidth;
  private float lineHeight;
  private float codeLeft;

  @Nullable private ThemePreviewData data;
  @ColorInt private int ringColor = Color.GRAY;

  public ThemePreviewCanvas(Context context) {
    this(context, null);
  }

  public ThemePreviewCanvas(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    dp = getResources().getDisplayMetrics().density;
    title = context.getString(R.string.theme_preview_sample_title);
    runLabel = context.getString(R.string.theme_preview_sample_run);
    debugLabel = context.getString(R.string.theme_preview_sample_debug);
    tabLabels =
        new String[] {
          context.getString(R.string.theme_preview_sample_tab_code),
          context.getString(R.string.theme_preview_sample_tab_files),
          context.getString(R.string.theme_preview_sample_tab_terminal)
        };
    code.setTypeface(Typeface.MONOSPACE);
    code.setTextSize(sp(10.5f));
    label.setTextSize(sp(12f));
    stroke.setStyle(Paint.Style.STROKE);
    for (int i = 0; i < CODE.length; i++) {
      tokenX[i] = new float[CODE[i].length / 2 + 1];
    }
    measureText();
  }

  /** Shows {@code data}, or an empty frame while it is loading when {@code data} is null. */
  public void setData(@Nullable ThemePreviewData data) {
    this.data = data;
    if (data != null) {
      roleColors[ROLE_TEXT] = data.textNormal;
      roleColors[ROLE_KEYWORD] = data.keyword;
      roleColors[ROLE_TYPE] = data.identifierName;
      roleColors[ROLE_FUNCTION] = data.functionName;
      roleColors[ROLE_VARIABLE] = data.identifierVar;
      roleColors[ROLE_LITERAL] = data.literal;
      roleColors[ROLE_COMMENT] = data.comment;
      roleColors[ROLE_OPERATOR] = data.operator;
      roleColors[ROLE_ANNOTATION] = data.annotation;
      swatches[0] = data.primary;
      swatches[1] = data.secondary;
      swatches[2] = data.tertiary;
      swatches[3] = data.surfaceContainer;
      swatches[4] = data.error;
      swatches[5] = data.keyword;
      swatches[6] = data.functionName;
      updateWallpaperMatrix();
    }
    invalidate();
  }

  /** Font of the sheet, so the sample labels match the app's typeface. */
  public void setLabelTypeface(@Nullable Typeface typeface) {
    label.setTypeface(typeface);
    measureText();
    invalidate();
  }

  /** Outline colour of the palette swatches; it belongs to the app, not to the previewed theme. */
  public void setRingColor(@ColorInt int color) {
    ringColor = color;
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = Math.round(width * ASPECT);
    setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    screen.set(0, 0, w, h * SCREEN_HEIGHT_RATIO);
    screenClip.reset();
    screenClip.addRoundRect(screen, 22 * dp, 22 * dp, Path.Direction.CW);
    updateWallpaperMatrix();
  }

  private void measureText() {
    lineHeight = sp(17f);
    codeLeft = 40 * dp;
    for (int line = 0; line < CODE.length; line++) {
      float x = 0;
      String[] tokens = CODE[line];
      for (int t = 0; t < tokens.length; t += 2) {
        tokenX[line][t / 2] = x;
        x += code.measureText(tokens[t]);
      }
      tokenX[line][tokens.length / 2] = x;
      lineNumberWidth[line] = code.measureText(LINE_NUMBERS[line]);
    }
    runWidth = label.measureText(runLabel);
    debugWidth = label.measureText(debugLabel);
    for (int i = 0; i < TAB_COUNT; i++) {
      tabLabelWidth[i] = label.measureText(tabLabels[i]);
    }
  }

  private void updateWallpaperMatrix() {
    ThemePreviewData d = data;
    Bitmap bitmap = d != null ? d.wallpaper : null;
    if (bitmap == null || screen.width() <= 0) {
      return;
    }
    float scale =
        Math.max(screen.width() / bitmap.getWidth(), screen.height() / bitmap.getHeight());
    wallpaperMatrix.setScale(scale, scale);
    wallpaperMatrix.postTranslate(
        (screen.width() - bitmap.getWidth() * scale) / 2f,
        (screen.height() - bitmap.getHeight() * scale) / 2f);
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    ThemePreviewData d = data;
    if (d == null) {
      fill.setColor(ColorUtils.setAlphaComponent(ringColor, 40));
      canvas.drawRoundRect(screen, 22 * dp, 22 * dp, fill);
      return;
    }
    int save = canvas.save();
    canvas.clipPath(screenClip);
    drawWindow(canvas, d);
    float top = drawAppBar(canvas, d);
    float editorBottom = drawEditor(canvas, d, top);
    drawControls(canvas, d, editorBottom + 14 * dp);
    drawTabBar(canvas, d);
    canvas.restoreToCount(save);
    drawSwatches(canvas);
  }

  private void drawWindow(Canvas canvas, ThemePreviewData d) {
    if (d.wallpaper != null) {
      canvas.drawBitmap(d.wallpaper, wallpaperMatrix, bitmapPaint);
      if (Color.alpha(d.wallpaperScrim) > 0) {
        canvas.drawColor(d.wallpaperScrim);
      }
    } else {
      canvas.drawColor(d.windowBackground);
    }
  }

  private float drawAppBar(Canvas canvas, ThemePreviewData d) {
    float centerY = 30 * dp;
    label.setColor(d.onSurface);
    label.setFakeBoldText(true);
    canvas.drawText(title, 16 * dp, centerY + label.getTextSize() / 3f, label);
    label.setFakeBoldText(false);
    fill.setColor(d.onSurfaceVariant);
    float x = screen.right - 20 * dp;
    for (int i = 0; i < 3; i++) {
      canvas.drawCircle(x, centerY - 5 * dp + i * 5 * dp, 1.6f * dp, fill);
    }
    return 52 * dp;
  }

  private float drawEditor(Canvas canvas, ThemePreviewData d, float top) {
    float left = 12 * dp;
    float right = screen.right - 12 * dp;
    float padTop = 8 * dp;
    float bottom = top + padTop * 2 + lineHeight * CODE.length;
    float radius = 14 * dp;
    rect.set(left, top, right, bottom);
    if (Color.alpha(d.editorBackground) > 0) {
      fill.setColor(d.editorBackground);
      canvas.drawRoundRect(rect, radius, radius, fill);
    }
    int save = canvas.save();
    canvas.clipRect(rect);
    float gutterRight = left + codeLeft - 8 * dp;
    if (Color.alpha(d.gutterBackground) > 0) {
      fill.setColor(d.gutterBackground);
      canvas.drawRect(left, top, gutterRight, bottom, fill);
    }
    if (Color.alpha(d.lineDivider) > 0) {
      fill.setColor(d.lineDivider);
      canvas.drawRect(gutterRight, top, gutterRight + Math.max(1f, dp / 2f), bottom, fill);
    }
    float baselineOffset = lineHeight * 0.72f;
    float textLeft = left + codeLeft;
    for (int line = 0; line < CODE.length; line++) {
      float lineTop = top + padTop + line * lineHeight;
      if (line == CURRENT_LINE && Color.alpha(d.currentLine) > 0) {
        fill.setColor(d.currentLine);
        canvas.drawRect(gutterRight + 1, lineTop, right, lineTop + lineHeight, fill);
      }
      if (line == SELECTED_LINE) {
        fill.setColor(d.selection);
        rect.set(
            textLeft + tokenX[line][SELECTED_TOKEN] - dp,
            lineTop + dp,
            textLeft + tokenX[line][SELECTED_TOKEN + 1] + dp,
            lineTop + lineHeight - dp);
        canvas.drawRoundRect(rect, 2 * dp, 2 * dp, fill);
      }
      float baseline = lineTop + baselineOffset;
      code.setColor(line == CURRENT_LINE ? d.lineNumberCurrent : d.lineNumber);
      canvas.drawText(
          LINE_NUMBERS[line], gutterRight - 6 * dp - lineNumberWidth[line], baseline, code);
      String[] tokens = CODE[line];
      for (int t = 0; t < tokens.length; t += 2) {
        code.setColor(roleColors[tokens[t + 1].charAt(0) - '0']);
        canvas.drawText(tokens[t], textLeft + tokenX[line][t / 2], baseline, code);
      }
      if (line == CURRENT_LINE) {
        float cursorX = textLeft + tokenX[line][tokens.length / 2 - 1];
        fill.setColor(d.cursor);
        canvas.drawRect(cursorX, lineTop + 2 * dp, cursorX + 1.5f * dp, lineTop + lineHeight - 2 * dp,
            fill);
      }
    }
    canvas.restoreToCount(save);
    return bottom;
  }

  private void drawControls(Canvas canvas, ThemePreviewData d, float top) {
    float height = 34 * dp;
    float padH = 16 * dp;
    float x = 12 * dp;
    float baseline = top + height / 2f + label.getTextSize() / 3f;

    rect.set(x, top, x + runWidth + padH * 2, top + height);
    fill.setColor(d.primary);
    canvas.drawRoundRect(rect, height / 2f, height / 2f, fill);
    label.setColor(d.onPrimary);
    canvas.drawText(runLabel, x + padH, baseline, label);

    x = rect.right + 8 * dp;
    rect.set(x, top, x + debugWidth + padH * 2, top + height);
    fill.setColor(d.secondaryContainer);
    canvas.drawRoundRect(rect, height / 2f, height / 2f, fill);
    label.setColor(d.onSecondaryContainer);
    canvas.drawText(debugLabel, x + padH, baseline, label);

    float trackW = 44 * dp;
    float trackH = 26 * dp;
    float trackRight = screen.right - 12 * dp;
    float trackTop = top + (height - trackH) / 2f;
    rect.set(trackRight - trackW, trackTop, trackRight, trackTop + trackH);
    fill.setColor(d.primary);
    canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, fill);
    fill.setColor(d.onPrimary);
    canvas.drawCircle(trackRight - trackH / 2f, trackTop + trackH / 2f, trackH / 2f - 4 * dp, fill);

    float fab = 46 * dp;
    float fabRight = screen.right - 14 * dp;
    float fabTop = top + height + 12 * dp;
    rect.set(fabRight - fab, fabTop, fabRight, fabTop + fab);
    fill.setColor(d.primaryContainer);
    canvas.drawRoundRect(rect, 14 * dp, 14 * dp, fill);
    fill.setColor(d.onPrimaryContainer);
    float cx = rect.centerX();
    float cy = rect.centerY();
    float arm = 8 * dp;
    float half = dp;
    canvas.drawRect(cx - arm, cy - half, cx + arm, cy + half, fill);
    canvas.drawRect(cx - half, cy - arm, cx + half, cy + arm, fill);
  }

  private void drawTabBar(Canvas canvas, ThemePreviewData d) {
    float height = 50 * dp;
    float bottom = screen.bottom - 12 * dp;
    rect.set(14 * dp, bottom - height, screen.right - 14 * dp, bottom);
    float radius = height / 2f;
    fill.setColor(ColorUtils.setAlphaComponent(d.surface, Math.round(255 * GLASS_TINT_STRENGTH)));
    canvas.drawRoundRect(rect, radius, radius, fill);
    stroke.setStrokeWidth(dp);
    stroke.setColor(
        ColorUtils.setAlphaComponent(
            d.dark ? Color.WHITE : Color.BLACK, d.dark ? RIM_ALPHA_DARK : RIM_ALPHA_LIGHT));
    canvas.drawRoundRect(rect, radius, radius, stroke);

    float tabWidth = rect.width() / TAB_COUNT;
    float left = rect.left;
    float top = rect.top;
    float baseline = rect.centerY() + label.getTextSize() / 3f;
    for (int i = 0; i < TAB_COUNT; i++) {
      float tabLeft = left + i * tabWidth;
      if (i == 0) {
        fill.setColor(ColorUtils.setAlphaComponent(d.primary, SELECTED_TAB_ALPHA));
        canvas.drawRoundRect(
            tabLeft + 5 * dp, top + 5 * dp, tabLeft + tabWidth - 5 * dp, rect.bottom - 5 * dp,
            radius, radius, fill);
        label.setColor(d.primary);
      } else {
        label.setColor(d.onSurfaceVariant);
      }
      canvas.drawText(tabLabels[i], tabLeft + (tabWidth - tabLabelWidth[i]) / 2f, baseline, label);
    }
  }

  private void drawSwatches(Canvas canvas) {
    float radius = 9 * dp;
    float gap = 8 * dp;
    float y = screen.bottom + (getHeight() - screen.bottom) / 2f;
    float x = radius + dp;
    stroke.setStrokeWidth(dp);
    stroke.setColor(ringColor);
    for (int i = 0; i < SWATCH_COUNT; i++) {
      fill.setColor(swatches[i]);
      canvas.drawCircle(x, y, radius, fill);
      canvas.drawCircle(x, y, radius, stroke);
      x += radius * 2 + gap;
    }
  }

  private static final String[] LINE_NUMBERS = buildLineNumbers();

  private static String[] buildLineNumbers() {
    String[] numbers = new String[CODE.length];
    for (int i = 0; i < numbers.length; i++) {
      numbers[i] = String.valueOf(i + 1);
    }
    return numbers;
  }

  private float sp(float value) {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
  }
}
