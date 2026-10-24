package ir.hanzodev1375.ghostide.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.ghostide.R;
import ir.theme.M3Theme;

/**
 * Draws hairline separators between the rows of a linear list whose rows sit flush against each
 * other as one grouped surface. A line starts after the row icon (the inset is measured from the
 * physical left edge, because the file manager is always laid out left to right) and stops short
 * of the right edge. Nothing is drawn after the last row or while the list uses a grid layout.
 *
 * <p>The colour is read from {@link M3Theme#outlineVariant()} on every pass, so a theme change is
 * picked up on the next frame without re-creating the decoration.
 */
public class GroupedRowDecoration extends RecyclerView.ItemDecoration {
  private static final float LINE_ALPHA = 0.55f;
  private static final float LINE_WIDTH_DP = 0.6f;
  private static final float END_INSET_DP = 16f;

  private final Paint paint = new Paint();
  private final int startInset;
  private final int endInset;
  private final int lineHeight;

  public GroupedRowDecoration(@NonNull Context context) {
    float density = context.getResources().getDisplayMetrics().density;
    startInset = context.getResources().getDimensionPixelSize(R.dimen.fm_row_divider_inset);
    endInset = Math.round(END_INSET_DP * density);
    lineHeight = Math.max(1, Math.round(LINE_WIDTH_DP * density));
  }

  @Override
  public void onDrawOver(
      @NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    if (parent.getLayoutManager() instanceof GridLayoutManager) return;
    Integer outline = M3Theme.outlineVariant();
    if (outline == null) return;
    int lastPosition = state.getItemCount() - 1;
    int baseAlpha = Math.round(Color.alpha(outline) * LINE_ALPHA);
    paint.setColor(outline);
    for (int i = 0; i < parent.getChildCount(); i++) {
      View child = parent.getChildAt(i);
      int position = parent.getChildAdapterPosition(child);
      if (position == RecyclerView.NO_POSITION || position >= lastPosition) continue;
      float alpha = child.getAlpha();
      if (alpha <= 0f) continue;
      paint.setAlpha(Math.round(baseAlpha * Math.min(1f, alpha)));
      float bottom = child.getBottom() + child.getTranslationY();
      canvas.drawRect(
          child.getLeft() + child.getTranslationX() + startInset,
          bottom - lineHeight,
          child.getRight() + child.getTranslationX() - endInset,
          bottom,
          paint);
    }
  }
}
