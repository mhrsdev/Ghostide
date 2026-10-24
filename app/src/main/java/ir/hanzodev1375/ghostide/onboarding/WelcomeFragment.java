package ir.hanzodev1375.ghostide.onboarding;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.databinding.FragmentWelcomeBinding;
import ir.hanzodev1375.ghostide.databinding.ItemOnboardingPermissionBinding;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.utils.PermissionUtils;
import ir.theme.M3Theme;

public class WelcomeFragment extends Fragment {

  private static final int REQ_NOTIFICATION = 2030;
  private static final String PERM_ID_MANAGE_STORAGE = "MANAGE_EXTERNAL_STORAGE";
  private static final String PERM_ID_READ_STORAGE = "READ_EXTERNAL_STORAGE";
  private static final String PERM_ID_NOTIFICATIONS = "POST_NOTIFICATIONS";
  private static final String PERM_ID_INSTALL = "REQUEST_INSTALL_PACKAGES";

  private FragmentWelcomeBinding binding;
  private ActivityResultLauncher<Intent> storageLauncher;
  private ActivityResultLauncher<String[]> mediaLauncher;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    binding = FragmentWelcomeBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    styleColors();

    bindRow(
        binding.rowFiles,
        R.drawable.ic_onboarding_perm_files,
        R.string.onboarding_perm_files_title,
        R.string.onboarding_perm_files_desc,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ? PERM_ID_MANAGE_STORAGE
            : PERM_ID_READ_STORAGE,
        this::requestFiles);
    bindRow(
        binding.rowNotifications,
        R.drawable.ic_onboarding_perm_notifications,
        R.string.onboarding_perm_notifications_title,
        R.string.onboarding_perm_notifications_desc,
        PERM_ID_NOTIFICATIONS,
        this::requestNotifications);
    bindRow(
        binding.rowInstall,
        R.drawable.ic_onboarding_perm_install,
        R.string.onboarding_perm_install_title,
        R.string.onboarding_perm_install_desc,
        PERM_ID_INSTALL,
        this::requestInstallApps);

    storageLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> refreshGranted());
    mediaLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> refreshGranted());
    refreshGranted();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (binding != null) {
      refreshGranted();
      animateIn();
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void animateIn() {
    OnboardingAnim.slideUpFade(binding.ivLogo, 0);
    OnboardingAnim.slideUpFade(binding.tvHeader, 80);
  }

  private void styleColors() {
    int fallback = 0xFF202020;
    LinearLayout.LayoutParams logoParams =
        (LinearLayout.LayoutParams) binding.ivLogo.getLayoutParams();
    logoParams.gravity = LocaleDirection.startGravity(requireContext());
    binding.ivLogo.setLayoutParams(logoParams);
    binding.tvHeader.setTextColor(fallback(M3Theme.onSurface(), fallback));
    binding.tvDesc.setTextColor(fallback(M3Theme.onSurface(), fallback));
    int divider = fallback(M3Theme.outlineVariant(), fallback);
    binding.divider0.setBackgroundColor(divider);
    binding.divider1.setBackgroundColor(divider);
    binding.divider2.setBackgroundColor(divider);
  }

  private void bindRow(
      ItemOnboardingPermissionBinding row,
      int iconRes,
      int titleRes,
      int descRes,
      String permissionId,
      Runnable action) {
    int fallback = 0xFF202020;
    row.ivPermIcon.setImageResource(iconRes);
    tintIcon(row.ivPermIcon, fallback(M3Theme.primary(), fallback));
    row.tvPermTitle.setText(titleRes);
    row.tvPermTitle.setTextColor(fallback(M3Theme.onSurface(), fallback));
    row.tvPermDesc.setText(descRes);
    row.tvPermDesc.setTextColor(fallback(M3Theme.onSurface(), fallback));
    row.tvPermId.setText(permissionId);
    row.tvPermId.setGravity(LocaleDirection.startGravity(requireContext()));
    row.tvPermId.setTextColor(fallback(M3Theme.onSurface(), fallback));
    row.btnGrant.setOnClickListener(v -> action.run());
    LocaleDirection.mirrorRow(row.getRoot());
    alignGrantButton(row);
  }

  /**
   * Puts the grant button on the same side as the row text. The app always lays out left-to-right,
   * so a right-to-left language such as Persian needs the button pinned to the right explicitly.
   */
  private void alignGrantButton(ItemOnboardingPermissionBinding row) {
    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.btnGrant.getLayoutParams();
    params.gravity = LocaleDirection.startGravity(requireContext());
    row.btnGrant.setLayoutParams(params);
  }

  private void refreshGranted() {
    if (binding == null) return;
    int fallback = 0xFF202020;
    setGranted(binding.rowFiles, PermissionUtils.hasManageStoragePermission(requireContext()));
    boolean notified =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    setGranted(binding.rowNotifications, notified);
    setGranted(binding.rowInstall, requireContext().getPackageManager().canRequestPackageInstalls());
    binding.tvHeader.setTextColor(fallback(M3Theme.onSurface(), fallback));
  }

  private void setGranted(ItemOnboardingPermissionBinding row, boolean granted) {
    int fallback = 0xFF202020;
    int primary = fallback(M3Theme.primary(), fallback);
    row.btnGrant.setText(getString(granted ? R.string.onboarding_perm_granted : R.string.onboarding_perm_grant));
    row.btnGrant.setEnabled(!granted);
    row.btnGrant.setIconResource(granted ? R.drawable.check_24px : 0);
    row.btnGrant.setIconTint(ColorStateList.valueOf(primary));
    row.btnGrant.setStrokeColor(
        ColorStateList.valueOf(
            fallback(granted ? M3Theme.outlineVariant() : M3Theme.outline(), fallback)));
    row.btnGrant.setTextColor(primary);
  }

  private void requestFiles() {
    if (PermissionUtils.hasManageStoragePermission(requireContext())) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
      intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
      try {
        storageLauncher.launch(intent);
      } catch (ActivityNotFoundException e) {
        showPermissionSettingsMissing();
      }
    } else {
      mediaLauncher.launch(PermissionUtils.getRequiredPermissions());
    }
  }

  private void requestNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED) return;
    requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
  }

  private void requestInstallApps() {
    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
    intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
    try {
      startActivity(intent);
    } catch (ActivityNotFoundException e) {
      showPermissionSettingsMissing();
    }
  }

  private void showPermissionSettingsMissing() {
    GhostToast.makeText(
            requireContext(),
            getString(R.string.onboarding_perm_unavailable),
            GhostToast.LENGTH_SHORT)
        .show();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_NOTIFICATION && binding != null) refreshGranted();
  }

  private static void tintIcon(ImageView icon, int color) {
    if (icon != null) icon.setColorFilter(color);
  }

  private static int fallback(Integer value, int def) {
    return value != null ? value : def;
  }
}