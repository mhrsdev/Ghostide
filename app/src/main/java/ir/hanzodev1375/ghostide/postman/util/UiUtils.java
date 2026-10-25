package ir.hanzodev1375.ghostide.postman.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import ir.theme.M3Theme;
import ir.theme.ThemeManager;
import ir.theme.ThemeUtils;

/** Small shared helpers for turning HTTP methods / status codes into the right accent color. */
public class UiUtils {

  private static final float ACCENT_TOWARD_TEXT = 0.3f;
  private static final float PILL_ACCENT_SHARE = 0.22f;
  private static final float ICON_ACCENT_SHARE = 0.16f;
  private static final double MIN_TEXT_CONTRAST = 4.5;
  private static final int CONTRAST_STEPS = 10;
  private static final int OPAQUE_ALPHA = 255;

  /**
   * Moves a fixed semantic accent (method / status colour) toward the theme's text colour, so it
   * stays readable as text on the current surface in both light and dark themes.
   */
  public static int readableAccent(int accent) {
    Integer text = M3Theme.onSurface();
    return text == null ? accent : ColorUtils.blendARGB(accent, text, ACCENT_TOWARD_TEXT);
  }

  /**
   * Styles a pill label (a {@code bg_chip_rounded} TextView) as a tonal badge: a faint wash of
   * {@code accent} over the theme surface and the accent itself, made readable, as text.
   */
  public static void tonalPill(TextView pill, int accent) {
    Integer surface = M3Theme.surfaceContainerHigh();
    int base = surface == null ? Color.TRANSPARENT : surface;
    int background = ColorUtils.blendARGB(base, accent, PILL_ACCENT_SHARE);
    pill.setBackgroundTintList(ColorStateList.valueOf(background));
    pill.setTextColor(legibleOn(readableAccent(accent), background));
  }

  /**
   * Moves {@code text} further toward the theme's on-surface colour until it reaches a 4.5:1
   * contrast ratio against an opaque {@code background}.
   */
  private static int legibleOn(int text, int background) {
    Integer onSurface = M3Theme.onSurface();
    if (onSurface == null || Color.alpha(background) != OPAQUE_ALPHA) {
      return text;
    }
    int color = text;
    for (int step = 1; step <= CONTRAST_STEPS
        && ColorUtils.calculateContrast(color, background) < MIN_TEXT_CONTRAST; step++) {
      color = ColorUtils.blendARGB(text, onSurface, step / (float) CONTRAST_STEPS);
    }
    return color;
  }

  /** Gives an icon a tonal primary container: primary-tinted glyph on a faint primary wash. */
  public static void tonalIcon(ImageView icon) {
    Integer primary = M3Theme.primary();
    Integer surface = M3Theme.surfaceContainerHigh();
    if (primary == null || surface == null) {
      return;
    }
    android.graphics.drawable.GradientDrawable box =
        new android.graphics.drawable.GradientDrawable();
    box.setCornerRadius(icon.getResources().getDisplayMetrics().density * 12);
    box.setColor(ColorUtils.blendARGB(surface, primary, ICON_ACCENT_SHARE));
    icon.setBackground(box);
    icon.setImageTintList(ColorStateList.valueOf(primary));
  }

  /** Tint for secondary icon buttons (delete, copy): the theme's on-surface-variant colour. */
  public static ColorStateList mutedTint() {
    Integer muted = M3Theme.onSurfaceVariant();
    return muted == null ? null : ColorStateList.valueOf(muted);
  }

  /** Tints a toolbar's navigation and overflow icons with the theme's on-surface colour. */
  public static void tintToolbar(com.google.android.material.appbar.MaterialToolbar toolbar) {
    Integer onSurface = M3Theme.onSurface();
    if (onSurface == null) {
      return;
    }
    toolbar.setNavigationIconTint(onSurface);
    if (toolbar.getOverflowIcon() != null) {
      toolbar.getOverflowIcon().mutate().setTint(onSurface);
    }
  }

  /** The theme's error colour, or the fixed server-error colour when the theme has none. */
  public static int errorColor(Context context) {
    Integer error = M3Theme.error();
    return error != null ? error : ContextCompat.getColor(context, R.color.status_server_error);
  }

  /** @return true when the active network reports internet capability */
  public static boolean isOnline(Context context) {
    ConnectivityManager cm =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (cm == null) {
      return true;
    }
    Network network = cm.getActiveNetwork();
    NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
    return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }

  public static int methodColor(Context context, String method) {
    if (method == null) return ContextCompat.getColor(context, R.color.method_default);
    switch (method.toUpperCase()) {
      case "GET":
        return ContextCompat.getColor(context, R.color.method_get);
      case "POST":
        return ContextCompat.getColor(context, R.color.method_post);
      case "PUT":
        return ContextCompat.getColor(context, R.color.method_put);
      case "PATCH":
        return ContextCompat.getColor(context, R.color.method_patch);
      case "DELETE":
        return ContextCompat.getColor(context, R.color.method_delete);
      default:
        return ContextCompat.getColor(context, R.color.method_default);
    }
  }

  public static int statusColor(Context context, int code) {
    if (code >= 200 && code < 300) return ContextCompat.getColor(context, R.color.status_success);
    if (code >= 300 && code < 400) return ContextCompat.getColor(context, R.color.status_redirect);
    if (code >= 400 && code < 500)
      return ContextCompat.getColor(context, R.color.status_client_error);
    if (code >= 500) return ContextCompat.getColor(context, R.color.status_server_error);
    return ContextCompat.getColor(context, R.color.status_neutral);
  }

  public static String statusLabel(int code, String message) {
    if (code <= 0) return "—";
    if (message == null || message.isEmpty()) return String.valueOf(code);
    return code + " " + message;
  }

  /**
   * Edge-to-edge insets for the history and collection list screens: the app bar clears the status
   * bar, the list scrolls under the navigation bar but its last row (and the optional FAB) stays
   * above it, and side insets keep rows clear of cutouts in landscape.
   */
  public static void applyListInsets(
      View appBar, View contentFrame, View list, View emptyState, @Nullable View fab) {
    int listBottom = list.getPaddingBottom();
    int fabBottom =
        fab != null && fab.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
            ? ((ViewGroup.MarginLayoutParams) fab.getLayoutParams()).bottomMargin
            : 0;
    ViewCompat.setOnApplyWindowInsetsListener(
        appBar,
        (v, insets) -> {
          Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(bars.left, bars.top, bars.right, v.getPaddingBottom());
          return insets;
        });
    ViewCompat.setOnApplyWindowInsetsListener(
        contentFrame,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(bars.left, 0, bars.right, 0);
          list.setPadding(
              list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
              listBottom + bars.bottom);
          emptyState.setPadding(
              emptyState.getPaddingLeft(), 0, emptyState.getPaddingRight(), bars.bottom);
          if (fab != null && fab.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            lp.bottomMargin = fabBottom + bars.bottom;
            fab.setLayoutParams(lp);
          }
          return insets;
        });
  }

  /**
   * وقتی حالت بک‌گراند فعال باشد، رنگ بک‌گراند تم را با شفافیت ۱۲۸ (الفا) روی ویوهای محتوا اعمال
   * می‌کند تا تصویر پس‌زمینه پشت آن‌ها دیده شود. باید بعد از setupBackgroundBlur صدا زده شود.
   */
  public static void tintContentForBackground(Context context, View... views) {
    if (!new PreferencesUtils(context).isShowBackground()) {
      return;
    }
    ThemeUtils themeUtil = new ThemeUtils(new ThemeManager(context));
    var theme = themeUtil.getTheme();
    if (theme == null
        || theme.getActivity() == null
        || theme.getActivity().getBackground() == null) {
      return;
    }
    int bg = Color.parseColor(theme.getActivity().getBackground());
    int tint = ColorUtils.setAlphaComponent(bg, 128);
    for (View v : views) {
      if (v != null) {
        v.setBackgroundColor(tint);
      }
    }
  }
}
