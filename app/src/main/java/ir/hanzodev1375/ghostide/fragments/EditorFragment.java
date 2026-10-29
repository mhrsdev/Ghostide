package ir.hanzodev1375.ghostide.fragments;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import com.blankj.utilcode.util.ThreadUtils;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.EditorFocusChangeEvent;
import ir.hanzodev1375.ghostide.activity.EditorActivity;
import ir.hanzodev1375.ghostide.codeeditors.langs.lsp.AndroidClasspathResolver;
import ir.hanzodev1375.ghostide.codeeditors.langs.lsp.LspRouter;
import ir.hanzodev1375.ghostide.dialogs.DiagnosticsBottomSheet;
import ir.hanzodev1375.ghostide.editorlangs.LanguageManager;
import ir.hanzodev1375.ghostide.listeners.LspDiagnosticsEventListener;
import ir.hanzodev1375.ghostide.codeeditors.IdeEditor;
import io.github.rosemoe.sora.lsp.editor.LspEditor;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import ir.hanzodev1375.ghostide.databinding.EditorFragmentBinding;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.mvvm.viewmodel.EditorViewModel;
import ir.hanzodev1375.ghostide.mvvm.viewmodel.LspViewModel;
import ir.hanzodev1375.ghostide.models.LspState;
import ir.hanzodev1375.ghostide.paged.PagedEditSession;
import ir.hanzodev1375.ghostide.refactor.renameclass.ui.RenameClassBottomSheet;
import ir.hanzodev1375.ghostide.tasks.FileChangeReceiver;
import ir.theme.ThemeManager;
import ir.theme.ThemeUtils;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import ir.hanzodev1375.ghostide.R;
import java.io.Reader;
import ir.hanzodev1375.components.WebViewBottomSheetFragment;
import ir.hanzodev1375.components.views.GhostToast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.lsp4j.DiagnosticSeverity;
import ir.hanzodev1375.ghostide.codeeditors.langs.lsp.model.BreadcrumbItem;

public class EditorFragment extends Fragment {

  private static File findSourceRootForPackageDirectory(File directory) {
    File current = directory;
    while (current != null) {
      String name = current.getName();
      if (name.equals("java") || name.equals("kotlin")) {
        File parent = current.getParentFile();
        File grandParent = parent != null ? parent.getParentFile() : null;
        if (grandParent != null && grandParent.getName().equals("src")) {
          return current;
        }
      }
      current = current.getParentFile();
    }
    return null;
  }

  private static final long PAGED_EDIT_THRESHOLD = 2L * 1024 * 1024;
  private static final int PAGED_EDIT_PAGE_SIZE = 1024 * 1024;
  private ExecutorService pagedExecutor;
  private EditorFragmentBinding binding;
  private EditorViewModel viewModel;
  private IdeEditor editor;
  private String filePath;
  private long lastKnownModifiedTime = -1;
  private ThemeUtils theme;
  private PreferencesUtils setting;
  private PagedEditSession pagedSession;
  private int pageIndex = -1;
  private LspViewModel lspViewModel;
  private LspDiagnosticsEventListener diagnosticsListener;
  private boolean readOnly;
  private boolean contentLoaded;
  private boolean loadFailed;
  private int pendingLine = -1;
  private int pendingColumn = -1;
  private static final int DIAGNOSTICS_COLOR_OK = Color.parseColor("#4CAF50");
  private static final int DIAGNOSTICS_COLOR_ERROR = Color.parseColor("#F44336");
  private final Handler breadcrumbHandler = new Handler(Looper.getMainLooper());
  private static final long BREADCRUMB_DEBOUNCE_MS = 300;
  private final Runnable breadcrumbRefreshRunnable = this::refreshBreadcrumbs;

  public static EditorFragment newInstance(String path) {
    return newInstance(path, false);
  }

  public static EditorFragment newInstance(String path, boolean readOnly) {
    EditorFragment f = new EditorFragment();
    Bundle args = new Bundle();
    args.putString("file_path", path);
    args.putBoolean("read_only", readOnly);
    f.setArguments(args);
    return f;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = EditorFragmentBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    filePath = getArguments().getString("file_path");
    readOnly = getArguments().getBoolean("read_only", false);
    contentLoaded = false;
    loadFailed = false;
    viewModel = new ViewModelProvider(this).get(EditorViewModel.class);
    lspViewModel = new ViewModelProvider(this).get(LspViewModel.class);
    editor = binding.editor;
    var manager = new ThemeManager(requireActivity());
    theme = new ThemeUtils(manager);
    theme.applyEditor(editor);
    applyImeInsets(binding.getRoot());
    setting = new PreferencesUtils(getContext());
    editor.subscribeEvent(
        ContentChangeEvent.class,
        (event, unevent) -> {
          if (event.getAction() == ContentChangeEvent.ACTION_SET_NEW_TEXT) return;
          if (getActivity() instanceof EditorActivity && filePath != null) {
            ((EditorActivity) getActivity()).setTabDirty(filePath, true);
          }
          if (setting.autoSaveFiles()) {
            saveCurrentFile();
          }
          scheduleBreadcrumbRefresh();
        });
    viewModel
        .getLoading()
        .observe(
            getViewLifecycleOwner(),
            loading -> {
              binding.prograssLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            });

    viewModel
        .getText()
        .observe(
            getViewLifecycleOwner(),
            content -> {
              if (content == null) {
                onContentLoadFailed();
                return;
              }
              var b = new Bundle();
              b.putString("path", filePath);
              editor.setText(content, b);
              onContentLoaded();
              updateKnownModifiedTime();
              applyPendingJump();
            });

    if (filePath != null) {
      File file = new File(filePath);
      if (file.exists() && file.length() > PAGED_EDIT_THRESHOLD) {
        openPagedSession(file);
      } else {
        viewModel.loadFile(filePath);
      }
    }

    LanguageManager.resolveAsync(
        getContext(),
        filePath,
        lang -> {
          if (lang != null && editor != null) {
            editor.setEditorLanguage(lang);
          }
        });

    applyReadOnly();

    editor.setOnSaveRequest(this::saveCurrentFile);
    editor.setOnSearchRequest(
        () -> {
          if (getActivity() instanceof EditorActivity) {
            ((EditorActivity) getActivity()).showEditorSearch();
          }
        });
    editor.setOnGotoLineRequest(
        () -> {
          if (getActivity() instanceof EditorActivity) {
            ((EditorActivity) getActivity()).showGotoLineDialog();
          }
        });

    if (LspRouter.isSupportedFile(filePath)) {
      File targetFile = new File(filePath);
      String projectRoot = AndroidClasspathResolver.findProjectRoot(targetFile).getAbsolutePath();
      lspViewModel.connect(projectRoot, filePath, editor);

      lspViewModel
          .getState()
          .observe(
              getViewLifecycleOwner(),
              state -> {
                if (state == null) return;
                if (state.isConnected()) {
                  LspEditor connected = lspViewModel.getLspEditor();
                  if (connected != null) {
                    editor.setLspEditor(connected);
                    lspViewModel.applySettings(
                        setting.isHover(), setting.isInlayHint(), setting.isSignatureHelp());
                    if (setting.isDiagnostics()) {
                      connected.setDiagnostics(connected.getDiagnostics());
                    } else {
                      connected.setDiagnostics(Collections.emptyList());
                    }
                    updateDiagnosticsIcon(connected);
                    registerDiagnosticsListener(connected);
                    scheduleBreadcrumbRefresh();
                  }
                } else if (state.hasError()) {
                  Log.e("EditorFragment", "LSP Error: " + state.errorMessage);
                }
              });
    }

    theme.applyCursorPositionPill(binding.tvCursorPosition);
    editor.subscribeEvent(
        SelectionChangeEvent.class,
        (event, unevent) -> {
          var cursor = editor.getCursor();
          binding.tvCursorPosition.setText(
              "L " + (cursor.getLeftLine() + 1) + ", C " + (cursor.getLeftColumn() + 1));
          scheduleBreadcrumbRefresh();
        });
    binding.tvCursorPosition.setVisibility(
        setting.getShowLineColPanel() ? View.VISIBLE : View.GONE);
    binding.dlch.setOnClickListener(v -> showDiagnosticsSheet());
    editor.subscribeEvent(
        LongPressEvent.class,
        (event, unevent) -> {
          if (filePath == null) return;
          if (!filePath.endsWith(".java") && !filePath.endsWith(".kt")) return;
          File file = new File(filePath);
          File sourceRoot = findSourceRootForPackageDirectory(file.getParentFile());
          File moduleRoot =
              sourceRoot != null
                      && sourceRoot.getParentFile() != null
                      && sourceRoot.getParentFile().getParentFile() != null
                  ? sourceRoot.getParentFile().getParentFile().getParentFile()
                  : file.getParentFile();
          if (moduleRoot == null || getActivity() == null) return;
          String name = file.getName();
          int dot = name.lastIndexOf('.');
          String className = dot > 0 ? name.substring(0, dot) : name;
          RenameClassBottomSheet.newInstance(
                  moduleRoot.getAbsolutePath(), file.getAbsolutePath(), className)
              .show(getActivity().getSupportFragmentManager(), RenameClassBottomSheet.TAG);
        });

    binding.ivWrapToggle1.setOnClickListener(v -> goToPreviousPage());
    binding.ivWrapToggle2.setOnClickListener(v -> goToNextPage());
    theme.applyViewPagePanel(
        binding.ivWrapToggle1, binding.ivWrapToggle2, binding.tvWrapInfo, binding.llWrapIndicator);
    if (filePath != null) {
      binding.editor.setCurrentFilePath(filePath);
    }
    binding.editor.setOnLinkClick(
        links -> {
          var webs = WebViewBottomSheetFragment.newInstance(links);
          theme.applyWebViewBottomSheetFragment(webs);
          webs.show(getParentFragmentManager(), "WebViewBottomSheet");
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    if (editor != null && theme != null) {
      theme.applyEditor(editor);
    }
    checkExternalChangesOnResume();
    refreshFileWatching();
  }

  private void checkExternalChangesOnResume() {
    if (filePath == null) return;
    File file = new File(filePath);
    if (!file.exists()) return;

    long diskModifiedTime = file.lastModified();
    if (lastKnownModifiedTime > 0 && diskModifiedTime != lastKnownModifiedTime) {
      FileChangeReceiver.showFileChangedDialog(
          requireActivity(),
          filePath,
          this::reloadFromDisk);
    }
    lastKnownModifiedTime = diskModifiedTime;
  }

  private void updateKnownModifiedTime() {
    if (filePath != null) {
      lastKnownModifiedTime = new File(filePath).lastModified();
    }
  }

  private void refreshFileWatching() {
    FileChangeReceiver.stopWatching();
    if (filePath != null && !setting.autoSaveFiles()) {
      FileChangeReceiver.startWatching(
          requireActivity(),
          filePath,
          changedPath ->
              FileChangeReceiver.showFileChangedDialog(
                  requireActivity(),
                  changedPath,
                  this::reloadFromDisk));
    }
  }

  /**
   * Re-reads the file after it was changed by another app. A file opened in pages is split again
   * instead of being loaded whole into the editor, because the open page session would otherwise
   * write the whole file into the current page on the next save.
   */
  private void reloadFromDisk() {
    if (binding == null || filePath == null) return;
    if (pagedSession != null) {
      pagedSession.close();
      pagedSession = null;
      pageIndex = -1;
      contentLoaded = false;
      binding.llWrapIndicator.setVisibility(View.GONE);
      openPagedSession(new File(filePath));
    } else if (viewModel != null) {
      viewModel.loadFile(filePath);
    }
  }

  /** Marks the editor content as a faithful copy of the file, which makes saving it safe. */
  private void onContentLoaded() {
    contentLoaded = true;
    if (loadFailed) {
      loadFailed = false;
      if (!readOnly && editor != null) editor.setEditable(true);
    }
  }

  /**
   * Handles a file that could not be read. If nothing was loaded yet, the empty editor is locked so
   * typing or auto-save cannot overwrite the real file with an empty buffer; after a failed reload
   * the previously loaded content stays editable.
   */
  private void onContentLoadFailed() {
    Context context = getContext();
    if (context == null || editor == null) return;
    String name = filePath != null ? new File(filePath).getName() : "";
    if (!contentLoaded) {
      loadFailed = true;
      editor.setEditable(false);
    }
    GhostToast.makeText(
            context, getString(R.string.audit_editor_open_failed, name), GhostToast.LENGTH_LONG)
        .show();
  }

  private void showSaveFailed(IOException e) {
    Context context = getContext();
    if (context == null) return;
    String name = filePath != null ? new File(filePath).getName() : "";
    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    GhostToast.makeText(
            context,
            getString(R.string.audit_editor_save_failed, name, reason),
            GhostToast.LENGTH_LONG)
        .show();
  }

  /** The save replaced the file on disk, so a live observer of the old file has to be re-armed. */
  private void onSavedToDisk() {
    updateKnownModifiedTime();
    if (getActivity() instanceof EditorActivity) {
      ((EditorActivity) getActivity()).setTabDirty(filePath, false);
    }
    if (isAdded() && FileChangeReceiver.isWatching(filePath)) {
      refreshFileWatching();
    }
  }

  /**
   * Splits a large file into pages on a background thread and loads the first page into the
   * editor.
   *
   * <p>The session is published to the fragment only from the main thread, and only while the view
   * still exists; a session that finishes after the view was destroyed is closed immediately so it
   * cannot leak or be assigned after {@link #onDestroyView()} cleared the field. The executor is
   * created on demand because the same fragment instance can get a new view after its old one was
   * destroyed.
   */
  private void openPagedSession(File file) {
    var context = requireContext();
    binding.prograssLoading.setVisibility(View.VISIBLE);
    if (pagedExecutor == null || pagedExecutor.isShutdown()) {
      pagedExecutor = Executors.newSingleThreadExecutor();
    }
    pagedExecutor.execute(
        () -> {
          try {
            File tmpDir =
                new File(context.getCacheDir(), "paged_edit_" + System.currentTimeMillis());
            PagedEditSession session;
            try (Reader reader = new FileReader(file)) {
              session = new PagedEditSession(reader, tmpDir, PAGED_EDIT_PAGE_SIZE);
            }
            FragmentActivity host = getActivity();
            if (host == null) {
              session.close();
              return;
            }
            host.runOnUiThread(() -> attachPagedSession(session));
          } catch (IOException e) {
            Log.e("EditorFragment", "خطا در باز کردن فایل بزرگ", e);
            FragmentActivity host = getActivity();
            if (host == null) return;
            host.runOnUiThread(
                () -> {
                  if (binding == null) return;
                  binding.prograssLoading.setVisibility(View.GONE);
                  onContentLoadFailed();
                });
          }
        });
  }

  private void attachPagedSession(PagedEditSession session) {
    if (binding == null) {
      session.close();
      return;
    }
    pagedSession = session;
    pageIndex = 0;
    session.loadPageToEditor(
        0,
        editor,
        new PagedEditSession.Callback() {
          @Override
          public void onSuccess() {
            if (binding == null) return;
            binding.prograssLoading.setVisibility(View.GONE);
            binding.llWrapIndicator.setVisibility(View.VISIBLE);
            onContentLoaded();
            updatePageIndicator();
            updateKnownModifiedTime();
          }

          @Override
          public void onError(IOException e) {
            Log.e("EditorFragment", "خطا در صفحه‌بندی فایل", e);
            if (binding == null) return;
            binding.prograssLoading.setVisibility(View.GONE);
            onContentLoadFailed();
          }
        });
  }

  private void updatePageIndicator() {
    if (binding == null || pagedSession == null) return;
    binding.tvWrapInfo.setText("Page " + (pageIndex + 1) + "/" + pagedSession.getPageCount());
  }

  private void goToNextPage() {
    if (pagedSession == null || pageIndex == -1 || pageIndex >= pagedSession.getPageCount() - 1) {
      return;
    }
    switchPage(pageIndex + 1);
  }

  private void goToPreviousPage() {
    if (pagedSession == null || pageIndex <= 0) return;
    switchPage(pageIndex - 1);
  }

  /**
   * Stores the current page and loads {@code target}. Saving and further page switches are blocked
   * until the new page is in the editor, because in between the editor still shows the old page
   * while {@link #pageIndex} may already point at the new one, and a save in that window would
   * write the old page's text into the new page. If loading fails, the index goes back to the page
   * that is still shown.
   */
  private void switchPage(int target) {
    if (!contentLoaded) return;
    PagedEditSession session = pagedSession;
    int current = pageIndex;
    contentLoaded = false;
    session.unloadPageFromEditor(
        current,
        editor,
        new PagedEditSession.Callback() {
          @Override
          public void onSuccess() {
            if (pagedSession != session) return;
            pageIndex = target;
            session.loadPageToEditor(
                target,
                editor,
                new PagedEditSession.Callback() {
                  @Override
                  public void onSuccess() {
                    if (pagedSession != session) return;
                    contentLoaded = true;
                    updatePageIndicator();
                  }

                  @Override
                  public void onError(IOException e) {
                    Log.e("EditorFragment", "خطا در صفحه‌بندی فایل", e);
                    if (pagedSession != session) return;
                    pageIndex = current;
                    contentLoaded = true;
                    updatePageIndicator();
                  }
                });
          }

          @Override
          public void onError(IOException e) {
            Log.e("EditorFragment", "خطا در تعویض صفحه", e);
            if (pagedSession != session) return;
            contentLoaded = true;
          }
        });
  }

  public void saveCurrentFile() {
    if (readOnly) return;
    if (!contentLoaded) {
      Log.w("EditorFragment", "ذخیره نادیده گرفته شد چون محتوای فایل هنوز بارگذاری نشده: " + filePath);
      return;
    }
    if (pagedSession != null) {
      saveCurrentPagedFile();
      return;
    }
    if (filePath != null && viewModel != null && editor != null) {
      String content = editor.getText().toString();
      if (content != null) {
        if (FileChangeReceiver.isWatching(filePath)) {
          FileChangeReceiver.notifyInternalWrite();
        }
        viewModel.saveFile(filePath, content, this::onSavedToDisk, this::showSaveFailed);
      } else {
        Log.e("EditorFragment", "محتوای ادیتور نال است");
      }
    }
  }

  private void saveCurrentPagedFile() {
    if (pagedSession == null || pageIndex == -1 || filePath == null) return;
    pagedSession.unloadPageFromEditor(
        pageIndex,
        editor,
        new PagedEditSession.Callback() {
          @Override
          public void onSuccess() {
            if (pagedSession == null) return;
            if (FileChangeReceiver.isWatching(filePath)) {
              FileChangeReceiver.notifyInternalWrite();
            }
            pagedSession.writeTo(
                new File(filePath),
                new PagedEditSession.Callback() {
                  @Override
                  public void onSuccess() {
                    Log.d("EditorFragment", "فایل بزرگ ذخیره شد: " + filePath);
                    IdeEvents.post(FileEvent.saved(filePath));
                    onSavedToDisk();
                  }

                  @Override
                  public void onError(IOException e) {
                    Log.e("EditorFragment", "خطا در ذخیره فایل بزرگ", e);
                    showSaveFailed(e);
                  }
                });
          }

          @Override
          public void onError(IOException e) {
            Log.e("EditorFragment", "خطا در ذخیره فایل بزرگ", e);
            showSaveFailed(e);
          }
        });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    breadcrumbHandler.removeCallbacksAndMessages(null);
    FileChangeReceiver.stopWatching();
    notifyTabError(false);
    unregisterDiagnosticsListener();
    if (lspViewModel != null) {
      lspViewModel.disconnect();
    }
    if (pagedExecutor != null) {
      pagedExecutor.shutdownNow();
      pagedExecutor = null;
    }
    if (pagedSession != null) {
      pagedSession.close();
      pagedSession = null;
    }
    binding = null;
  }

  public IdeEditor getEditor() {
    return editor;
  }

  private void applyReadOnly() {
    if (editor == null) return;
    if (readOnly) {
      editor.setEditable(false);
    }
  }

  /**
   * از side یه درخواست پرش به location میاد (مثلاً go-to-definition/reference). اگه محتوا هنوز
   * لود نشده باشه ذخیره‌اش می‌کنیم و بعد از لودِ محتوا در {@link #applyPendingJump()} اجراش می‌کنیم.
   */
  public void jumpToLocation(int line, int column) {
    if (editor == null || editor.getText().length() == 0) {
      pendingLine = line;
      pendingColumn = column;
      return;
    }
    doJump(line, column);
  }

  private void applyPendingJump() {
    if (pendingLine >= 0) {
      int line = pendingLine;
      int column = pendingColumn;
      pendingLine = -1;
      pendingColumn = -1;
      doJump(line, column);
    }
  }

  private void doJump(int line, int column) {
    if (editor == null) return;
    int maxLine = editor.getLineCount() - 1;
    int targetLine = Math.max(0, Math.min(line, maxLine));
    editor.setSelection(targetLine, Math.max(0, column));
  }

  public void scheduleBreadcrumbRefresh() {
    if (lspViewModel == null || !lspViewModel.isConnected()) return;
    breadcrumbHandler.removeCallbacks(breadcrumbRefreshRunnable);
    breadcrumbHandler.postDelayed(breadcrumbRefreshRunnable, BREADCRUMB_DEBOUNCE_MS);
  }

  private void refreshBreadcrumbs() {
    LspEditor currentLspEditor = lspViewModel != null ? lspViewModel.getLspEditor() : null;
    IdeEditor currentEditor = editor;
    String currentFilePath = filePath;
    if (currentLspEditor == null || currentEditor == null || currentFilePath == null) return;
    var cursor = currentEditor.getCursor();
    int line = cursor.getLeftLine();
    int column = cursor.getLeftColumn();
    new Thread(
            () -> {
              List<BreadcrumbItem> items =
                  LspRouter.fetchBreadcrumbs(currentLspEditor, currentFilePath, line, column);
              ThreadUtils.runOnUiThread(
                  () -> {
                    if (isAdded() && getActivity() instanceof EditorActivity host) {
                      host.showBreadcrumbs(this, items);
                    }
                  });
            })
        .start();
  }

  private void updateDiagnosticsIcon(@NonNull LspEditor lspEditor) {
    if (binding == null) {
      return;
    }
    boolean connected = lspEditor.isConnected();
    binding.dlch.setVisibility(connected ? View.VISIBLE : View.GONE);
    if (!connected) {
      notifyTabError(false);
      return;
    }

    boolean hasError = false;
    for (var diagnostic : lspEditor.getDiagnostics()) {
      if (diagnostic.getSeverity() == DiagnosticSeverity.Error) {
        hasError = true;
        break;
      }
    }

    if (hasError) {
      binding.dlch.setImageResource(R.drawable.ic_close_24);
      binding.dlch.setColorFilter(DIAGNOSTICS_COLOR_ERROR, PorterDuff.Mode.SRC_IN);
    } else {
      binding.dlch.setImageResource(R.drawable.check_24px);
      binding.dlch.setColorFilter(DIAGNOSTICS_COLOR_OK, PorterDuff.Mode.SRC_IN);
    }
    notifyTabError(hasError);
  }

  private void notifyTabError(boolean hasError) {
    if (filePath == null) return;
    if (getActivity() instanceof EditorActivity) {
      ((EditorActivity) getActivity()).setTabError(filePath, hasError);
    }
  }

  private void showDiagnosticsSheet() {
    LspEditor connected = lspViewModel == null ? null : lspViewModel.getLspEditor();
    if (connected == null || !connected.isConnected()) {
      return;
    }
    DiagnosticsBottomSheet sheet = DiagnosticsBottomSheet.newInstance();
    sheet.setLspEditor(connected);
    sheet.setOnDiagnosticClickListener(
        (startLine, startColumn, endLine, endColumn) -> {
          if (editor == null) return;
          int maxLine = editor.getLineCount() - 1;
          int l1 = Math.max(0, Math.min(startLine, maxLine));
          int l2 = Math.max(l1, Math.min(endLine, maxLine));
          try {
            editor.setSelectionRegion(l1, Math.max(0, startColumn), l2, Math.max(startColumn, endColumn));
          } catch (Exception ignored) {
            editor.setSelection(l1, Math.max(0, startColumn));
          }
        });
    sheet.show(getChildFragmentManager(), DiagnosticsBottomSheet.TAG);
  }

  private void registerDiagnosticsListener(@NonNull LspEditor connected) {
    unregisterDiagnosticsListener();
    if (!setting.isDiagnostics()) {
      return;
    }
    diagnosticsListener =
        new LspDiagnosticsEventListener(
            connected,
            (lspEditor, diagnostics) -> {
              if (binding == null || !isAdded()) {
                return;
              }
              binding.getRoot().post(() -> updateDiagnosticsIcon(lspEditor));
            });
    connected.getEventManager().addEventListener(diagnosticsListener);
  }

  private void unregisterDiagnosticsListener() {
    if (diagnosticsListener == null) {
      return;
    }
    try {
      diagnosticsListener
          .getEditor()
          .getEventManager()
          .removeEventListener(diagnosticsListener.getClass());
    } catch (Exception ignored) {
    }
    diagnosticsListener = null;
  }

  /**
   * Keeps the focused editor above the keyboard. The host activity already pads the whole editor
   * area by the system bars and display cutout, so only the part of the keyboard that rises above
   * that padding is added here; adding the navigation bar again would leave an empty band above
   * the keyboard and below the last line.
   */
  void applyImeInsets(@NonNull final View target) {
    ViewCompat.setOnApplyWindowInsetsListener(
        target,
        (v, insets) -> {
          Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
          Insets hostInsets =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          boolean thisEditorIsFocused = editor != null && editor.hasFocus();
          int bottomInset =
              thisEditorIsFocused ? Math.max(0, imeInsets.bottom - hostInsets.bottom) : 0;
          v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomInset);
          return insets;
        });

    if (editor != null) {
      editor.subscribeEvent(
          EditorFocusChangeEvent.class,
          (event, un) -> {
            ViewCompat.requestApplyInsets(target);
          });
    }
  }

  public String getFilePath() {
    return filePath;
  }
}
