package ir.hanzodev1375.components.views;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Draws a soft accent wash that fades out along the reading direction, clipped to a pill on the
 * leading edge.
 *
 * <p>The fade is a fixed white-to-transparent mask built once per size change; the accent colour is
 * applied through a colour filter, so animating the colour no longer rebuilds a shader (and its
 * arrays) on every frame.
 */
public class GradientFrameLayout extends FrameLayout {

  private static final float HOLD_FRACTION = 0.12f;
  private static final float WASH_ALPHA = 0.34f;
  private static final long COLOR_ANIMATION_DURATION = 420L;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
  private int startColor = Color.TRANSPARENT;
  private boolean rtl;
  private ValueAnimator colorAnimator;

  public GradientFrameLayout(Context context) {
    super(context);
    init();
  }

  public GradientFrameLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public GradientFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    paint.setDither(true);
    setWillNotDraw(false);
  }

  public int getStartColor() {
    return startColor;
  }

  public void setStartColor(int color) {
    if (colorAnimator != null) {
      colorAnimator.cancel();
      colorAnimator = null;
    }
    startColor = color;
    applyColor();
    invalidate();
  }

  public void animateToColor(int color, long duration) {
    if (color == startColor && (colorAnimator == null || !colorAnimator.isRunning())) {
      return;
    }
    if (colorAnimator != null) {
      colorAnimator.cancel();
    }
    colorAnimator = ValueAnimator.ofObject(argbEvaluator, startColor, color);
    colorAnimator.setDuration(duration > 0 ? duration : COLOR_ANIMATION_DURATION);
    colorAnimator.setInterpolator(new DecelerateInterpolator());
    colorAnimator.addUpdateListener(
        animation -> {
          startColor = (int) animation.getAnimatedValue();
          applyColor();
          invalidate();
        });
    colorAnimator.start();
  }

  public void animateToColor(int color) {
    animateToColor(color, COLOR_ANIMATION_DURATION);
  }

  private void applyColor() {
    if (startColor == Color.TRANSPARENT) {
      paint.setColorFilter(null);
      return;
    }
    int alpha = Math.round(Color.alpha(startColor) * WASH_ALPHA);
    int wash = (startColor & 0x00FFFFFF) | (alpha << 24);
    paint.setColorFilter(new PorterDuffColorFilter(wash, PorterDuff.Mode.SRC_IN));
  }

  private void rebuildShader() {
    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0) {
      paint.setShader(null);
      return;
    }
    float x0 = rtl ? width : 0f;
    float x1 = rtl ? 0f : width;
    paint.setShader(
        new LinearGradient(
            x0,
            0f,
            x1,
            0f,
            new int[] {Color.WHITE, Color.WHITE, Color.TRANSPARENT},
            new float[] {0f, HOLD_FRACTION, 1f},
            Shader.TileMode.CLAMP));
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    rebuildShader();
    bounds.set(0f, 0f, w, h);
  }

  @Override
  public void onRtlPropertiesChanged(int layoutDirection) {
    super.onRtlPropertiesChanged(layoutDirection);
    rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL;
    rebuildShader();
    invalidate();
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    if (startColor != Color.TRANSPARENT && paint.getShader() != null) {
      float radius = bounds.height() / 2f;
      canvas.drawRoundRect(bounds, radius, radius, paint);
    }
    super.dispatchDraw(canvas);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (colorAnimator != null) {
      colorAnimator.cancel();
      colorAnimator = null;
    }
    super.onDetachedFromWindow();
  }
}
