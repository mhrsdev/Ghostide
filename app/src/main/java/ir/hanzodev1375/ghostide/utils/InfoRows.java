package ir.hanzodev1375.ghostide.utils;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import ir.hanzodev1375.ghostide.R;

/**
 * Label/value rows built from {@code item_diag_row_single}: the label reads in the UI language,
 * the value is monospace data (versions, ABIs, sizes). For a right-to-left language the row is
 * mirrored so the label sits on the right, and the value hugs the opposite edge.
 */
public final class InfoRows {

  private InfoRows() {}

  /**
   * Inflates a row, fills it and appends it to {@code parent}.
   *
   * @return the added row
   */
  @NonNull
  public static View add(
      @NonNull LinearLayout parent, @StringRes int label, @NonNull CharSequence value) {
    View row =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_diag_row_single, parent, false);
    parent.addView(row);
    prepare(row);
    set(row, parent.getContext().getString(label), value);
    return row;
  }

  /**
   * Mirrors an already inflated row for right-to-left languages and aligns its value. Call once per
   * row, before {@link #set}.
   */
  public static void prepare(@NonNull View row) {
    boolean rtl = LocaleDirection.isRtl(row.getContext());
    if (row instanceof LinearLayout) {
      LocaleDirection.mirrorRow((LinearLayout) row);
    }
    TextView value = row.findViewById(R.id.tv_value);
    TextView label = row.findViewById(R.id.tv_label);
    if (value != null) {
      value.setGravity((rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
    }
    if (label != null) {
      label.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
    }
  }

  /** Sets the label and value text of a row. */
  public static void set(@NonNull View row, @NonNull CharSequence label, @NonNull CharSequence value) {
    TextView labelView = row.findViewById(R.id.tv_label);
    TextView valueView = row.findViewById(R.id.tv_value);
    if (labelView != null) {
      labelView.setText(label);
    }
    if (valueView != null) {
      valueView.setText(value);
    }
  }
}
