package ir.hanzodev1375.ghostide.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import ir.hanzodev1375.ghostide.databinding.FragmentTerminalSessionBinding;
import ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity;
import ir.theme.GhostTheme;
import ir.theme.M3Theme;
import ir.theme.ThemeManager;
import ir.theme.ThemeUtils;

public class TerminalSessionFragment extends Fragment
    implements GhostTerminalViewClient.KeyModifierState {

  private static final String ARG_SESSION_ID = "session_id";
  private static final int TERMINAL_SWIPE_MAX_MS = 350;
  private static final int WALLPAPER_SCRIM_ALPHA = 235;
  private static final int FALLBACK_TERMINAL_BACKGROUND = 0xFF121212;

  private FragmentTerminalSessionBinding binding;
  private GhostTerminalViewClient viewClient;
  private TerminalViewModel viewModel;
  private int sessionId = -1;
  private boolean refreshPending;

  /** Coalesces every PTY chunk that arrives within one frame into a single redraw. */
  private final Runnable frameRefresh =
      () -> {
        refreshPending = false;
        if (binding != null) binding.terminalView.onScreenUpdated();
      };

  private final TerminalViewModel.ScreenRefresher screenRefresher =
      () -> {
        if (binding == null || refreshPending) return;
        refreshPending = true;
        binding.terminalView.postOnAnimation(frameRefresh);
      };

  public static TerminalSessionFragment newInstance(int sessionId) {
    TerminalSessionFragment fragment = new TerminalSessionFragment();
    Bundle args = new Bundle();
    args.putInt(ARG_SESSION_ID, sessionId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getArguments() != null) {
      sessionId = getArguments().getInt(ARG_SESSION_ID, -1);
    }
    viewModel = new ViewModelProvider(requireActivity()).get(TerminalViewModel.class);
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    binding = FragmentTerminalSessionBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    setupTerminalView();
    attachSession();
  }

  private void setupTerminalView() {
    viewClient = new GhostTerminalViewClient(binding.terminalView, this);
    int savedFontSize = viewModel.getFontSizeSp();
    if (savedFontSize > 0) viewClient.setFontSizeSp(savedFontSize);
    viewClient.setOnFontSizeChangedListener(viewModel::setFontSizeSp);
    binding.terminalView.setTerminalViewClient(viewClient);
    TerminalColorsUtil.apply(requireActivity(), requireContext());
    applyTerminalBackground();
    binding.terminalView.setOnCreateContextMenuListener(this::onCreateTerminalContextMenu);

    binding.terminalView.setOnTouchListener(
        (v, event) -> {
          detectTerminalSwipe(event);
          return false;
        });
  }

  /**
   * The terminal surface is always a calm opaque tone so output stays readable; with a wallpaper
   * the same tone is used as a dense scrim instead of a fully transparent background.
   */
  private void applyTerminalBackground() {
    if (binding == null) return;
    boolean hasImage = false;
    try {
      GhostTheme theme = new ThemeUtils(new ThemeManager(requireContext())).getTheme();
      hasImage =
          new PreferencesUtils(requireContext()).isShowBackground()
              && theme != null
              && theme.getWidget() != null
              && theme.getWidget().getImagepath() != null
              && !theme.getWidget().getImagepath().isEmpty();
    } catch (RuntimeException e) {
      hasImage = false;
    }
    Integer bg = M3Theme.surfaceContainerLowest();
    if (bg == null) bg = M3Theme.surface();
    int color = bg != null ? bg : FALLBACK_TERMINAL_BACKGROUND;
    if (hasImage) color = ColorUtils.setAlphaComponent(color, WALLPAPER_SCRIM_ALPHA);
    binding.getRoot().setBackgroundColor(color);
    binding.terminalView.setBackgroundColor(hasImage ? Color.TRANSPARENT : color);
  }

  private void onCreateTerminalContextMenu(
      ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
    if (binding == null) return;
    TerminalView terminalView = binding.terminalView;
    String selected = terminalView.getStoredSelectedText();
    terminalView.unsetStoredSelectedText();
    if (selected != null && !selected.isEmpty()) {
      menu.add(R.string.terminal_share_selection)
          .setOnMenuItemClickListener(
              item -> {
                shareText(selected);
                return true;
              });
    }
    menu.add(R.string.terminal_copy_all_output)
        .setOnMenuItemClickListener(
            item -> {
              copyAllOutput();
              return true;
            });
    menu.add(R.string.terminal_paste)
        .setOnMenuItemClickListener(
            item -> {
              TerminalSession session = viewModel.getSessionById(sessionId);
              if (session != null) session.onPasteTextFromClipboard();
              return true;
            });
  }

  private void copyAllOutput() {
    TerminalSession session = viewModel.getSessionById(sessionId);
    TerminalEmulator emulator = session != null ? session.getEmulator() : null;
    if (emulator == null) return;
    String text = emulator.getScreen().getTranscriptTextWithFullLinesJoined();
    ClipboardManager clipboard =
        (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null || text == null) return;
    clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text));
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      GhostToast.makeText(
              requireActivity(), getString(R.string.terminal_copied), GhostToast.LENGTH_SHORT)
          .show();
    }
  }

  private void shareText(String text) {
    Intent send = new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(Intent.EXTRA_TEXT, text);
    startActivity(Intent.createChooser(send, getString(R.string.terminal_share_selection)));
  }

  public void attachSession() {
    if (binding == null) return;
    TerminalSession session = viewModel.getSessionById(sessionId);
    if (session == null) return;
    TerminalColorsUtil.refreshSession(session);
    binding.terminalView.attachSession(session);
    binding.terminalView.invalidate();
    viewModel.registerScreenRefresher(sessionId, screenRefresher);
  }

  public TerminalView getTerminalView() {
    return binding != null ? binding.terminalView : null;
  }

  public void sendKeyEvent(int keyCode) {
    if (binding == null || binding.terminalView == null) return;
    long now = SystemClock.uptimeMillis();
    binding.terminalView.dispatchKeyEvent(
        new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
    binding.terminalView.dispatchKeyEvent(
        new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
  }

  private float terminalDownX;
  private float terminalDownY;
  private long terminalDownTime;

  private void detectTerminalSwipe(MotionEvent event) {
    int touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        terminalDownX = event.getRawX();
        terminalDownY = event.getRawY();
        terminalDownTime = event.getEventTime();
        break;
      case MotionEvent.ACTION_UP:
        if (event.getEventTime() - terminalDownTime > TERMINAL_SWIPE_MAX_MS) break;
        float dx = event.getRawX() - terminalDownX;
        float dy = event.getRawY() - terminalDownY;
        if (Math.abs(dx) <= touchSlop * 2 || Math.abs(dx) <= Math.abs(dy) * 1.6f) break;
        if (getActivity() instanceof TerminalActivity) {
          TerminalActivity activity = (TerminalActivity) getActivity();
          if (dx < 0) {
            activity.getInputDock().showInputPage();
          } else {
            activity.getInputDock().showButtonsPage();
          }
        }
        break;
      default:
        break;
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (binding != null) {
      int savedFontSize = viewModel.getFontSizeSp();
      if (viewClient != null && savedFontSize > 0) viewClient.setFontSizeSp(savedFontSize);
      binding.terminalView.post(
          () -> {
            if (binding != null && binding.terminalView != null) {
              binding.terminalView.setTerminalCursorBlinkerState(true, false);
              binding.terminalView.onScreenUpdated();
              binding.terminalView.requestFocus();
            }
          });
    }
  }

  @Override
  public void onPause() {
    if (binding != null && binding.terminalView != null) {
      binding.terminalView.setTerminalCursorBlinkerState(false, false);
    }
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    viewModel.unregisterScreenRefresher(sessionId);
    if (binding != null) {
      binding.terminalView.removeCallbacks(frameRefresh);
      binding.terminalView.setOnCreateContextMenuListener(null);
      binding.terminalView.setTerminalViewClient(null);
    }
    refreshPending = false;
    if (viewClient != null) viewClient.setOnFontSizeChangedListener(null);
    viewClient = null;
    super.onDestroyView();
    binding = null;
  }

  @Override
  public boolean isCtrlToggled() {
    return viewModel.isCtrlToggled();
  }

  @Override
  public boolean isAltToggled() {
    return viewModel.isAltToggled();
  }

  @Override
  public void consumeCtrlToggle() {
    viewModel.consumeCtrlToggle();
  }

  @Override
  public void consumeAltToggle() {
    viewModel.consumeAltToggle();
  }
}
