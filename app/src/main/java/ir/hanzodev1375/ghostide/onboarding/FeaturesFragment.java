package ir.hanzodev1375.ghostide.onboarding;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.databinding.FragmentFeaturesBinding;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;

public class FeaturesFragment extends Fragment {

  private FragmentFeaturesBinding binding;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    binding = FragmentFeaturesBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    int fallback = 0xFF202020;
    int text = fallback(M3Theme.onSurface(), fallback);
    int iconTint = fallback(M3Theme.onSurfaceVariant(), fallback);
    binding.tvHeader.setTextColor(text);
    binding.tvDesc.setTextColor(text);

    boolean rtl = LocaleDirection.isRtl(requireContext());
    bindFeature(binding.tvFeatureLsp, R.drawable.ic_plugin_lsp, text, iconTint, rtl);
    bindFeature(binding.tvFeatureTerminal, R.drawable.ic_onboarding_terminal, text, iconTint, rtl);
    bindFeature(binding.tvFeatureGit, R.drawable.ic_git, text, iconTint, rtl);
    bindFeature(binding.tvFeaturePlugins, R.drawable.ic_outline_extension, text, iconTint, rtl);
    bindFeature(binding.tvFeatureThemes, R.drawable.ic_outline_palette, text, iconTint, rtl);
    bindFeature(binding.tvFeatureRemote, R.drawable.ic_sync, text, iconTint, rtl);

    animateIn();
  }

  /**
   * Gives a feature row its own icon on the reading side. The app runs with RTL support off, so a
   * relative (start) drawable would stay on the left in Persian; the side is chosen explicitly.
   */
  private void bindFeature(TextView row, int iconRes, int textColor, int iconTint, boolean rtl) {
    row.setTextColor(textColor);
    Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
    if (icon != null) {
      icon = icon.mutate();
      icon.setTint(iconTint);
    }
    row.setCompoundDrawablesWithIntrinsicBounds(rtl ? null : icon, null, rtl ? icon : null, null);
  }

  private void animateIn() {
    OnboardingAnim.slideUpFade(binding.featuresColumn, 0);
  }

  private static int fallback(Integer value, int def) {
    return value != null ? value : def;
  }
}