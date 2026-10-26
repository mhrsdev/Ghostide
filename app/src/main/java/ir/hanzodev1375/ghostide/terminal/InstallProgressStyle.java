package ir.hanzodev1375.ghostide.terminal;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.progressindicator.BaseProgressIndicator;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import ir.hanzodev1375.ghostide.R;
import ir.theme.M3Theme;

/**
 * The Material 3 Expressive wavy look shared by every Debian install progress indicator (the
 * activity banner, the terminal sheet dialog and the init overlay).
 *
 * <p>The wave costs nothing extra while idle: it has no phase animation of its own (wave speed 0),
 * and its amplitude is set to 0, a flat track, whenever the indicator is not active or not shown,
 * or the system animator duration scale is 0 (reduced motion).
 */
public final class InstallProgressStyle {

  private InstallProgressStyle() {}

  /** Creates a linear indicator with the wavy install style for views built in code. */
  @NonNull
  public static LinearProgressIndicator createLinear(@NonNull Context context) {
    return new LinearProgressIndicator(
        new ContextThemeWrapper(context, R.style.ThemeOverlay_GhostIDE_InstallProgress));
  }

  /**
   * Applies the theme colours from {@link M3Theme} and turns the wave on or off.
   *
   * @param indicator the indicator to update; ignored when {@code null}
   * @param active whether an install phase is running and the indicator is meant to be visible
   */
  public static void apply(@Nullable BaseProgressIndicator<?> indicator, boolean active) {
    if (indicator == null) return;
    M3Theme.progress(indicator);
    boolean wave =
        active && indicator.getVisibility() == View.VISIBLE && ValueAnimator.areAnimatorsEnabled();
    int amplitude = 0;
    if (wave) {
      int res =
          indicator instanceof CircularProgressIndicator
              ? R.dimen.install_progress_wave_amplitude_circular
              : R.dimen.install_progress_wave_amplitude_linear;
      amplitude = indicator.getResources().getDimensionPixelSize(res);
    }
    if (indicator.getWaveAmplitude() != amplitude) indicator.setWaveAmplitude(amplitude);
  }
}
