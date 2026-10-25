package ir.hanzodev1375.ghostide.customui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.bumptech.glide.Glide;
import com.google.android.material.materialswitch.MaterialSwitch;
import ir.hanzodev1375.ghostide.databinding.LayoutSwitchPerfenceBinding;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.utils.ShapeUtil;
import ir.theme.M3Theme;

/**
 * One settings row: optional icon, title, optional summary and an optional trailing switch.
 *
 * <p>The row itself is the touch target and the accessibility node. With its switch visible it is
 * reported as a checkable switch whose state is announced on toggle; otherwise it is reported as a
 * button that opens the row's dialog. The inner switch is hidden from accessibility services so the
 * row is read once. The row is mirrored for right-to-left languages because the app declares {@code
 * supportsRtl="false"}.
 */
public class PreferenceSwitchGroup extends RelativeLayout implements View.OnClickListener {

  private static final int BG_TYPE_UNKNOWN = -1;
  private static final int BG_TYPE_SINGLE = 0;
  private static final int BG_TYPE_TOP = 1;
  private static final int BG_TYPE_BOTTOM = 2;
  private static final int BG_TYPE_MIDDLE = 3;

  private LayoutSwitchPerfenceBinding binding;
  private boolean value = false;
  private int lastBackgroundType = BG_TYPE_UNKNOWN;

  public PreferenceSwitchGroup(Context context) {
    super(context);
    init(context, null);
  }

  public PreferenceSwitchGroup(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public PreferenceSwitchGroup(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs);
  }

  private void init(Context context, AttributeSet attrs) {
    binding = LayoutSwitchPerfenceBinding.inflate(LayoutInflater.from(context), this, true);
    setOnClickListener(this);
    showIcon(false);
    M3Theme.listCard(binding.getRoot());
    int startGravity = LocaleDirection.startGravity(context);
    binding.preferenceName.setGravity(startGravity);
    binding.preferenceDescription.setGravity(startGravity);
    LocaleDirection.mirrorRow(binding.getRoot());
    ViewCompat.setImportantForAccessibility(
        binding.preferenceSwitch, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
    ViewCompat.setAccessibilityDelegate(this, new RowAccessibilityDelegate());
  }

  private boolean hasVisibleSwitch() {
    return binding.preferenceSwitch.getVisibility() == View.VISIBLE;
  }

  private final class RowAccessibilityDelegate extends AccessibilityDelegateCompat {

    @Override
    public void onInitializeAccessibilityNodeInfo(
        @NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
      super.onInitializeAccessibilityNodeInfo(host, info);
      if (hasVisibleSwitch()) {
        info.setClassName(Switch.class.getName());
        info.setCheckable(true);
        info.setChecked(binding.preferenceSwitch.isChecked());
      } else {
        info.setClassName(Button.class.getName());
      }
    }

    @Override
    public void onInitializeAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event) {
      super.onInitializeAccessibilityEvent(host, event);
      if (hasVisibleSwitch()) {
        event.setClassName(Switch.class.getName());
        event.setChecked(binding.preferenceSwitch.isChecked());
      }
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (getLayoutParams() instanceof MarginLayoutParams) {
      MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
      if (lp.bottomMargin == 0) {
        lp.bottomMargin = (int) (4 * getResources().getDisplayMetrics().density);
        setLayoutParams(lp);
      }
    }
  }

  public void setListPosition(int position, int totalCount) {
    int type;
    if (totalCount <= 1) {
      type = BG_TYPE_SINGLE;
    } else if (position == 0) {
      type = BG_TYPE_TOP;
    } else if (position == totalCount - 1) {
      type = BG_TYPE_BOTTOM;
    } else {
      type = BG_TYPE_MIDDLE;
    }

    if (type == lastBackgroundType) {
      return;
    }
    lastBackgroundType = type;
    Drawable background;
    if (type == BG_TYPE_TOP) {
      background = ShapeUtil.top(this);
    } else if (type == BG_TYPE_BOTTOM || type == BG_TYPE_SINGLE) {
      background = ShapeUtil.bottom(this);
    } else {
      background = ShapeUtil.middel(this);
    }
    if (background != null) {
      setBackground(background);
    }
  }

  public boolean getValue() {
    return value;
  }

  public void setValue(boolean value) {
    this.value = value;
    binding.preferenceSwitch.setChecked(value);
  }

  @Override
  public void onClick(View v) {
    binding.preferenceSwitch.setChecked(!binding.preferenceSwitch.isChecked());
  }

  public void setTitle(CharSequence title) {
    binding.preferenceName.setText(title);
  }

  public void setDescription(CharSequence description) {
    boolean hasDescription = description != null && description.length() > 0;
    binding.preferenceDescription.setText(description);
    binding.preferenceDescription.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
  }

  public void setIcon(int resId) {
    binding.preferenceIcon.setImageResource(resId);
    showIcon(true);
  }

  public void setIcon(Drawable drawable) {
    binding.preferenceIcon.setImageDrawable(drawable);
    showIcon(true);
  }

  public void setIconPath(String path) {
    showIcon(true);
    if (path != null && !path.isEmpty()) {
      if (path.endsWith(".gif")) {
        Glide.with(binding.preferenceIcon.getContext())
            .asGif()
            .load(path)
            .into(binding.preferenceIcon);
      } else {
        Glide.with(binding.preferenceIcon.getContext()).load(path).into(binding.preferenceIcon);
      }
    }
  }

  public void setIconColorFilter(int color) {
    binding.preferenceIcon.setColorFilter(color);
  }

  public void setSwitchChangedListener(CompoundButton.OnCheckedChangeListener listener) {
    binding.preferenceSwitch.setOnCheckedChangeListener(
        (button, isChecked) -> {
          value = isChecked;
          if (listener != null) listener.onCheckedChanged(button, isChecked);
        });
  }

  public MaterialSwitch getSwitch() {
    return binding.preferenceSwitch;
  }

  public TextView getTitleView() {
    return binding.preferenceName;
  }

  public TextView getDescriptionView() {
    return binding.preferenceDescription;
  }

  public ImageView getIconView() {
    return binding.preferenceIcon;
  }

  public void showIcon(boolean show) {
    binding.preferenceIcon.setVisibility(show ? View.VISIBLE : View.GONE);
  }
}
