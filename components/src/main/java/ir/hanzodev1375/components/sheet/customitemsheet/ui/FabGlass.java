package ir.hanzodev1375.components.sheet.customitemsheet.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import com.example.liquidglass.LiquidGlassFab;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.components.utils.ComponentsPrefs;
import ir.theme.M3Theme;

/**
 * {@link LiquidGlassFab} that can fall back to a real Material 3 {@link FloatingActionButton}.
 *
 * <p>When the glass mode is turned off the glass icon is hidden and a genuine Material FAB is drawn
 * in its place.
 */
public class FabGlass extends LiquidGlassFab {
  private ComponentsPrefs setting;
  private FloatingActionButton materialFab;

  public FabGlass(Context c) {
    super(c);
    init();
  }

  public FabGlass(Context c, AttributeSet s) {
    super(c, s);
    init();
  }

  public FabGlass(Context c, AttributeSet s, int defStyle) {
    super(c, s, defStyle);
    init();
  }

  void init() {
    setting = new ComponentsPrefs(getContext());
    refresh();
  }

  /** Re-applies the glass tint, or the Material 3 FAB when glass is turned off. */
  public void refresh() {
    if (setting == null) {
      setting = new ComponentsPrefs(getContext());
    }
    if (!setting.isGlassEnabled() || !setting.isGlassMaterialColor()) {
      applyMaterialFallback();
      return;
    }
    setGlassTint(fallback(M3Theme.surface(), 0), setting.getGlassTint());
  }

  /** Swaps the glass rendering for a real Material 3 FAB. */
  private void applyMaterialFallback() {
    Glass.stripGlassEffects(this);
    // Hide the library's icon so only the Material FAB below is measured and drawn.
    Glass.hideGlassChildren(this, materialFab);
    if (materialFab == null) {
      materialFab = new FloatingActionButton(getContext());
      materialFab.setClickable(false);
      materialFab.setFocusable(false);
      int size = Glass.dp(getContext(), 56f);
      addView(materialFab, new LayoutParams(size, size, Gravity.CENTER));
    }
    materialFab.setImageDrawable(getIcon());
    M3Theme.fabView(materialFab);
  }

  private static int fallback(Integer value, int def) {
    return value != null ? value : def;
  }
}
