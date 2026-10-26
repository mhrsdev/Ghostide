package ir.hanzodev1375.components.glass;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import com.example.liquidglass.GlassMaterial;
import com.example.liquidglass.LiquidGlassButton;
import com.google.android.material.button.MaterialButton;
import ir.theme.M3Theme;

/**
 * {@link LiquidGlassButton} that wires its own backdrop.
 *
 * <p>Use this instead of the raw library class and the glass keeps working inside dialogs and
 * bottom sheets without any manual {@code setBackdropSource(android.R.id.content)} call.
 *
 * <p>When the glass mode is turned off the button hosts a real Material 3 {@link MaterialButton}
 * instead of drawing a glass panel.
 */
public class ButtonCompat extends LiquidGlassButton {

  /**
   * Accent a button can carry instead of the plain theme surface.
   *
   * <p>The colour itself is resolved in {@link Glass} from the active theme, so a screen only ever
   * names the tone and never picks a hex value.
   */
  public enum Tone {
    DEFAULT,
    ERROR
  }

  private static final float ERROR_TINT_STRENGTH = 0.35f;

  private static final float DISABLED_CONTENT_ALPHA = 0.38f;

  private static final int MATERIAL_LABEL_LARGE =
      com.google.android.material.R.style.TextAppearance_Material3_LabelLarge;

  private Tone tone = Tone.DEFAULT;
  private boolean autoBackdrop = true;
  private LinearLayout contentRow;
  private TextView label;
  private ImageView iconView;
  private MaterialButton materialButton;

  public ButtonCompat(Context context) {
    super(context);
    init();
  }

  public ButtonCompat(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public ButtonCompat(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setEnableDynamicBackground(true);
    setMaterial(GlassMaterial.REGULAR);
    setEnableSensorHighlight(true);
    setEnableAdaptiveTint(true);
    setPressScale(0.96f);
    refresh();
    applyEnabledContentAlpha();
  }

  /** Re-applies the glass material, or the Material 3 button when glass is turned off. */
  public void refresh() {
    Glass.applyButton(this);
    applyMaterialLabelStyle();
  }

  /**
   * Gives the glass label Google Material's own button text: {@code TextAppearance.Material3
   * .LabelLarge} (14sp, medium weight, 0.1 letter spacing). Only size/weight/spacing come from the
   * appearance — the label colour stays whatever the theme tint set, so error and accent tones keep
   * working. The Material fallback button already carries this appearance through its default style.
   */
  private void applyMaterialLabelStyle() {
    TextView labelView = getTextView();
    if (labelView == null) {
      return;
    }
    labelView.setTextAppearance(MATERIAL_LABEL_LARGE);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (autoBackdrop) {
      Glass.autoBackdrop(this);
    }
  }

  /**
   * Turns the automatic backdrop wiring on or off.
   *
   * <p>On by default: the host Activity's content view is captured as soon as the button is attached,
   * which is what keeps the glass working inside dialogs and bottom sheets. Passing {@code false}
   * stops that wiring (and any live backdrop capture already running); passing {@code true} re-applies
   * it immediately when the button is already attached.
   *
   * @return this button, for chaining.
   */
  public ButtonCompat setAutoBackdrop(boolean autoBackdrop) {
    this.autoBackdrop = autoBackdrop;
    if (!autoBackdrop) {
      setEnableDynamicBackground(false);
      return this;
    }
    if (isAttachedToWindow()) {
      Glass.autoBackdrop(this);
    }
    return this;
  }

  /**
   * Gives the button an accent tone, re-resolving the glass colour and the label for the current
   * theme. Pass {@link Tone#DEFAULT} to go back to the plain surface.
   */
  public ButtonCompat setTone(Tone tone) {
    this.tone = tone == null ? Tone.DEFAULT : tone;
    refresh();
    return this;
  }

  public Tone getTone() {
    return tone;
  }

  /** Convenience alias for {@link #setText(CharSequence)}. */
  public void setLabel(CharSequence text) {
    setText(text);
  }

  /**
   * Puts a leading icon in front of the label.
   *
   * <p>Any tint baked into the drawable is cleared so the icon follows the button's adaptive
   * light/dark foreground like the label does.
   */
  public void setIconResource(int iconRes) {
    if (iconRes == 0) {
      setIconDrawable(null);
      return;
    }
    Drawable drawable = AppCompatResources.getDrawable(getContext(), iconRes);
    if (drawable == null) {
      return;
    }
    DrawableCompat.setTintList(drawable, null);
    setIconDrawable(drawable);
  }

  public void setIconDrawable(@Nullable Drawable drawable) {
    if (materialButton != null) {
      materialButton.setIcon(drawable);
      materialButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
      materialButton.setIconPadding(Glass.dp(this, 8));
      return;
    }
    ensureContentRow();
    if (contentRow == null) {
      return;
    }
    if (drawable == null) {
      if (iconView != null) {
        iconView.setVisibility(View.GONE);
      }
      return;
    }
    if (iconView == null) {
      iconView = new ImageView(getContext());
      iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
      LinearLayout.LayoutParams iconParams =
          new LinearLayout.LayoutParams(
              Glass.dp(this, 18), Glass.dp(this, 18), Gravity.CENTER_VERTICAL);
      iconParams.setMarginEnd(Glass.dp(this, 8));
      contentRow.addView(iconView, 0, iconParams);
    }
    iconView.setImageDrawable(drawable);
    iconView.setVisibility(VISIBLE);
    applyEnabledContentAlpha();
  }

  /**
   * {@link com.example.liquidglass.LiquidGlassView} handles touches without looking at the enabled
   * flag and calls {@link #performClick()} on every release, so a disabled button would still run
   * its click listener. A disabled button swallows the touch without press feedback, like {@code
   * LiquidGlassChip} does.
   */
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled()) {
      return isClickable();
    }
    return super.onTouchEvent(event);
  }

  @Override
  public boolean performClick() {
    if (!isEnabled()) {
      return false;
    }
    return super.performClick();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (materialButton != null) {
      materialButton.setEnabled(enabled);
    }
    applyEnabledContentAlpha();
  }

  /**
   * Fades the glass label and icon to the Material 3 disabled content opacity. The Material
   * fallback button draws its own disabled state, and its label lives in that button.
   */
  private void applyEnabledContentAlpha() {
    float alpha = isEnabled() ? 1f : DISABLED_CONTENT_ALPHA;
    TextView labelView = getTextView();
    if (labelView != null) {
      labelView.setAlpha(alpha);
    }
    if (iconView != null) {
      iconView.setAlpha(alpha);
    }
  }

  /** Swaps the glass rendering for a real Material 3 button. Called by {@link Glass}. */
  void applyMaterialFallback() {
    Glass.stripGlassEffects(this);
    // Hide the library's label/row so only the Material button below is measured and drawn.
    Glass.hideGlassChildren(this, materialButton);
    if (materialButton == null) {
      materialButton = new MaterialButton(getContext());
      materialButton.setClickable(false);
      materialButton.setFocusable(false);
      materialButton.setInsetTop(0);
      materialButton.setInsetBottom(0);
      addView(
          materialButton,
          new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      TextView labelView = label != null ? label : getTextView();
      if (labelView != null) {
        labelView.addTextChangedListener(
            new TextWatcher() {
              @Override
              public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

              @Override
              public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (materialButton != null) {
                  materialButton.setText(s);
                }
              }

              @Override
              public void afterTextChanged(Editable s) {}
            });
      }
    }
    materialButton.setText(getText());
    styleMaterialButton();
  }

  private void styleMaterialButton() {
    if (materialButton == null) {
      return;
    }
    M3Theme.materialButton(materialButton);
    if (tone == Tone.ERROR) {
      int background = Glass.errorColor();
      materialButton.setBackgroundTintList(ColorStateList.valueOf(background));
      materialButton.setTextColor(Glass.onErrorColor());
      materialButton.setIconTint(ColorStateList.valueOf(Glass.onErrorColor()));
    }
  }

  /**
   * Moves the library's centred label into a horizontal row so an icon can sit beside it. The
   * original label view is reused, so {@code android:text} from XML and every style applied to
   * it keep working.
   */
  private void ensureContentRow() {
    if (contentRow != null) {
      return;
    }
    label = getTextView();
    contentRow = new LinearLayout(getContext());
    contentRow.setOrientation(LinearLayout.HORIZONTAL);
    contentRow.setGravity(Gravity.CENTER);
    removeView(label);
    contentRow.addView(
        label,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    addView(
        contentRow,
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
  }
}
