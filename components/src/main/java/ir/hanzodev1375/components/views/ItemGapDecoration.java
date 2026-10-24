package ir.hanzodev1375.components.views;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Leaves a fixed gap below every item of a vertical list except the last one.
 *
 * <p>Replaces the identical per-screen copies that existed in the history, bookmark, GitHub
 * profile, log and AI chat lists. The gap is applied to the bottom edge only, so it does not depend
 * on the layout direction.
 */
public class ItemGapDecoration extends RecyclerView.ItemDecoration {

  /** Gap in pixels used by lists that never defined a dimension of their own. */
  public static final int DEFAULT_GAP_PX = 2;

  private final int gapPx;

  public ItemGapDecoration() {
    this(DEFAULT_GAP_PX);
  }

  public ItemGapDecoration(int gapPx) {
    this.gapPx = gapPx;
  }

  /** Creates a decoration whose gap is the pixel size of the given dimension resource. */
  @NonNull
  public static ItemGapDecoration fromDimen(@NonNull Context context, @DimenRes int dimenRes) {
    return new ItemGapDecoration(context.getResources().getDimensionPixelSize(dimenRes));
  }

  @Override
  public void getItemOffsets(
      @NonNull Rect outRect,
      @NonNull View view,
      @NonNull RecyclerView parent,
      @NonNull RecyclerView.State state) {
    if (parent.getChildAdapterPosition(view) != state.getItemCount() - 1) {
      outRect.bottom = gapPx;
    }
  }
}
