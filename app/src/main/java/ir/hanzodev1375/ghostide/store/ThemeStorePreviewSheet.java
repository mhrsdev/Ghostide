package ir.hanzodev1375.ghostide.store;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import ir.hanzodev1375.components.sheet.BaseBlurBottomSheet;
import ir.hanzodev1375.components.store.model.ThemeItem;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.databinding.SheetThemeStorePreviewBinding;
import ir.theme.M3Theme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import ninja.coder.appuploader.main.appupdate.MarkwonHelper;

/**
 * Store theme preview: shows a store theme in a miniature Ghost IDE screen, lets the user step
 * through the store list without closing the sheet, and installs the theme through the existing
 * store install path.
 *
 * <p>Browsing never changes the active app theme: the preview is drawn from colours extracted by
 * {@link ThemeStorePreviewViewModel} into {@link ThemePreviewData}, and nothing global (M3Theme
 * preview state, the TextMate theme registry) is written until the user taps Apply.
 */
public class ThemeStorePreviewSheet extends BaseBlurBottomSheet {

  private static final String ARG_THEMES_JSON = "themes_json";
  private static final String ARG_INDEX = "index";
  private static final String STATE_INDEX = "state_index";
  private static final int DISABLED_ICON_ALPHA = 97;
  private static final int RIPPLE_ALPHA = 31;

  private SheetThemeStorePreviewBinding ui;
  private ThemeStorePreviewViewModel viewModel;
  private final List<ThemeItem> themes = new ArrayList<>();
  private int index;
  private boolean rtl;

  /** Preview of a single theme. */
  public static ThemeStorePreviewSheet newInstance(ThemeItem theme) {
    return newInstance(Collections.singletonList(theme), 0);
  }

  /**
   * Preview that starts at {@code themes.get(index)} and can step through {@code themes}.
   *
   * @param themes the store entries in the order the list shows them; must not be empty.
   */
  public static ThemeStorePreviewSheet newInstance(List<ThemeItem> themes, int index) {
    ThemeStorePreviewSheet sheet = new ThemeStorePreviewSheet();
    Bundle args = new Bundle();
    args.putString(ARG_THEMES_JSON, new Gson().toJson(themes));
    args.putInt(ARG_INDEX, index);
    sheet.setArguments(args);
    return sheet;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Bundle args = getArguments();
    if (args != null) {
      try {
        List<ThemeItem> parsed =
            new Gson()
                .fromJson(
                    args.getString(ARG_THEMES_JSON),
                    new TypeToken<List<ThemeItem>>() {}.getType());
        if (parsed != null) {
          for (ThemeItem item : parsed) {
            if (item != null) {
              themes.add(item);
            }
          }
        }
      } catch (JsonParseException e) {
        themes.clear();
      }
      index = args.getInt(ARG_INDEX, 0);
    }
    if (savedInstanceState != null) {
      index = savedInstanceState.getInt(STATE_INDEX, index);
    }
    index = Math.max(0, Math.min(index, themes.size() - 1));
    if (themes.isEmpty()) {
      dismissAllowingStateLoss();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(STATE_INDEX, index);
  }

  @Override
  protected void onContentReady(ViewGroup contentContainer) {
    if (themes.isEmpty()) {
      return;
    }
    ui = SheetThemeStorePreviewBinding.inflate(getLayoutInflater(), contentContainer, false);
    contentContainer.addView(
        ui.getRoot(),
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    rtl =
        TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
            == ViewCompat.LAYOUT_DIRECTION_RTL;
    if (rtl) {
      mirrorForRtl();
    }

    ui.closeButton.setOnClickListener(v -> dismiss());
    ui.prevButton.setOnClickListener(v -> step(rtl ? 1 : -1));
    ui.nextButton.setOnClickListener(v -> step(rtl ? -1 : 1));
    ui.retryButton.setOnClickListener(v -> select());
    ui.applyButton.setOnClickListener(v -> viewModel.install(current()));
    boolean browsing = themes.size() > 1;
    ui.pagerRow.setVisibility(browsing ? View.VISIBLE : View.GONE);

    M3Theme.apply(ui.getRoot());
    applyChromeColors();

    viewModel = new ViewModelProvider(this).get(ThemeStorePreviewViewModel.class);
    viewModel.getPreview().observe(getViewLifecycleOwner(), this::render);
    viewModel.getDescription().observe(getViewLifecycleOwner(), this::renderDescription);
    viewModel.getInstalling().observe(getViewLifecycleOwner(), busy -> renderApply());
    viewModel.getInstallResult().observe(getViewLifecycleOwner(), this::onInstallResult);
    select();
  }

  /**
   * Chrome colours that M3Theme's traversal does not cover for these views. The icon buttons are
   * turned back into standard M3 icon buttons: the traversal paints every MaterialButton as a
   * filled primary button.
   */
  private void applyChromeColors() {
    Integer outline = M3Theme.outlineVariant();
    if (outline != null) {
      ui.dragHandle.setBackgroundTintList(ColorStateList.valueOf(outline));
      ui.previewCanvas.setRingColor(outline);
    }
    Integer primary = M3Theme.primary();
    if (primary != null) {
      ui.previewProgress.setIndicatorColor(primary);
    }
    ui.previewCanvas.setLabelTypeface(ui.previewTitle.getTypeface());
    Integer onSurface = M3Theme.onSurface();
    if (onSurface != null) {
      ColorStateList iconTint =
          new ColorStateList(
              new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
              new int[] {
                ColorUtils.setAlphaComponent(onSurface, DISABLED_ICON_ALPHA), onSurface
              });
      ColorStateList ripple =
          ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, RIPPLE_ALPHA));
      for (MaterialButton button : new MaterialButton[] {ui.closeButton, ui.prevButton, ui.nextButton}) {
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setIconTint(iconTint);
        button.setRippleColor(ripple);
      }
    }
  }

  /**
   * The app keeps {@code supportsRtl} off, so rows are mirrored by hand for right-to-left locales:
   * the close button moves to the left, text is right aligned and the badges start on the right.
   */
  private void mirrorForRtl() {
    reverseChildren(ui.headerRow);
    reverseChildren(ui.badgeRow);
    int right = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
    ui.previewTitle.setGravity(right);
    ui.previewSubtitle.setGravity(right);
    ui.previewDescriptionLabel.setGravity(right);
    ui.previewDescription.setGravity(Gravity.RIGHT);
    ui.prevButton.setContentDescription(getString(R.string.theme_preview_next));
    ui.nextButton.setContentDescription(getString(R.string.theme_preview_previous));
  }

  private static void reverseChildren(ViewGroup group) {
    List<View> children = new ArrayList<>();
    for (int i = group.getChildCount() - 1; i >= 0; i--) {
      children.add(group.getChildAt(i));
    }
    group.removeAllViews();
    for (View child : children) {
      group.addView(child);
    }
  }

  private ThemeItem current() {
    return themes.get(index);
  }

  private void step(int delta) {
    int next = index + delta;
    if (next < 0 || next >= themes.size()) {
      return;
    }
    index = next;
    select();
  }

  /** Shows the theme at {@link #index}; after a failure it starts the download again. */
  private void select() {
    if (ui == null) {
      return;
    }
    ThemeItem item = current();
    ui.previewTitle.setText(item.name());
    ui.previewSubtitle.setText(
        getString(
            R.string.themes_dev_version,
            item.devname() != null ? item.devname() : "",
            item.version()));
    ui.positionText.setText(
        String.format(Locale.getDefault(), "%d / %d", index + 1, themes.size()));
    boolean canGoBack = index > 0;
    boolean canGoForward = index < themes.size() - 1;
    ui.prevButton.setEnabled(rtl ? canGoForward : canGoBack);
    ui.nextButton.setEnabled(rtl ? canGoBack : canGoForward);
    ui.previewCanvas.setContentDescription(
        getString(R.string.theme_preview_canvas_description, item.name()));
    viewModel.select(item, previewWidthPx());
    viewModel.loadDescription(item.doc());
  }

  private int previewWidthPx() {
    return getResources().getDisplayMetrics().widthPixels;
  }

  private void render(@Nullable ThemeStorePreviewViewModel.PreviewState state) {
    if (ui == null || state == null) {
      return;
    }
    if (!state.key.equals(ThemeStorePreviewViewModel.keyOf(current()))) {
      return;
    }
    boolean ready = state.state == ThemeStorePreviewViewModel.STATE_READY && state.data != null;
    boolean failed = state.state == ThemeStorePreviewViewModel.STATE_ERROR;
    ui.previewCanvas.setData(ready ? state.data : null);
    ui.statusPanel.setVisibility(ready ? View.GONE : View.VISIBLE);
    ui.previewProgress.setVisibility(failed ? View.GONE : View.VISIBLE);
    ui.retryButton.setVisibility(failed ? View.VISIBLE : View.GONE);
    if (failed) {
      ui.statusText.setText(R.string.theme_preview_failed);
    } else if (!ready) {
      if (state.progress > 0) {
        ui.previewProgress.setIndeterminate(false);
        ui.previewProgress.setProgressCompat(state.progress, true);
        ui.statusText.setText(getString(R.string.theme_preview_downloading_percent, state.progress));
      } else {
        ui.previewProgress.setIndeterminate(true);
        ui.statusText.setText(R.string.theme_preview_downloading);
      }
    }
    ui.badgeRow.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
    if (ready) {
      ui.modeBadge.setText(
          state.data.dark ? R.string.theme_preview_badge_dark : R.string.theme_preview_badge_light);
      ui.wallpaperBadge.setVisibility(state.data.wallpaper != null ? View.VISIBLE : View.GONE);
    }
    renderApply();
  }

  private void renderApply() {
    if (ui == null || viewModel == null) {
      return;
    }
    ThemeStorePreviewViewModel.PreviewState state = viewModel.getPreview().getValue();
    boolean ready =
        state != null
            && state.state == ThemeStorePreviewViewModel.STATE_READY
            && state.key.equals(ThemeStorePreviewViewModel.keyOf(current()));
    boolean busy = Boolean.TRUE.equals(viewModel.getInstalling().getValue());
    boolean active = viewModel.isActive(current());
    int label =
        busy
            ? R.string.theme_preview_applying
            : active ? R.string.theme_preview_active : R.string.theme_preview_apply;
    ui.applyButton.setText(label);
    ui.applyButton.setEnabled(ready && !busy && !active);
  }

  private void renderDescription(@Nullable String text) {
    if (ui == null) {
      return;
    }
    boolean empty = TextUtils.isEmpty(text);
    ui.previewDescriptionLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
    ui.descriptionScroll.setVisibility(empty ? View.GONE : View.VISIBLE);
    if (!empty) {
      MarkwonHelper.setMarkdown(ui.previewDescription, text);
      ui.descriptionScroll.scrollTo(0, 0);
    }
  }

  private void onInstallResult(@Nullable ThemeStorePreviewViewModel.InstallResult result) {
    if (result == null || ui == null) {
      return;
    }
    viewModel.consumeInstallResult();
    if (!result.success) {
      GhostToast.makeText(
              requireContext(), R.string.themes_download_failed, GhostToast.LENGTH_SHORT)
          .show();
      renderApply();
      return;
    }
    GhostToast.makeText(requireContext(), R.string.themes_applied, GhostToast.LENGTH_SHORT).show();
    dismissAllowingStateLoss();
  }

  @Override
  public void onDestroyView() {
    if (ui != null) {
      ui.previewCanvas.setData(null);
    }
    ui = null;
    super.onDestroyView();
  }
}
