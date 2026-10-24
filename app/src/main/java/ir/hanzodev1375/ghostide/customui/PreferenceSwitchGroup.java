package ir.hanzodev1375.ghostide.customui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.google.android.material.materialswitch.MaterialSwitch;
import ir.hanzodev1375.ghostide.databinding.LayoutSwitchPerfenceBinding;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.utils.ShapeUtil;
import ir.theme.M3Theme;

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
    LocaleDirection.mirrorRow(binding.getRoot());
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
