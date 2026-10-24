package ir.hanzodev1375.ghostide.utils;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.util.Locale;

/**
 * Right-to-left helpers for screens of an app that declares {@code supportsRtl="false"}.
 *
 * <p>With RTL support off, Android always resolves layout direction to left-to-right, so {@code
 * start}/{@code end} attributes keep meaning left/right even in Persian. Text paragraphs still
 * align by their own direction, but icons, buttons and label/value pairs do not move. These helpers
 * put such views on the reading side explicitly; they are no-ops for left-to-right locales.
 */
public final class LocaleDirection {

  private LocaleDirection() {}

  /** @return true when the current UI language of {@code context} reads right to left. */
  public static boolean isRtl(Context context) {
    Locale locale = context.getResources().getConfiguration().getLocales().get(0);
    return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
  }

  /**
   * Physical gravity of the reading start edge: {@link Gravity#RIGHT} for a right-to-left language,
   * {@link Gravity#LEFT} otherwise.
   */
  public static int startGravity(Context context) {
    return isRtl(context) ? Gravity.RIGHT : Gravity.LEFT;
  }

  /**
   * Mirrors a horizontal row for a right-to-left language: reverses the child order and swaps each
   * child's left and right margins, so the first child of the XML sits on the right edge. Call it
   * once per inflated row; calling it twice restores the original order.
   *
   * @param row a horizontal {@link LinearLayout}; other orientations are left untouched
   */
  public static void mirrorRow(LinearLayout row) {
    if (row == null
        || row.getOrientation() != LinearLayout.HORIZONTAL
        || !isRtl(row.getContext())) {
      return;
    }
    int count = row.getChildCount();
    View[] children = new View[count];
    for (int i = 0; i < count; i++) {
      children[i] = row.getChildAt(i);
    }
    row.removeAllViews();
    for (int i = count - 1; i >= 0; i--) {
      View child = children[i];
      ViewGroup.LayoutParams params = child.getLayoutParams();
      if (params instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        int start = margins.getMarginStart();
        int end = margins.getMarginEnd();
        margins.setMarginStart(end);
        margins.setMarginEnd(start);
        margins.leftMargin = end;
        margins.rightMargin = start;
      }
      row.addView(child, params);
    }
  }
}
