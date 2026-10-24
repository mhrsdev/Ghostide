package ir.hanzodev1375.ghostide.activity;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.StringRes;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.components.childern.ViewChilder;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.adapters.ContributorAdapter;
import ir.hanzodev1375.ghostide.mvvm.viewmodel.AboutViewModel;
import ir.hanzodev1375.ghostide.utils.InfoRows;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;
import ir.theme.ThemeUtils;
import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * About screen: product name and version, the GitHub project (stars, contributors, source link) and
 * a grouped, collapsible list of device diagnostics shown as label/value rows.
 */
public class AboutActivity extends BaseCompat {

  private static final long BYTES_PER_MB = 1024L * 1024L;
  private static final long BYTES_PER_GB = BYTES_PER_MB * 1024L;
  private static final long SECTION_ANIM_MS = 200L;
  private static final float ARROW_EXPANDED_DEG = 180f;
  private static final String NOT_AVAILABLE = "N/A";

  private Section sectionDevice, sectionDisplay, sectionMemory, sectionStorage;
  private RecyclerView contributorList;
  private TextView contributorStatus, starsValue;
  private ContributorAdapter contributorAdapter;
  private AboutViewModel viewModel;
  private boolean rtl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);
    rtl = LocaleDirection.isRtl(this);
    setupBackgroundBlur();

    View root = findViewById(R.id.root_scroll);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
          int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
          v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
          return insets;
        });

    contributorList = findViewById(R.id.rv_contributors);
    contributorStatus = findViewById(R.id.tv_contributors_status);
    starsValue = findViewById(R.id.tv_stars_value);
    sectionDevice = new Section(R.id.section_device_info, R.string.diag_device_info);
    sectionDisplay = new Section(R.id.section_display, R.string.diag_display);
    sectionMemory = new Section(R.id.section_memory, R.string.diag_memory);
    sectionStorage = new Section(R.id.section_storage, R.string.diag_storage);

    alignForReading();
    fillVersion();
    fillDiagnostics();
    setupSource();
    setupContributors();
    sectionDevice.setExpanded(true, false);

    M3Theme.apply(root);
    applyAccents();
  }

  @Override
  protected void applyOwnTheme(ThemeUtils themeUtils) {
    applyAccents();
  }

  private void setupBackgroundBlur() {
    ViewChilder background = findViewById(R.id.backgroundIconAbout);
    View content = findViewById(R.id.root_scroll);
    if (background == null || content == null) return;
    setupBackgroundBlur(background, content);
  }

  /**
   * Mirrors the icon/label/value rows for right-to-left languages; the app runs with RTL support
   * off, so this is done explicitly rather than through start/end resolution.
   */
  private void alignForReading() {
    int startGravity = LocaleDirection.startGravity(this);
    int endGravity = rtl ? Gravity.LEFT : Gravity.RIGHT;
    LocaleDirection.mirrorRow(findViewById(R.id.aboutHeader));
    LocaleDirection.mirrorRow(findViewById(R.id.rowStars));
    LocaleDirection.mirrorRow(findViewById(R.id.rowContributors));
    LocaleDirection.mirrorRow(findViewById(R.id.rowSource));
    int[] startAligned = {
      R.id.tvAboutName, R.id.tv_about_version, R.id.tvSectionProject, R.id.tvSectionDiagnostics
    };
    for (int id : startAligned) {
      ((TextView) findViewById(id)).setGravity(startGravity);
    }
    ((TextView) findViewById(R.id.tvSourceValue)).setGravity(endGravity | Gravity.CENTER_VERTICAL);
  }

  private void fillVersion() {
    TextView version = findViewById(R.id.tv_about_version);
    String name = "";
    long code = 0;
    try {
      PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
      if (info.versionName != null) name = info.versionName;
      code = PackageInfoCompat.getLongVersionCode(info);
    } catch (PackageManager.NameNotFoundException e) {
      name = "";
    }
    version.setText(getString(R.string.about_version_line, name, String.valueOf(code)));
  }

  private void setupSource() {
    TextView value = findViewById(R.id.tvSourceValue);
    value.setText(AboutViewModel.REPOSITORY_URL.replace("https://", ""));
    findViewById(R.id.rowSource)
        .setOnClickListener(
            v -> {
              Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(AboutViewModel.REPOSITORY_URL));
              try {
                startActivity(open);
              } catch (ActivityNotFoundException e) {
                GhostToast.makeText(
                        this, getString(R.string.about_unavailable), GhostToast.LENGTH_SHORT)
                    .show();
              }
            });
  }

  private void setupContributors() {
    contributorAdapter = new ContributorAdapter();
    contributorList.setLayoutManager(
        new LinearLayoutManager(this, RecyclerView.HORIZONTAL, rtl));
    contributorList.setAdapter(contributorAdapter);

    viewModel = new ViewModelProvider(this).get(AboutViewModel.class);
    viewModel
        .getStars()
        .observe(
            this,
            value -> {
              if (value == null || value < 0) {
                starsValue.setTypeface(Typeface.DEFAULT);
                starsValue.setText(R.string.about_unavailable);
              } else {
                starsValue.setTypeface(Typeface.MONOSPACE);
                starsValue.setText(NumberFormat.getIntegerInstance(Locale.US).format(value));
              }
            });
    viewModel
        .getContributors()
        .observe(
            this,
            contributors -> {
              if (contributors == null) return;
              if (contributors.isEmpty()) {
                contributorStatus.setText(R.string.about_unavailable);
                contributorList.setVisibility(View.GONE);
                return;
              }
              contributorStatus.setTypeface(Typeface.MONOSPACE);
              contributorStatus.setText(
                  NumberFormat.getIntegerInstance(Locale.US).format(contributors.size()));
              contributorList.setVisibility(View.VISIBLE);
              contributorAdapter.setItems(contributors);
            });
    viewModel.load();
  }

  /** Accent colours that the generic M3Theme pass resets; re-applied after every theme pass. */
  private void applyAccents() {
    Integer primary = M3Theme.primary();
    Integer muted = M3Theme.onSurfaceVariant();
    Integer outline = M3Theme.outlineVariant();
    if (primary != null) {
      ((TextView) findViewById(R.id.tvSectionProject)).setTextColor(primary);
      ((TextView) findViewById(R.id.tvSectionDiagnostics)).setTextColor(primary);
      ((TextView) findViewById(R.id.tvSourceValue)).setTextColor(primary);
    }
    if (muted != null) {
      int[] icons = {R.id.ivStars, R.id.ivContributors, R.id.ivSource};
      for (int id : icons) {
        ((ImageView) findViewById(id)).setColorFilter(muted);
      }
    }
    if (outline != null) {
      int[] dividers = {
        R.id.dividerProject1,
        R.id.dividerProject2,
        R.id.dividerDiag1,
        R.id.dividerDiag2,
        R.id.dividerDiag3
      };
      for (int id : dividers) {
        findViewById(id).setBackgroundColor(outline);
      }
    }
    Section[] sections = {sectionDevice, sectionDisplay, sectionMemory, sectionStorage};
    for (Section section : sections) {
      if (section != null && muted != null) {
        section.arrow.setColorFilter(muted);
      }
    }
  }

  private void fillDiagnostics() {
    sectionDevice.summary.setText(Build.MODEL);
    LinearLayout device = sectionDevice.body;
    InfoRows.add(device, R.string.info_model, Build.MODEL);
    InfoRows.add(device, R.string.info_manufacturer, Build.MANUFACTURER);
    InfoRows.add(device, R.string.info_codename, Build.DEVICE);
    InfoRows.add(device, R.string.info_brand, Build.BRAND);
    InfoRows.add(
        device,
        R.string.info_android,
        Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
    InfoRows.add(device, R.string.info_arch, System.getProperty("os.arch", "unknown"));
    InfoRows.add(device, R.string.info_hardware, Build.HARDWARE);
    InfoRows.add(device, R.string.info_abis, String.join(", ", Build.SUPPORTED_ABIS));

    fillDisplay();
    fillMemory();
    fillStorage();
  }

  private void fillDisplay() {
    LinearLayout body = sectionDisplay.body;
    DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
    Display display = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
    String refresh = display != null ? Math.round(display.getRefreshRate()) + " Hz" : NOT_AVAILABLE;
    sectionDisplay.summary.setText(refresh);
    InfoRows.add(body, R.string.info_refresh_rate, refresh);

    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    String glVersion = am != null ? am.getDeviceConfigurationInfo().getGlEsVersion() : null;
    InfoRows.add(body, R.string.info_opengl, glVersion != null ? glVersion : NOT_AVAILABLE);
    boolean vulkan = getPackageManager().hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL);
    InfoRows.add(body, R.string.info_vulkan, yesNo(vulkan));
    InfoRows.add(body, R.string.info_hdr, yesNo(display != null && display.isHdr()));
    InfoRows.add(body, R.string.info_wide_color, yesNo(display != null && display.isWideColorGamut()));
  }

  private void fillMemory() {
    LinearLayout body = sectionMemory.body;
    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
    if (am != null) am.getMemoryInfo(memInfo);
    boolean known = memInfo.totalMem > 0;
    sectionMemory.summary.setText(
        known
            ? String.format(Locale.US, "%.1f GB", memInfo.totalMem / (double) BYTES_PER_GB)
            : NOT_AVAILABLE);
    InfoRows.add(
        body,
        R.string.info_total_memory,
        known ? memInfo.totalMem / BYTES_PER_MB + " MB" : NOT_AVAILABLE);
    InfoRows.add(body, R.string.info_low_ram, yesNo(am != null && am.isLowRamDevice()));
    InfoRows.add(body, R.string.info_large_heap, yesNo(isLargeHeap()));
    InfoRows.add(body, R.string.info_process, processAbi());
  }

  private void fillStorage() {
    LinearLayout body = sectionStorage.body;
    StatFs internal = new StatFs(Environment.getDataDirectory().getPath());
    long intFree = internal.getFreeBytes() / BYTES_PER_GB;
    sectionStorage.summary.setText(getString(R.string.about_free_summary, String.valueOf(intFree)));
    InfoRows.add(body, R.string.info_internal_total, internal.getTotalBytes() / BYTES_PER_GB + " GB");
    InfoRows.add(body, R.string.info_internal_free, intFree + " GB");

    File extDir = getExternalFilesDir(null);
    String extTotal = NOT_AVAILABLE;
    String extFree = NOT_AVAILABLE;
    if (extDir != null) {
      StatFs ext = new StatFs(extDir.getPath());
      extTotal = ext.getTotalBytes() / BYTES_PER_GB + " GB";
      extFree = ext.getFreeBytes() / BYTES_PER_GB + " GB";
    }
    InfoRows.add(body, R.string.info_external_total, extTotal);
    InfoRows.add(body, R.string.info_external_free, extFree);
  }

  private String yesNo(boolean value) {
    return getString(value ? R.string.yes : R.string.no);
  }

  private static String processAbi() {
    boolean is64 = Process.is64Bit();
    String[] abis = is64 ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
    String abi = abis.length > 0 ? abis[0] : "unknown";
    return abi + ", " + (is64 ? "64-bit" : "32-bit");
  }

  private boolean isLargeHeap() {
    return (getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
  }

  /** One collapsible diagnostics group: a header row (title, summary, arrow) and its rows. */
  private final class Section {
    final View header;
    final TextView summary;
    final ImageView arrow;
    final LinearLayout body;
    private boolean expanded;

    Section(int includeId, @StringRes int title) {
      View root = findViewById(includeId);
      header = root.findViewById(R.id.section_header);
      summary = root.findViewById(R.id.section_summary);
      arrow = root.findViewById(R.id.section_arrow);
      body = root.findViewById(R.id.section_body);
      TextView titleView = root.findViewById(R.id.section_title);
      titleView.setText(title);
      LocaleDirection.mirrorRow((LinearLayout) header);
      summary.setGravity((rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
      header.setOnClickListener(v -> setExpanded(!expanded, true));
      updateState();
    }

    void setExpanded(boolean expand, boolean animate) {
      expanded = expand;
      if (animate && getAnimationManager().areAnimationsEnabled()) {
        ViewGroup column = findViewById(R.id.aboutColumn);
        TransitionManager.beginDelayedTransition(
            column, new AutoTransition().setDuration(SECTION_ANIM_MS));
        arrow.animate().rotation(expand ? ARROW_EXPANDED_DEG : 0f).setDuration(SECTION_ANIM_MS);
      } else {
        arrow.setRotation(expand ? ARROW_EXPANDED_DEG : 0f);
      }
      body.setVisibility(expand ? View.VISIBLE : View.GONE);
      updateState();
    }

    private void updateState() {
      ViewCompat.setStateDescription(
          header, getString(expanded ? R.string.about_expanded : R.string.about_collapsed));
    }
  }
}
