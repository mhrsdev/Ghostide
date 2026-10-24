package ir.hanzodev1375.ghostide.activity;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.text.BidiFormatter;
import ir.hanzodev1375.ghostide.customui.NavModelPanel;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.components.effect.ripple.WaterRipple;
import ir.hanzodev1375.components.effect.ThanosEffect;
import ir.hanzodev1375.components.effect.ThanosItemAnimator;
import ir.hanzodev1375.ghostide.iconpack.IconPackManager;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.blankj.utilcode.util.ClipboardUtils;
import com.bumptech.glide.Glide;
import com.example.liquidglass.GlassMaterial;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;
import ir.theme.M3Theme;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.GlassCompat;
import ir.ghostide.logcat.BottomSheetLogView;
import ir.hanzodev1375.components.RenameDialogFragment;
import ir.hanzodev1375.components.TextInputDialogFragment;
import ir.hanzodev1375.components.ftp.interfaces.RemoteClient;
import ir.hanzodev1375.components.ftp.views.FtpConnectSheet;
import ir.hanzodev1375.components.ftp.views.FtpBrowserSheet;
import ir.hanzodev1375.components.searchdata.model.FileSearchResult;
import ir.hanzodev1375.components.searchdata.ui.SearchBottomSheet;
import ir.hanzodev1375.components.searchdata.interfaces.OnLineClickListener;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.CustomItemSheet;
import ir.hanzodev1375.components.ui.ProfileView;
import ir.hanzodev1375.ghostide.adapters.FileManagerAdapter;
import ir.hanzodev1375.ghostide.adapters.ToolbarAdapter;
import ir.hanzodev1375.ghostide.adapters.ToolbarListAdapter;
import ir.hanzodev1375.ghostide.helper.FileGitHelper;
import ir.hanzodev1375.ghostide.helper.PluginPopupHelper;
import ir.hanzodev1375.ghostide.plugin.PluginPanelHost;
import ir.hanzodev1375.ghostide.helper.ZipModeHelper;
import ir.hanzodev1375.ghostide.databinding.ActivityFilemanagerBinding;
import ir.hanzodev1375.ghostide.databinding.SelectionPanelBinding;
import ir.hanzodev1375.ghostide.dialogs.CopyProgressDialog;
import ir.hanzodev1375.ghostide.dialogs.FileSortManager;
import ir.hanzodev1375.ghostide.dialogs.DeleteProgressDialog;
import ir.hanzodev1375.ghostide.fragments.BatchRenameSheet;
import ir.hanzodev1375.ghostide.fragments.FilePropertiesSheet;
import ir.hanzodev1375.ghostide.jgit.GitHubClient;
import ir.hanzodev1375.ghostide.jgit.GitHubProfileSheet;
import ir.hanzodev1375.ghostide.jgit.jgitandroid.datamanager.GitViewModel;
import ir.hanzodev1375.ghostide.enums.ProjectType;
import ir.hanzodev1375.ghostide.models.FileManagerModel;
import ir.hanzodev1375.ghostide.models.ToolbarModel;
import ir.hanzodev1375.ghostide.postman.PostManActivity;
import ir.hanzodev1375.ghostide.refactor.rename.ui.RenamePackageBottomSheet;
import ir.hanzodev1375.ghostide.bookmark.BookmarkBottomSheet;
import ir.hanzodev1375.ghostide.bookmark.BookmarkViewModel;
import ir.hanzodev1375.ghostide.project.NewProjectDialog;
import ir.hanzodev1375.ghostide.project.android.AndroidStudioProjectSheet;
import ir.hanzodev1375.ghostide.history.HistoryBottomSheet;
import ir.hanzodev1375.ghostide.history.HistoryViewModel;

import ir.hanzodev1375.ghostide.mvvm.viewmodel.FileViewModel;
import ir.hanzodev1375.ghostide.mvvm.viewmodel.GitHubStarViewModel;
import ir.hanzodev1375.ghostide.adapters.FileManagerHostAdapter;
import ir.hanzodev1375.ghostide.ide.ui.api.FileClickHandlers;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginIconState;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginToolbarIcon;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.PluginHostRouter;
import ir.hanzodev1375.ghostide.plugin.PluginManager;
import ir.hanzodev1375.ghostide.pulse.PulseBridge;
import ir.hanzodev1375.ghostide.pulse.PulseListener;
import ir.hanzodev1375.ghostide.pulse.PulseService;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GlobalRegistry;
import ir.hanzodev1375.ghostide.shizuku.ShizukuManager;
import ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity;
import ir.hanzodev1375.ghostide.utils.GroupedRowDecoration;
import ir.hanzodev1375.ghostide.utils.NetworkChangeReceiver;
import ir.hanzodev1375.ghostide.utils.FileExtensionUtils;
import ir.hanzodev1375.ghostide.utils.ObjectUtil;
import ir.hanzodev1375.ghostide.utils.ShortcutHelper;
import ir.hanzodev1375.ghostide.utils.StorageUtils;
import ir.hanzodev1375.ghostide.utils.ZipUtil;
import ir.hanzodev1375.ghostide.utils.ArchiveExtractUtil;
import ir.theme.themeeditor.ThemeEditorActivity;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Collections;
import ir.hanzodev1375.ghostide.R;
import ninja.coder.appuploader.main.ApkInstallerCompat;
import ninja.coder.appuploader.main.appupdate.UpadteAppView;
import ir.hanzodev1375.ghostide.translator.ui.StringsTranslatorSheet;
import irhanzodev1375.musicpreview.MusicPlayerBottomSheetFragment;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.components.store.event.ThemeInstalledEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ir.hanzodev1375.ghostide.dialogs.OpenFilesSheet;
import ir.hanzodev1375.ghostide.models.TabModel;

public class FileManagerActivity extends BaseCompat
    implements NetworkChangeReceiver.CallBackNetWork {

  private static final String SUMMARY_SEPARATOR = "  ·  ";
  private static final int SHOW_BACKGROUND_ALPHA = 128;
  private static final char RIGHT_TO_LEFT_MARK = '\u200F';

  private ActivityFilemanagerBinding bind;
  private FileViewModel viewModel;
  private Disposable fileManagerHostRegistration;
  private FileManagerAdapter adapter;
  private ZipModeHelper zipModeHelper;
  private ThanosEffect thanosEffect;
  private ThanosItemAnimator thanosItemAnimator;
  private View selectionPanel;
  private boolean selectionPanelShowing = false;
  private TextView selectionCount;
  private ImageView btnCopy, btnCut, btnDelete, btnPaste, btnClose, btnSelectall;
  private boolean isCutOperation = false;
  private List<FileManagerModel> pendingClipboard = new ArrayList<>();
  private SelectionPanelBinding selectionPanelBinding;
  private FileManagerModel fileModels;
  private UpadteAppView app;
  private ActivityResultLauncher<Intent> installPermissionLauncher;
  private File apkPendingInstall;
  private PreferencesUtils appsetting;
  private ProfileView profileview;
  private NetworkChangeReceiver networkChangeReceiver;
  private Handler pluginHostHandler;
  private Runnable pluginHostRunnable;
  private CopyProgressDialog copyProgressDialog;
  private DeleteProgressDialog deleteProgressDialog;
  private HistoryViewModel historyViewModel;
  private BookmarkViewModel bookmarkViewModel;
  private GitViewModel gitViewModel;
  private GitHubStarViewModel starViewModel;
  private FileGitHelper gitHelper;
  private PluginPopupHelper pluginPopupHelper;
  private PluginPanelHost pluginPanelHost;
  private List<ToolbarModel> toolbarModel = new ArrayList<>();
  private ToolbarListAdapter toolbarAdapter;
  private boolean gitToolbarVisible = true;
  private int gitToolbarIcon = R.drawable.ic_git;
  private View openFilesItemView;
  private final ExecutorService ftpExecutor = Executors.newSingleThreadExecutor();
  private String currentDir;
  private int systemBarsBottomInset = 0;
  private MusicPlayerBottomSheetFragment musicBottomSheet;
  private boolean pendingAnimation = false;
  private boolean snapArmed = false;
  private String pendingScrollToPath;
  private long pendingScrollToPathTime;
  private PulseBridge changePulse;
  private final PulseListener changePulseListener = this::echoTreeChange;
  private BadgeDrawable openFilesBadge;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    bind = ActivityFilemanagerBinding.inflate(getLayoutInflater());
    setContentView(bind.getRoot());
    setupInsets();
    setupSearchLayoutInsets();

    appsetting = new PreferencesUtils(this);
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    bind.fab.bindOfAcivity(this);
    M3Theme.imageB(
        bind.btnGoToDir,
        bind.btnGoToDir);
    TooltipCompat.setTooltipText(bind.btnGoToDir, getString(R.string.goto_dir));
    setupOpenFilesIndicator();
    applyScreenSurfaces();
    networkChangeReceiver = new NetworkChangeReceiver(this);
    IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      this.registerReceiver(networkChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      this.registerReceiver(networkChangeReceiver, filter);
    }
    pluginHostHandler = new Handler(Looper.getMainLooper());
    pluginHostRunnable =
        () -> {
          try {
            PluginManager.getInstance().setCurrentFileManagerActivity(this);
          } catch (Exception e) {
            e.printStackTrace();
          }
        };
    pluginHostHandler.postDelayed(pluginHostRunnable, 100);

    viewModel = new ViewModelProvider(this).get(FileViewModel.class);
    getSupportFragmentManager()
        .setFragmentResultListener(
            AndroidStudioProjectSheet.RESULT_KEY,
            this,
            (requestKey, result) -> {
              String currentDir = viewModel.getCurrentPath().getValue();
              if (currentDir != null) viewModel.loadFiles(currentDir);
            });
    fileManagerHostRegistration =
        GlobalRegistry.services()
            .register(IdeHostServices.FILE_MANAGER_HOST, new FileManagerHostAdapter(this));
    historyViewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
    bookmarkViewModel = new ViewModelProvider(this).get(BookmarkViewModel.class);
    starViewModel = new ViewModelProvider(this).get(GitHubStarViewModel.class);
    starViewModel.getPromptEvent().observe(this, this::handleStarPromptEvent);
    gitHelper = new FileGitHelper(this, bind, () -> adapter, () -> viewModel.getCurrentPath().getValue());
    gitViewModel = new ViewModelProvider(this).get(GitViewModel.class);
    gitViewModel.changedFiles.observe(
        this,
        changes -> {
          String repoRoot = gitHelper.findGitRepositoryPathForCurrent();
          if (repoRoot == null) return;
          gitHelper.applyChangedFiles(repoRoot, changes);
        });
    adapter = new FileManagerAdapter(this);
    bind.rvfiles.setLayoutManager(
        appsetting.getGridMod()
            ? new GridLayoutManager(this, appsetting.getGridSpanCount())
            : new LinearLayoutManager(this));
    bind.rvfiles.setAdapter(adapter);
    bind.rvfiles.addItemDecoration(new GroupedRowDecoration(this));
    setupThanosEffect();
    app = new UpadteAppView(this, bind.downloader, () -> {});
    installPermissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              File pending = apkPendingInstall;
              apkPendingInstall = null;
              if (pending != null
                  && getPackageManager().canRequestPackageInstalls()) {
                new ApkInstallerCompat(this, pending, installPermissionLauncher, null).install();
              }
            });
    bind.downloader.setOnInstallApkListener(
        (apkFile, fileName) -> {
          apkPendingInstall = apkFile;
          new ApkInstallerCompat(this, apkFile, installPermissionLauncher, null).install();
        });
    stepSearch();
    adapter.setupSelectionTracker(bind.rvfiles);
    viewModel.getFileEvents().observe(this, adapter::setFileEvents);

    viewModel
        .getScrollToPath()
        .observe(
            this,
            path -> {
              pendingScrollToPath = path;
              pendingScrollToPathTime = System.currentTimeMillis();
            });

    viewModel
        .getFiles()
        .observe(
            this,
            files -> {
              if (files != null && !files.isEmpty()) fileModels = files.get(0);
              boolean animate = pendingAnimation;
              pendingAnimation = false;
              boolean wasArmed = snapArmed;
              snapArmed = false;
              adapter.submitList(new ArrayList<>(files), animate);
              String targetPath = pendingScrollToPath;
              boolean scrollTargetFresh =
                  targetPath != null
                      && System.currentTimeMillis() - pendingScrollToPathTime < 3000;
              if (files == null || !scrollTargetFresh || files.isEmpty()) {
                pendingScrollToPath = null;
              }
              if (scrollTargetFresh && files != null) {
                for (int i = 0; i < files.size(); i++) {
                  if (targetPath.equals(files.get(i).getPath())) {
                    final int position = i;
                    bind.rvfiles.post(() -> bind.rvfiles.scrollToPosition(position));
                    break;
                  }
                }
              } else {
                String loadedPath = viewModel.getCurrentPath().getValue();
                if (loadedPath != null) {
                  bind.rvfiles.setLocationKey("dir:" + loadedPath);
                  bind.rvfiles.restoreScrollPosition();
                }
              }
              // این بارگذاری مجدد ناشی از حذف واقعیه؛ باید پرچم تانوس روشن بمونه تا انیمیشن حذف که
              // به‌صورت
              // ناهمگام روی فریم بعدی توسط RecyclerView اجرا می‌شه، بتونه اون رو ببینن. برای
              // ناوبری/بارگذاری
              // عادی (wasArmed=false) پرچم رو خاموش می‌کنیم تا افکت روی کلیک/ناوبری اجرا نشه.
              if (!wasArmed) {
                thanosItemAnimator.setSnapDeletionPending(false);
              }
              updateDirectorySummary(files);
              if (files == null || files.isEmpty()) {
                bind.emptystates.setVisibility(View.VISIBLE);
                bind.rvfiles.setVisibility(View.GONE);
              } else {
                bind.emptystates.setVisibility(View.GONE);
                bind.rvfiles.setVisibility(View.VISIBLE);
              }
              gitHelper.refreshGitStatus();
            });

    viewModel
        .getIsLoading()
        .observe(
            this, loading -> bind.loadingprogass.setVisibility(loading ? View.VISIBLE : View.GONE));

    viewModel.savePath(true);
    String startPath = getIntent().getStringExtra("start_path");
    if (startPath != null && new File(startPath).exists()) {
      viewModel.navigateTo(startPath);
    }
    viewModel
        .getCurrentPath()
        .observe(
            this,
            path -> {
              if (path != null) {
                bind.navmodel.setFile(new File(path));
                bind.tvDirTitle.setText(directoryTitle(path));
              }
            });

    copyProgressDialog = new CopyProgressDialog(this);
    viewModel
        .getCopyProgress()
        .observe(
            this,
            progress -> {
              if (progress == null) return;
              if (progress.isRunning) {
                if (!copyProgressDialog.isShowing()) copyProgressDialog.show();
                copyProgressDialog.update(progress);
              } else {
                copyProgressDialog.dismiss();
              }
            });

    deleteProgressDialog = new DeleteProgressDialog(this);
    viewModel
        .getDeleteProgress()
        .observe(
            this,
            progress -> {
              if (progress == null) return;
              if (progress.isRunning) {
                if (!deleteProgressDialog.isShowing()) deleteProgressDialog.show();
                deleteProgressDialog.update(progress);
              } else {
                deleteProgressDialog.dismiss();
              }
            });

    adapter.setOnItemClickListener(
        (item, view, pos) -> {
          viewModel.clearFileEvents();
          historyViewModel.addToHistory(item.getPath(), item.getName(), item.isDirectory());
          if (!FileClickHandlers.dispatch(
              this, item.getPath(), item.getName(), item.isDirectory(), view)) {
            if (item.isDirectory()) {
              pendingAnimation = true;
              bind.rvfiles.saveScrollPosition();
              viewModel.navigateTo(item.getPath());
            } else if (ArchiveExtractUtil.isZip(item.getPath())) {
              showZipActionDialog(item.getPath());
            } else if (ArchiveExtractUtil.isArchive(item.getPath())) {
              ArchiveExtractUtil.showExtractOptionsDialog(
                  FileManagerActivity.this, new File(item.getPath()), this::refreshFileList);
            } else {
              setupClick(item.getPath(), item.getName(), view);
            }
          }
          String currentPath = viewModel.getCurrentPath().getValue();
          if (currentPath != null) {
            gitHelper.updateGitActionVisibility(currentPath);
          }
          if (bind.ser.isShow()) {
            bind.ser.hide();
            setFabVisible(true);
            bind.ser.setQuery("");
          }
        });

    List<Integer> listIcon = new ArrayList<>();
    listIcon.add(R.drawable.folder);
    listIcon.add(R.drawable.ic_fileicon);
    listIcon.add(R.drawable.add);

    List<Integer> listTooltips = new ArrayList<>();
    listTooltips.add(R.string.dialog_create_folder_title);
    listTooltips.add(R.string.dialog_create_file_title);
    listTooltips.add(R.string.project_new_title);

    bind.fab
        .getRecyclerView()
        .setAdapter(
            new ToolbarAdapter(
                listIcon,
                listTooltips,
                (view2, mypos) -> {
                  switch (mypos) {
                    case 0 -> {
                      creatorFolder(fileModels);
                      bind.fab.dismiss();
                    }
                    case 1 -> {
                      creatorFile(fileModels);
                      bind.fab.dismiss();
                    }
                    case 2 -> {
                      String currentDir = viewModel.getCurrentPath().getValue();
                      if (currentDir != null) {
                        new NewProjectDialog(
                                FileManagerActivity.this,
                                currentDir,
                                projectPath ->
                                    runOnUiThread(
                                        () -> {
                                          viewModel.loadFiles(currentDir);
                                          GhostToast.makeText(
                                                  FileManagerActivity.this,
                                                  getString(R.string.project_created_toast),
                                                  GhostToast.LENGTH_SHORT)
                                              .show();
                                        }))
                            .show();
                      }
                      bind.fab.dismiss();
                    }
                  }
                }));

    bind.fab
        .getFab()
        .setOnClickListener(
            v -> {
              if (!bind.fab.isExpanded()) bind.fab.expand();
              else bind.fab.collapse();
            });

    bind.navmodel
        .getAdapter()
        .setOnItemClickListener(
            (view, nav, pos) -> {
              pendingAnimation = true;
              viewModel.navigateTo(nav.getFilePath());
            });

    bind.navmodel.setOnNavigateListener(
        path -> {
          pendingAnimation = true;
          viewModel.navigateTo(path);
          gitHelper.updateGitActionVisibility(path);
        });
    bind.btnGoToDir.setOnClickListener(this::showGoToDirMenu);

    stepMoreAdapter();
    setupSelectionPanel();

    adapter.setSelectionStateListener(
        new FileManagerAdapter.SelectionStateListener() {
          @Override
          public void onSelectionChanged(int count) {
            if (count == 0 && pendingClipboard.isEmpty() && !zipModeHelper.hasZipClipboard()) {
              hideSelectionPanel();
            } else if (count > 0) {
              showSelectionPanel();
              selectionCount.setText(getString(R.string.selected_items_count, count));
            } else if (count == 0 && !pendingClipboard.isEmpty()) {
              selectionCount.setText("0");
              showSelectionPanel();
            }
          }

          @Override
          public void onSelectionModeStarted() {}

          @Override
          public void onSelectionModeEnded() {
            if (pendingClipboard.isEmpty() && selectionPanel != null) {
              hideSelectionPanel();
            }
          }
        });

    pluginPanelHost = PluginPanelHost.forFileManager(this);
    pluginPopupHelper = new PluginPopupHelper(pluginPanelHost);
    stepToolbar();

    setOnBackPress();
    gitHelper.observe(this, viewModel.getCurrentPath());
    observePulseRoot();
    initZipModeHelper();
  }

  private void initZipModeHelper() {
    zipModeHelper = new ZipModeHelper(this, bind, viewModel, adapter);
    zipModeHelper.setSelectionChangedListener(this::onZipSelectionChanged);
    zipModeHelper.init();
  }

  private void onZipSelectionChanged(int count) {
    if (count == 0 && pendingClipboard.isEmpty() && !zipModeHelper.hasZipClipboard()) {
      hideSelectionPanel();
    } else if (count > 0) {
      showSelectionPanel();
      selectionCount.setText(getString(R.string.selected_items_count, count));
    } else if (count == 0 && !pendingClipboard.isEmpty()) {
      selectionCount.setText("0");
      showSelectionPanel();
    }
  }

  private void setupThanosEffect() {
    if (!ThanosEffect.supports()) return;
    ViewGroup container = (ViewGroup) bind.rvfiles.getParent();
    if (container == null) return;
    thanosEffect = new ThanosEffect(this);
    thanosEffect.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    container.addView(thanosEffect);
    thanosItemAnimator = new ThanosItemAnimator(() -> thanosEffect);
    bind.rvfiles.setItemAnimator(thanosItemAnimator);
  }

  /**
   * قبل از شروع عملیات حذف صدا زده می‌شه تا افکت تانوس فقط روی حذف واقعی اجرا بشه نه روی
   * کلیک/ناوبری.
   */
  private void armSnap() {
    if (thanosItemAnimator == null) return;
    snapArmed = true;
    thanosItemAnimator.setSnapDeletionPending(true);
  }

  private void setupSearchLayoutInsets() {
    bind.fab.post(
        () -> {
          ViewCompat.setOnApplyWindowInsetsListener(
              bind.ser,
              (view, insets) -> {
                int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                int fabBottomMargin = getFabBottomMargin();
                int targetBottomMargin;
                if (imeHeight > 0) {
                  targetBottomMargin = imeHeight;
                } else {
                  targetBottomMargin =
                      fabBottomMargin + (int) (8 * getResources().getDisplayMetrics().density);
                }
                ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                params.bottomMargin = targetBottomMargin;
                view.setLayoutParams(params);
                return insets;
              });
          ViewCompat.requestApplyInsets(bind.ser);
        });
  }

  private int getFabBottomMargin() {
    int fabBottom = bind.fab.getBottom();
    int screenHeight = bind.fab.getRootView().getHeight();
    return screenHeight - fabBottom;
  }

  public String getCurrentDirectoryPath() {
    return viewModel.getCurrentPath().getValue();
  }

  public void refreshCurrentDirectory() {
    viewModel.clearFileEvents();
    viewModel.loadFiles(viewModel.getCurrentPath().getValue());
  }

  public void setupClick(String path, String name) {
    setupClick(path, name, null);
  }

  public void setupClick(String path, String name, View sourceItemView) {
    String extension = FileExtensionUtils.getExtension(name);
    if (PluginHostRouter.route(this, path, extension)) {
      return;
    }
    if (FileExtensionUtils.isCodeFile(extension)) {
      PluginHostRouter.openInEditor(this, new File(path));
    } else if (path.endsWith(".gth")) {
      var sheets = new CustomItemSheet(FileManagerActivity.this);
      sheets.add(getString(R.string.theme_edit), R.drawable.ic_edit);
      sheets.add(getString(R.string.theme_apply), R.drawable.outline_color_lens);
      sheets.setOnClickListener(
          (models, pos, view) -> {
            switch (pos) {
              case 0 -> {
                sheets.dismiss();
                Intent i = new Intent(FileManagerActivity.this, ThemeEditorActivity.class);
                i.putExtra(ThemeEditorActivity.EXTRA_THEME_PATH, path);
                View sharedView = sourceItemView;
                if (sharedView != null) {
                  sharedView = sourceItemView.findViewById(R.id.listcard);
                  if (sharedView == null) sharedView = sourceItemView;
                }
                startActivityWithSharedElement(i, sharedView, ObjectUtil.TRANSITION_THEME);
              }
              case 1 -> {
                appsetting.setAppThemeFile(path);
                sheets.dismiss();
              }
            }
          });
      sheets.show();

    } else if (FileExtensionUtils.isImageFile(extension)) {
      String currentDir = new File(path).getParent();
      File dir = new File(currentDir);
      File[] allFiles = dir.listFiles();
      ArrayList<String> imagePaths = new ArrayList<>();
      int currentIndex = 0;
      if (allFiles != null) {
        for (int i = 0; i < allFiles.length; i++) {
          File f = allFiles[i];
          if (f.isFile()) {
            String ext = FileExtensionUtils.getExtension(f.getName());
            if (FileExtensionUtils.isImageFile(ext)) {
              imagePaths.add(f.getAbsolutePath());
              if (f.getAbsolutePath().equals(path)) currentIndex = imagePaths.size() - 1;
            }
          }
        }
      }
      if (!imagePaths.isEmpty()) {
        Intent setImage = new Intent(FileManagerActivity.this, ImageViewerActivity.class);
        setImage.putStringArrayListExtra(ImageViewerActivity.EXTRA_IMAGE_URIS, imagePaths);
        setImage.putExtra(ImageViewerActivity.EXTRA_CURRENT_INDEX, currentIndex);
        View sharedView = sourceItemView;
        if (sharedView != null) {
          sharedView = sourceItemView.findViewById(R.id.ivIcon);
          if (sharedView == null) sharedView = sourceItemView;
        }
        startActivityWithSharedElement(setImage, sharedView, ObjectUtil.TRANSITION_IMAGE);
      } else {
        GhostToast.makeText(this, "No image found", GhostToast.LENGTH_SHORT).show();
      }
    } else if (FileExtensionUtils.isAudioFile(extension)) {
      showMusicPreview(resolveAudioPlaylist(path), path);
    } else if (extension.equals(".apk")) {
      installApk(path);
    } else {
      GhostToast.makeText(
              this, getString(R.string.error_file_format_not_supported), GhostToast.LENGTH_SHORT)
          .show();
    }
  }

  private void showSelectionPanel() {
    if (selectionPanel == null || selectionPanelShowing) return;
    selectionPanelShowing = true;
    selectionPanel.setAlpha(0f);
    selectionPanel.setTranslationY(selectionPanel.getHeight() + dp(48));
    selectionPanel.setVisibility(View.VISIBLE);
    selectionPanel
        .animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(220)
        .setInterpolator(new DecelerateInterpolator(1.5f))
        .start();
  }

  private void hideSelectionPanel() {
    if (selectionPanel == null || !selectionPanelShowing) return;
    selectionPanelShowing = false;
    View panel = selectionPanel;
    panel
        .animate()
        .alpha(0f)
        .translationY(panel.getHeight() + dp(48))
        .setDuration(160)
        .setInterpolator(new DecelerateInterpolator(1.5f))
        .withEndAction(
            () -> {
              if (!selectionPanelShowing) panel.setVisibility(View.GONE);
            })
        .start();
  }

  private int dp(float value) {
    return (int) (value * getResources().getDisplayMetrics().density);
  }

  public void setFabVisible(boolean visible) {
    if (bind.fab == null) return;
    bind.fab.animate().cancel();
    if (visible) {
      if (bind.fab.getVisibility() != View.VISIBLE) {
        bind.fab.setScaleX(0.8f);
        bind.fab.setScaleY(0.8f);
        bind.fab.setAlpha(0f);
        bind.fab.setVisibility(View.VISIBLE);
      }
      bind.fab
          .animate()
          .alpha(1f)
          .scaleX(1f)
          .scaleY(1f)
          .setDuration(200)
          .setInterpolator(new DecelerateInterpolator(1.5f))
          .start();
    } else {
      if (bind.fab.getVisibility() != View.VISIBLE) return;
      bind.fab
          .animate()
          .alpha(0f)
          .scaleX(0.8f)
          .scaleY(0.8f)
          .setDuration(160)
          .setInterpolator(new DecelerateInterpolator(1.5f))
          .withEndAction(() -> bind.fab.setVisibility(View.GONE))
          .start();
    }
  }

  public void updateGitActionVisibility(String path) {
    gitHelper.updateGitActionVisibility(path);
  }

  /**
   * آخرین وضعیت آیکون git را ذخیره و توپ‌بار را تازه می‌کند. از داخل {@link FileGitHelper} صدا
   * زده می‌شود تا به‌جای تغییر مستقیم یک ImageButton، آیتم ریسایکلر آپدیت شود.
   */
  public void updateGitToolbar(boolean visible, int iconRes) {
    gitToolbarVisible = visible;
    gitToolbarIcon = iconRes;
    if (toolbarModel != null) {
      for (ToolbarModel model : toolbarModel) {
        if ("git".equals(model.getTag())) {
          model.setVisible(visible);
          model.setIcon(iconRes);
        }
      }
    }
    if (toolbarAdapter != null) {
      toolbarAdapter.notifyDataSetChanged();
    }
  }

  /** توپ‌بار هدر فایل‌منیجر (مثل ادیتور) از یک ریسایکلر افقی ساخته می‌شود تا آیتم‌های افزونه هم اضافه شوند. */
  private void stepToolbar() {
    toolbarModel.clear();
    boolean hasTabs = !readOpenTabs().isEmpty();
    toolbarModel.add(new ToolbarModel(gitToolbarIcon, "git", gitToolbarVisible, gitToolbarVisible));
    int priorityInsertIndex = toolbarModel.size();
    for (var registration :
        GlobalRegistry.extensions().registrations(PluginUiExtensionPoints.TOOLBAR_ICON)) {
      PluginToolbarIcon icon = registration.extension();
      if (icon.getState() == PluginIconState.FileES) {
        toolbarModel.add(
            priorityInsertIndex++,
            new ToolbarModel(icon.getIcon(), "plugin:" + icon.getId()));
      }
    }
    toolbarModel.add(new ToolbarModel(R.drawable.ic_ai_robot, "ai"));
    toolbarModel.add(new ToolbarModel(R.drawable.ic_panel, "plugins"));
    toolbarModel.add(new ToolbarModel(R.drawable.ic_open_files, "openfiles", hasTabs));
    toolbarModel.add(new ToolbarModel(R.drawable.more_vert, "settings"));
    if (toolbarAdapter == null) {
      toolbarAdapter = new ToolbarListAdapter(toolbarModel, this::onToolbarClicked, this);
      bind.rvtoolbar.setLayoutManager(
          new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
      bind.rvtoolbar.setAdapter(toolbarAdapter);
    } else {
      toolbarAdapter.notifyDataSetChanged();
    }
    attachOpenFilesBadge();
  }

  private void onToolbarClicked(View view, ToolbarModel model, int position) {
    String tag = model.getTag();
    if (tag == null) {
      return;
    }
    if (tag.startsWith("plugin:")) {
      for (var registration :
          GlobalRegistry.extensions().registrations(PluginUiExtensionPoints.TOOLBAR_ICON)) {
        PluginToolbarIcon icon = registration.extension();
        if (("plugin:" + icon.getId()).equals(tag)) {
          icon.onClick(view);
          return;
        }
      }
      return;
    }
    switch (tag) {
      case "git" -> gitHelper.handleGitClick(view);
      case "ai" ->
          startActivityWithSharedElement(
              new Intent(getApplicationContext(), AiChatActivity.class),
              view,
              ObjectUtil.TRANSITION_AI_CHAT);
      case "plugins" -> pluginPopupHelper.show(this, view);
      case "openfiles" -> showOpenFilesSheet();
      case "settings" -> stepButton(view);
    }
  }

  /** آیتم توپ‌بار را با tag پیدا می‌کند (ویوها تازه ساخته‌شده‌ی ریسایکلر). */
  private View findToolbarItem(String tag) {
    if (bind == null || tag == null) return null;
    for (int i = 0; i < bind.rvtoolbar.getChildCount(); i++) {
      View child = bind.rvtoolbar.getChildAt(i);
      if (tag.equals(child.getTag())) {
        return child;
      }
    }
    return null;
  }

  /** بدج (تعداد فایل‌های باز) را به آیتم open files توپ‌بار وصل می‌کند. */
  private void attachOpenFilesBadge() {
    if (bind == null || openFilesBadge == null) return;
    bind.rvtoolbar.post(
        () -> {
          if (bind == null) return;
          View item = findToolbarItem("openfiles");
          if (item == null) return;
          View anchor = item;
          if (item instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) item;
            if (group.getChildCount() > 0) {
              anchor = group.getChildAt(0);
            }
          }
          if (!anchor.equals(openFilesItemView)) {
            if (openFilesItemView != null) {
              try {
                BadgeUtils.detachBadgeDrawable(openFilesBadge, openFilesItemView);
              } catch (Exception ignored) {
              }
            }
            openFilesItemView = anchor;
            BadgeUtils.attachBadgeDrawable(openFilesBadge, anchor);
          }
          openFilesBadge.updateBadgeCoordinates(anchor);
          openFilesBadge.setVisible(!readOpenTabs().isEmpty());
        });
  }

  public void markPasteAvailable() {
    if (btnPaste != null) btnPaste.setColorFilter(0xff00ff00);
  }

  public void clearPasteAvailable() {
    if (btnPaste != null) btnPaste.clearColorFilter();
  }

  public void setSelectionCount(String text) {
    if (selectionCount != null) selectionCount.setText(text);
  }

  private void setupSelectionPanel() {
    selectionPanelBinding = bind.selectionPanel;
    selectionPanel = selectionPanelBinding.getRoot();
    selectionCount = selectionPanelBinding.txtSelectedCount;
    btnCopy = selectionPanelBinding.btnCopy;
    btnCut = selectionPanelBinding.btnCut;
    btnDelete = selectionPanelBinding.btnDelete;
    btnPaste = selectionPanelBinding.btnPaste;
    btnClose = selectionPanelBinding.btnClose;
    btnSelectall = selectionPanelBinding.btnSelectall;
    applyGlassBackground(selectionPanelBinding.getRoot());
    var selectionMore = selectionPanelBinding.selectionmore;
    btnCopy.setOnClickListener(
        v -> {
          if (zipModeHelper.isZipMode()) {
            zipModeHelper.copySelection();
            return;
          }
          List<FileManagerModel> selected = adapter.getSelectedItems();
          if (!selected.isEmpty()) {
            pendingClipboard = new ArrayList<>(selected);
            isCutOperation = false;
            adapter.clearSelection();
            btnPaste.setColorFilter(0xff00ff00);
            showSelectionPanel();
            selectionCount.setText("0");
          }
        });

    btnCut.setOnClickListener(
        v -> {
          if (zipModeHelper.isZipMode()) {
            zipModeHelper.cutSelection();
            return;
          }
          List<FileManagerModel> selected = adapter.getSelectedItems();
          if (!selected.isEmpty()) {
            pendingClipboard = new ArrayList<>(selected);
            isCutOperation = true;
            adapter.clearSelection();
            btnPaste.setColorFilter(0xff00ff00);
            showSelectionPanel();
            selectionCount.setText("0");
          }
        });

    btnDelete.setOnClickListener(
        v -> {
          if (zipModeHelper.isZipMode()) {
            zipModeHelper.deleteSelection();
            return;
          }
          List<FileManagerModel> selected = adapter.getSelectedItems();
          if (!selected.isEmpty()) {
            new DialogCompat(this)
                .setTitle(getString(R.string.removed))
                .setMessage(getString(R.string.removedmassges, selected.size()))
                .setPositiveButton(
                    getString(R.string.ok),
                    (d, w) -> {
                      armSnap();
                      viewModel.deleteFiles(selected);
                      adapter.clearSelection();
                    })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
          }
        });

    btnPaste.setOnClickListener(
        v -> {
          if (zipModeHelper.isZipMode()) {
            zipModeHelper.pasteClipboard();
            return;
          }
          if (pendingClipboard.isEmpty()) return;
          String currentDir = viewModel.getCurrentPath().getValue();
          if (currentDir != null) {
            copyProgressDialog.setMoveMode(isCutOperation);
            viewModel.pasteFiles(
                pendingClipboard,
                currentDir,
                isCutOperation,
                success -> {
                  pendingClipboard.clear();
                  btnPaste.clearColorFilter();
                  adapter.clearSelection();
                  hideSelectionPanel();
                  adapter.notifyDataSetChanged();
                  if (!success)
                    GhostToast.makeText(this, "Paste failed", GhostToast.LENGTH_SHORT).show();
                });
          }
        });

    btnSelectall.setOnClickListener(
        v -> {
          if (zipModeHelper.isZipMode()) {
            zipModeHelper.selectAll();
            return;
          }
          adapter.selectAll();
          selectionCount.setText(
              getString(R.string.selected_items_count, adapter.getSelectedItems().size()));
          if (selectionPanel.getVisibility() != View.VISIBLE) {
            showSelectionPanel();
          }
        });

    btnClose.setOnClickListener(
        v -> {
          pendingClipboard.clear();
          adapter.clearSelection();
          zipModeHelper.clearSelection();
          zipModeHelper.resetZipClipboard();
          btnPaste.clearColorFilter();
          hideSelectionPanel();
        });
    selectionMore.setOnClickListener(
        v -> {
          if (zipModeHelper.isZipMode()) return;
          List<FileManagerModel> selected = adapter.getSelectedItems();
          if (selected.isEmpty()) return;

          List<String> items =
              List.of(
                  getString(R.string.zip), getString(R.string.props_title_multi), "Rename Group");
          ObjectUtil.showGlassMenu(
              this,
              v,
              items,
              (index, title) -> {
                if (index == 0) {
                  List<File> filesToZip = new ArrayList<>();
                  for (FileManagerModel model : selected) {
                    filesToZip.add(new File(model.getPath()));
                  }
                  btnClose.performClick();
                  ZipUtil.showZipDialog(FileManagerActivity.this, filesToZip);
                } else if (index == 1) {
                  FilePropertiesSheet.newInstance(selected)
                      .show(getSupportFragmentManager(), FilePropertiesSheet.TAG);
                  btnClose.performClick();
                } else if (index == 2) {
                  BatchRenameSheet sheet = BatchRenameSheet.newInstance(selected);
                  sheet.setOnRenameListener(
                      (items1, pattern, find, replace, useRegex) -> {
                        btnClose.performClick();
                        viewModel.loadFiles(viewModel.getCurrentPath().getValue());
                        refreshFileList();
                      });
                  sheet.show(getSupportFragmentManager(), BatchRenameSheet.TAG);
                }
              });
        });
    selectionPanel.setVisibility(View.GONE);

    M3Theme.applyTopLevel(bind.getRoot());
  }

  private void applyGlassBackground(View panel) {
    if (!(panel instanceof ViewGroup)) return;
    ViewGroup container = (ViewGroup) panel;
    container.setBackground(new ColorDrawable(Color.TRANSPARENT));
    int padding = (int) (2 * getResources().getDisplayMetrics().density);
    GlassCompat glass = new GlassCompat(this);
    glass.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    glass.setCornerRadius(10f * getResources().getDisplayMetrics().density);
    glass.setRefractionHeight(66f * getResources().getDisplayMetrics().density);
    glass.setBevelWidth(14f * getResources().getDisplayMetrics().density);
    glass.setMaterial(GlassMaterial.REGULAR);
    glass.setDispersionStrength(0.12f);
    glass.setEnableDynamicBackground(true);
    glass.setEnableSensorHighlight(false);
    glass.setEnableAdaptiveTint(true);
    glass.setBackdropSource(bind.backdropContent);
    LinearLayout inner = new LinearLayout(this);
    inner.setOrientation(LinearLayout.VERTICAL);
    inner.setGravity(Gravity.CENTER_VERTICAL);
    inner.setPadding(padding, padding, padding, padding);
    while (container.getChildCount() > 0) {
      View child = container.getChildAt(0);
      container.removeView(child);
      inner.addView(child);
    }
    glass.addView(inner);
    container.addView(glass);
  }

  private void setupInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(
        bind.coordinator,
        (view, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
          systemBarsBottomInset = systemBars.bottom;
          bind.headtop.setPadding(0, systemBars.top, 0, 0);
          bind.musicPreview.setPadding(
              bind.musicPreview.getPaddingLeft(),
              bind.musicPreview.getPaddingTop(),
              bind.musicPreview.getPaddingRight(),
              systemBars.bottom);
          bind.fab.post(
              () -> {
                int fabSpace = bind.fab.getHeight() + 48;
                int extraBottom = (imeBottom > 0) ? imeBottom : 0;
                bind.rvfiles.setPadding(
                    bind.rvfiles.getPaddingLeft(),
                    bind.rvfiles.getPaddingTop(),
                    bind.rvfiles.getPaddingRight(),
                    systemBars.bottom + fabSpace + extraBottom);
                updateFabBottomMargin();
              });
          return insets;
        });
  }

  private void updateFabBottomMargin() {
    ViewGroup.MarginLayoutParams fabParams =
        (ViewGroup.MarginLayoutParams) bind.fab.getLayoutParams();
    fabParams.bottomMargin =
        systemBarsBottomInset
            + (bind.musicPreview.getVisibility() == View.VISIBLE
                ? bind.musicPreview.getHeight()
                : 0);
    bind.fab.setLayoutParams(fabParams);
  }

  private ArrayList<String> resolveAudioPlaylist(String path) {
    ArrayList<String> audioPaths = new ArrayList<>();
    File dir = new File(path).getParentFile();
    File[] allFiles = dir != null ? dir.listFiles() : null;
    if (allFiles != null) {
      for (File f : allFiles) {
        if (f.isFile()
            && FileExtensionUtils.isAudioFile(FileExtensionUtils.getExtension(f.getName()))) {
          audioPaths.add(f.getAbsolutePath());
        }
      }
    }
    return audioPaths;
  }

  private void showMusicPreview(ArrayList<String> playlist, String path) {
    if (playlist.isEmpty()) {
      showMusicPreview(path);
      return;
    }
    int index = playlist.indexOf(path);
    if (index < 0) index = 0;
    bind.musicPreview.setPlaylist(playlist, index);
    bind.musicPreview.setSongName(new File(playlist.get(index)).getName());
    bind.musicPreview.setOnMusicClickListener(() -> showMusicPlayerBottomSheet());
    bind.musicPreview.setVisibility(View.VISIBLE);
    bind.musicPreview.post(this::updateFabBottomMargin);
  }

  private void showMusicPreview(String path) {
    bind.musicPreview.setMusicPath(path);
    bind.musicPreview.setSongName(new File(path).getName());
    bind.musicPreview.setOnMusicClickListener(() -> showMusicPlayerBottomSheet());
    bind.musicPreview.setVisibility(View.VISIBLE);
    bind.musicPreview.post(this::updateFabBottomMargin);
  }

  private void showMusicPlayerBottomSheet() {
    if (bind.musicPreview.getVisibility() != View.VISIBLE) return;
    String path = bind.musicPreview.getMusicPath();
    if (path == null) return;
    String songName = bind.musicPreview.getSongName();
    String artistName = "";
    try {
      artistName = bind.musicPreview.getArtistName();
    } catch (Exception ignored) {
    }
    musicBottomSheet = MusicPlayerBottomSheetFragment.newInstance(path, songName, artistName);
    musicBottomSheet.setMusicControl(bind.musicPreview);
    musicBottomSheet.setOnDismissListener(() -> musicBottomSheet = null);
    musicBottomSheet.show(getSupportFragmentManager(), "music_player_sheet");
  }

  private void hideMusicPreview() {
    if (musicBottomSheet != null && musicBottomSheet.isAdded()) {
      musicBottomSheet.dismiss();
    }
    if (bind.musicPreview.getVisibility() != View.VISIBLE) return;
    bind.musicPreview.release();
    bind.musicPreview.setVisibility(View.GONE);
    updateFabBottomMargin();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (pluginPanelHost != null) {
      pluginPanelHost.dismissAllFloatingWindows();
    }
    if (EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().unregister(this);
    }
    WaterRipple.clear();
    if (thanosEffect != null) {
      thanosEffect.kill();
      thanosEffect = null;
    }
    if (pluginHostHandler != null) {
      pluginHostHandler.removeCallbacks(pluginHostRunnable);
      pluginHostHandler = null;
    }
    if (PluginManager.getInstance() != null) {
      PluginManager.getInstance().clearFileManagerActivityReferences();
    }
    if (fileManagerHostRegistration != null) {
      fileManagerHostRegistration.dispose();
      fileManagerHostRegistration = null;
    }
    gitHelper.shutdown();
    ftpExecutor.shutdownNow();
    if (bind != null) {
      bind.musicPreview.release();
      bind = null;
    }
    if (networkChangeReceiver != null) {
      try {
        this.unregisterReceiver(networkChangeReceiver);
      } catch (IllegalArgumentException e) {
        // Receiver was not registered; onCreate aborted before registration.
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onThemeInstalled(ThemeInstalledEvent event) {
    if (EventBus.getDefault().isRegistered(this)) {
      reapplyThemeLive();
      applyToolbarIconTint();
    }
  }

  @Override
  protected void onThemeReapplied() {
    super.onThemeReapplied();
    if (bind != null) {
      applyToolbarIconTint();
      applyScreenSurfaces();
    }
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  /**
   * M3Theme.apply() برای ریسایکلر توپ‌بار رد می‌شه، پس اینجا بعد از تعویض تم دوباره آیتم‌ها را
   * bind می‌کنیم تا tint شان زنده آپدیت شود.
   */
  private void applyToolbarIconTint() {
    if (bind != null && toolbarAdapter != null) {
      toolbarAdapter.notifyDataSetChanged();
    }
  }

  private void setOnBackPress() {
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (musicBottomSheet != null && musicBottomSheet.isAdded()) {
                  musicBottomSheet.dismiss();
                  return;
                }
                if (bind.musicPreview.getVisibility() == View.VISIBLE) {
                  hideMusicPreview();
                } else if (zipModeHelper.isZipMode()) {
                  if (!zipModeHelper.navigateUp()) {
                    zipModeHelper.exitZipMode();
                  }
                } else {
                  String path = viewModel.getCurrentPath().getValue();
                  if (path != null
                      && !path.equals("/storage/emulated/0")
                      && !StorageUtils.isStorageRoot(FileManagerActivity.this, path)) {
                    pendingAnimation = true;
                    viewModel.navigateUp();
                    String currentPath = viewModel.getCurrentPath().getValue();
                    if (currentPath != null) {
                      gitHelper.updateGitActionVisibility(currentPath);
                    }
                  } else {
                    new DialogCompat(FileManagerActivity.this)
                        .setTitle(getString(R.string.dialog_exit_title))
                        .setMessage(getString(R.string.dialog_exit_message))
                        .setNegativeButton(getString(R.string.ok), (c, f) -> finishAffinity())
                        .setPositiveButton(getString(R.string.cancel), null)
                        .show();
                  }
                }
              }
            });
  }

  private void showZipActionDialog(String zipPath) {
    new DialogCompat(this)
        .setTitle(R.string.zip)
        .setItems(
            new String[] {
              getString(R.string.archive_browse),
              getString(R.string.extract_archive)
            },
            (d, which) -> {
              d.dismiss();
              if (which == 0) {
                zipModeHelper.enterZipMode(zipPath);
              } else {
                ArchiveExtractUtil.showExtractOptionsDialog(
                    FileManagerActivity.this, new File(zipPath), this::refreshFileList);
              }
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  void stepMoreAdapter() {
    adapter.setOnMoreClickListener(
        (filemodel, view, pos) -> {
          boolean showRenamePackage = isRenameablePackageDirectory(filemodel);
          boolean showRenameClass = isRenameableClassFile(filemodel);
          boolean isDir = filemodel.isDirectory();
          boolean isArch = ArchiveExtractUtil.isArchive(filemodel.getPath());
          boolean isZip = ArchiveExtractUtil.isZip(filemodel.getPath());

          List<String> items = new ArrayList<>();
          items.add(getString(R.string.removed));
          items.add(getString(R.string.rename));
          items.add(getString(R.string.props_title_single));
          items.add(getString(R.string.bookmark_add));
          items.add(getString(R.string.shortcut_menu_item));
          items.add(getString(R.string.copyfullpath));
          items.add(getString(R.string.create_zip_title));
          if (isArch) {
            items.add(getString(R.string.zip_extract_here));
            items.add(getString(R.string.zip_extract_to));
            if (isZip) {
              items.add(getString(R.string.archive_browse));
            }
          }
          if (showRenamePackage) {
            items.add(getString(R.string.rename_package_menu_item));
          }
          if (showRenameClass) {
            items.add(getString(R.string.rename_class_menu_item));
          }
          ObjectUtil.showGlassMenu(
              this,
              view,
              items,
              (index, title) -> {
                if (title.equals(getString(R.string.create_zip_title))) {
                  ZipUtil.showZipDialog(
                      FileManagerActivity.this,
                      Collections.singletonList(new File(filemodel.getPath())));
                } else if (title.equals(getString(R.string.zip_extract_here))) {
                  File arch = new File(filemodel.getPath());
                  ArchiveExtractUtil.extractArchive(
                      FileManagerActivity.this, arch, arch.getParentFile(), this::refreshFileList);
                } else if (title.equals(getString(R.string.zip_extract_to))) {
                  File arch = new File(filemodel.getPath());
                  File targetDir =
                      new File(
                          arch.getParentFile(),
                          ArchiveExtractUtil.getBaseArchiveName(arch.getName()));
                  ArchiveExtractUtil.extractArchive(
                      FileManagerActivity.this, arch, targetDir, this::refreshFileList);
                } else if (title.equals(getString(R.string.archive_browse))) {
                  zipModeHelper.enterZipMode(filemodel.getPath());
                } else if (title.equals(getString(R.string.removed))) {
                  removedItem(filemodel);
                } else if (title.equals(getString(R.string.rename))) {
                  renameItem(filemodel);
                } else if (title.equals(getString(R.string.props_title_single))) {
                  FilePropertiesSheet.newInstance(Collections.singletonList(filemodel))
                      .show(getSupportFragmentManager(), FilePropertiesSheet.TAG);
                } else if (title.equals(getString(R.string.bookmark_add))) {
                  bookmarkViewModel.toggle(
                      filemodel.getPath(),
                      filemodel.getName(),
                      filemodel.isDirectory(),
                      isNowBookmarked ->
                          runOnUiThread(
                              () ->
                                  GhostToast.makeText(
                                          FileManagerActivity.this,
                                          isNowBookmarked
                                              ? getString(R.string.bookmark_added)
                                              : getString(R.string.bookmark_removed),
                                          GhostToast.LENGTH_SHORT)
                                      .show()));
                } else if (title.equals(getString(R.string.shortcut_menu_item))) {
                  ShortcutHelper.showShortcutDialog(this, filemodel);
                } else if (title.equals(getString(R.string.copyfullpath))) {
                  ClipboardUtils.copyText(filemodel.getPath());
                  GhostToast.makeText(
                          FileManagerActivity.this, filemodel.getPath(), GhostToast.LENGTH_SHORT)
                      .show();
                } else if (title.equals(getString(R.string.rename_package_menu_item))) {
                  openRenamePackageSheet(filemodel);
                } else if (title.equals(getString(R.string.rename_class_menu_item))) {
                  openRenameClassSheet(filemodel);
                }
              });
        });
  }

  private File findSourceRootForPackageDirectory(File directory) {
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

  private boolean isRenameablePackageDirectory(FileManagerModel model) {
    if (!model.isDirectory()) {
      return false;
    }
    File directory = new File(model.getPath());
    File sourceRoot = findSourceRootForPackageDirectory(directory);
    return sourceRoot != null && !directory.equals(sourceRoot);
  }

  private boolean isRenameableClassFile(FileManagerModel model) {
    if (model.isDirectory()) {
      return false;
    }
    String name = model.getName();
    return name.endsWith(".java") || name.endsWith(".kt");
  }

  private void openRenameClassSheet(FileManagerModel model) {
    File file = new File(model.getPath());
    File sourceRoot = findSourceRootForPackageDirectory(file.getParentFile());
    File moduleRoot =
        sourceRoot != null
                && sourceRoot.getParentFile() != null
                && sourceRoot.getParentFile().getParentFile() != null
            ? sourceRoot.getParentFile().getParentFile().getParentFile()
            : file.getParentFile();
    if (moduleRoot == null) {
      return;
    }
    String name = model.getName();
    int dot = name.lastIndexOf('.');
    String className = dot > 0 ? name.substring(0, dot) : name;
    ir.hanzodev1375.ghostide.refactor.renameclass.ui.RenameClassBottomSheet.newInstance(
            moduleRoot.getAbsolutePath(), file.getAbsolutePath(), className)
        .show(
            getSupportFragmentManager(),
            ir.hanzodev1375.ghostide.refactor.renameclass.ui.RenameClassBottomSheet.TAG);
  }

  private void openRenamePackageSheet(FileManagerModel model) {
    File directory = new File(model.getPath());
    File sourceRoot = findSourceRootForPackageDirectory(directory);
    if (sourceRoot == null) {
      return;
    }
    File moduleRoot =
        sourceRoot.getParentFile() != null && sourceRoot.getParentFile().getParentFile() != null
            ? sourceRoot.getParentFile().getParentFile().getParentFile()
            : null;
    if (moduleRoot == null) {
      return;
    }
    String relativePath = sourceRoot.toURI().relativize(directory.toURI()).getPath();
    if (relativePath.endsWith("/")) {
      relativePath = relativePath.substring(0, relativePath.length() - 1);
    }
    String packageName = relativePath.replace('/', '.');
    RenamePackageBottomSheet.newInstance(moduleRoot.getAbsolutePath(), packageName)
        .show(getSupportFragmentManager(), RenamePackageBottomSheet.TAG);
  }

  void renameItem(FileManagerModel model) {
    RenameDialogFragment dialog =
        RenameDialogFragment.getInstance(
            model.getName(),
            (prefix, extension) -> {
              String displayName =
                  !TextUtils.isEmpty(extension) ? prefix + "." + extension : prefix;
              viewModel.renameFile(model, displayName);
            });
    dialog.show(getSupportFragmentManager(), RenameDialogFragment.TAG);
  }

  void removedItem(FileManagerModel model) {
    new DialogCompat(this)
        .setTitle(getString(R.string.removed))
        .setMessage(getString(R.string.removedmassges, model.getName() + "?"))
        .setPositiveButton(
            getString(R.string.ok),
            (d, w) -> {
              armSnap();
              viewModel.deleteFile(model);
            })
        .setNegativeButton(getString(R.string.cancel), null)
        .show();
  }

  void creatorFile(FileManagerModel model) {
    TextInputDialogFragment.newInstance(
            getString(R.string.dialog_create_file_title),
            getString(R.string.dialog_create_file_hint),
            null)
        .setCallback(
            text -> {
              viewModel.createFile(text);
              playRootRipple();
            })
        .show(getSupportFragmentManager(), null);
  }

  void creatorFolder(FileManagerModel model) {
    TextInputDialogFragment.newInstance(
            getString(R.string.dialog_create_folder_title),
            getString(R.string.dialog_create_folder_hint),
            null)
        .setCallback(
            text -> {
              viewModel.createFolder(text);
              playRootRipple();
            })
        .show(getSupportFragmentManager(), null);
  }

  private void playRootRipple() {
    if (getWindow() == null || getWindow().getDecorView() == null) return;
    View decor = getWindow().getDecorView();
    WaterRipple.ripple(decor, decor.getWidth() / 2f, decor.getHeight() / 2f, 0.6f);
    WaterRipple.ripple(decor, decor.getWidth() / 4f, decor.getHeight() / 3f, 0.4f);
    WaterRipple.ripple(decor, decor.getWidth() * 3f / 4f, decor.getHeight() / 3f, 0.4f);
  }

  /**
   * Paints the page, the header text and the path bar from the active {@link M3Theme}. With the
   * theme background image enabled the page stays transparent so the image shows through, and the
   * path bar is drawn half transparent like the rows.
   */
  private void applyScreenSurfaces() {
    M3Theme.textView(bind.tvDirTitle);
    M3Theme.textView(bind.tvDirSummary);
    boolean showBackground = appsetting.isShowBackground();
    int pathBarColor =
        fallback(M3Theme.surfaceContainerHigh(), fallback(M3Theme.surfaceContainer(), 0));
    if (showBackground) {
      bind.headtop.setBackgroundColor(Color.TRANSPARENT);
      bind.headline.setBackgroundColor(Color.TRANSPARENT);
      pathBarColor = ColorUtils.setAlphaComponent(pathBarColor, SHOW_BACKGROUND_ALPHA);
      setupBackgroundBlur(bind.backgroundiconfilemanager, bind.contentContainer);
    } else {
      int pageColor = fallback(M3Theme.surface(), fallback(M3Theme.surfaceContainer(), 0));
      bind.headtop.setBackgroundColor(pageColor);
      bind.headline.setBackgroundColor(pageColor);
    }
    GradientDrawable pathBar = new GradientDrawable();
    pathBar.setColor(pathBarColor);
    pathBar.setCornerRadius(getResources().getDimension(R.dimen.fm_path_bar_corner));
    bind.navRow.setBackground(pathBar);
  }

  /** @return the header title for {@code path}: the storage label for the internal-storage root. */
  private String directoryTitle(String path) {
    if (NavModelPanel.STORAGE_EMULATED_0.equals(path)) {
      return getString(R.string.internal_storage_label);
    }
    String name = new File(path).getName();
    return name.isEmpty() ? File.separator : name;
  }

  /**
   * Writes the one-line summary under the folder title: the build-system tag of the current folder
   * (if it is a project root) followed by its folder and file counts. Each part is bidi-isolated
   * and, in a right-to-left locale, the line starts with a right-to-left mark, so a Latin tag next
   * to Persian counts keeps the reading order of the user's language even though the app does not
   * resolve RTL text direction (supportsRtl is off).
   */
  private void updateDirectorySummary(List<FileManagerModel> files) {
    if (files == null || files.isEmpty()) {
      bind.tvDirSummary.setText("");
      return;
    }
    int folders = 0;
    String[] names = new String[files.size()];
    for (int i = 0; i < files.size(); i++) {
      FileManagerModel model = files.get(i);
      names[i] = model.getName();
      if (model.isDirectory()) folders++;
    }
    int regularFiles = files.size() - folders;
    BidiFormatter bidi = BidiFormatter.getInstance();
    SpannableStringBuilder summary = new SpannableStringBuilder();
    if (bidi.isRtlContext()) summary.append(RIGHT_TO_LEFT_MARK);
    ProjectType projectType = ProjectType.detect(names);
    if (projectType != null) {
      int tagStart = summary.length();
      summary.append(bidi.unicodeWrap(projectType.label()));
      Integer primary = M3Theme.primary();
      if (primary != null) {
        summary.setSpan(
            new ForegroundColorSpan(primary),
            tagStart,
            summary.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      summary.append(SUMMARY_SEPARATOR);
    }
    summary.append(
        bidi.unicodeWrap(
            getResources().getQuantityString(R.plurals.fm_folder_count, folders, folders)));
    summary.append(SUMMARY_SEPARATOR);
    summary.append(
        bidi.unicodeWrap(
            getResources()
                .getQuantityString(R.plurals.fm_file_count, regularFiles, regularFiles)));
    bind.tvDirSummary.setText(summary);
  }

  private void setupHeader() {
    GitHubClient gitHub = new GitHubClient(this);
    bind.userAvatar.setLoggedIn(gitHub.isLoggedIn());
    String accountLabel =
        gitHub.isLoggedIn() ? gitHub.getName() : getString(R.string.github_account_not_logged_in);
    bind.userAvatar.setContentDescription(accountLabel);
    TooltipCompat.setTooltipText(bind.userAvatar, accountLabel);
    if (gitHub.isLoggedIn()) {
      profileview = new ProfileView(this);
      profileview.bindImageView(bind.userAvatar, gitHub.getAvatarUrl(), R.drawable.user);
      Glide.with(this)
          .load(gitHub.getAvatarUrl())
          .circleCrop()
          .placeholder(R.drawable.user)
          .into(bind.userAvatar);
    } else {
      bind.userAvatar.setImageResource(R.drawable.user);
    }
    bind.userAvatar.setOnClickListener(
        v -> {
          if (gitHub.isLoggedIn()) {
            GitHubProfileSheet.newInstance().show(getSupportFragmentManager(), "github_profile");
          } else {
            new DialogCompat(v.getContext())
                .setTitle(getString(R.string.github_tokenerrortitle))
                .setMessage(getString(R.string.github_tokenerrormsg))
                .setPositiveButton(
                    getString(R.string.ok),
                    (c, e) -> {
                      Intent i = new Intent(getApplicationContext(), SettingActivity.class);
                      i.putExtra("open_section", "githublogin");
                      startActivity(i);
                    })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
          }
        });
  }

  private void handleStarPromptEvent(GitHubStarViewModel.PromptEvent event) {
    if (event == null) return;
    GitHubStarViewModel.PromptType type = event.consume();
    if (type == null) return;
    switch (type) {
      case NOT_STARRED -> showStarPrompt();
      case STAR_SUCCESS ->
          GhostToast.makeText(
                  this, getString(R.string.github_star_success), GhostToast.LENGTH_SHORT)
              .show();
      case STAR_FAILURE -> openGitHubRepository();
    }
  }

  private void showStarPrompt() {
    if (isFinishing() || isDestroyed()) return;
    starViewModel.markPromptShown();
    new DialogCompat(this)
        .setTitle(R.string.github_star_title)
        .setMessage(R.string.github_star_message)
        .setPositiveButton(
            R.string.github_star_action, (dialog, which) -> starViewModel.starRepository())
        .setNegativeButton(R.string.github_star_later, null)
        .show();
  }

  private void openGitHubRepository() {
    GhostToast.makeText(
            this, getString(R.string.github_star_open_fallback), GhostToast.LENGTH_SHORT)
        .show();
    try {
      startActivity(
          new Intent(Intent.ACTION_VIEW, Uri.parse(GitHubStarViewModel.REPOSITORY_URL)));
    } catch (ActivityNotFoundException e) {
      GhostToast.makeText(
              this, getString(R.string.github_star_no_browser), GhostToast.LENGTH_SHORT)
          .show();
    }
  }

  private void setupOpenFilesIndicator() {
    openFilesBadge = BadgeDrawable.create(this);
    openFilesBadge.setBadgeGravity(BadgeDrawable.TOP_END);
    attachOpenFilesBadge();
    updateOpenFilesIndicator();
  }

  private List<TabModel> readOpenTabs() {
    String json = getSharedPreferences("editor", MODE_PRIVATE).getString("path", null);
    if (json == null || json.isEmpty()) {
      return new ArrayList<>();
    }
    try {
      List<TabModel> tabs =
          new Gson().fromJson(json, new TypeToken<List<TabModel>>() {}.getType());
      return tabs == null ? new ArrayList<>() : tabs;
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private void updateOpenFilesIndicator() {
    if (bind == null || openFilesBadge == null) return;
    int count = readOpenTabs().size();
    boolean hasTabs = count > 0;
    openFilesBadge.setNumber(count);
    openFilesBadge.setVisible(hasTabs);
    if (toolbarModel != null) {
      for (ToolbarModel model : toolbarModel) {
        if ("openfiles".equals(model.getTag())) {
          model.setShowVisblityItem(hasTabs);
        }
      }
    }
    if (toolbarAdapter != null) {
      toolbarAdapter.notifyDataSetChanged();
    }
    attachOpenFilesBadge();
  }

  private void showOpenFilesSheet() {
    List<TabModel> tabs = readOpenTabs();
    if (tabs.isEmpty()) return;
    OpenFilesSheet sheet = OpenFilesSheet.newInstance();
    sheet.setTabs(tabs);
    sheet.setOnFileSelectedListener(
        tab -> setupClick(tab.getFilePath(), tab.getFileName()));
    sheet.show(getSupportFragmentManager(), OpenFilesSheet.TAG);
  }

  @Override
  protected void onResume() {
    super.onResume();
    bind.fab.clearRunningAnimation();
    if (toolbarAdapter != null) {
      stepToolbar();
    }
    updateOpenFilesIndicator();
    relaxPulseRoot();
    setupHeader();
    starViewModel.checkStarStatus();
    if (IconPackManager.shouldRefresh(this)) {
      refreshFileList();
    }
    applyScreenSurfaces();

    boolean currentGrid = appsetting.getGridMod();
    int currentSpan = appsetting.getGridSpanCount();
    boolean spanChanged =
        currentGrid
            && (bind.rvfiles.getLayoutManager() instanceof GridLayoutManager)
            && ((GridLayoutManager) bind.rvfiles.getLayoutManager()).getSpanCount() != currentSpan;

    if (adapter.isGridMode() != currentGrid || spanChanged) {
      bind.rvfiles.saveScrollPosition();
      adapter = new FileManagerAdapter(this);
      var gr = new GridLayoutManager(this, 2);
      gr.setSpanCount(appsetting.getGridSpanCount());
      bind.rvfiles.setLayoutManager(currentGrid ? gr : new LinearLayoutManager(this));
      bind.rvfiles.setRecycledViewPool(new RecyclerView.RecycledViewPool());
      bind.rvfiles.setAdapter(adapter);
      adapter.setupSelectionTracker(bind.rvfiles);

      adapter.setOnItemClickListener(
          (item, view, pos) -> {
            viewModel.clearFileEvents();
            historyViewModel.addToHistory(item.getPath(), item.getName(), item.isDirectory());
            if (!FileClickHandlers.dispatch(
                this, item.getPath(), item.getName(), item.isDirectory(), view)) {
              if (item.isDirectory()) {
                pendingAnimation = true;
                bind.rvfiles.saveScrollPosition();
                viewModel.navigateTo(item.getPath());
              } else if (item.getPath().toLowerCase().endsWith(".zip")) {
                zipModeHelper.enterZipMode(item.getPath());
              } else {
                setupClick(item.getPath(), item.getName());
              }
            }
            String currentPath = viewModel.getCurrentPath().getValue();
            if (currentPath != null) {
              gitHelper.updateGitActionVisibility(currentPath);
            }
            if (bind.ser.isShow()) {
              bind.ser.hide();
              setFabVisible(true);
              bind.ser.setQuery("");
            }
          });

      adapter.setSelectionStateListener(
          new FileManagerAdapter.SelectionStateListener() {
            @Override
            public void onSelectionChanged(int count) {
              if (count == 0 && pendingClipboard.isEmpty() && !zipModeHelper.hasZipClipboard()) {
                hideSelectionPanel();
              } else if (count > 0) {
                showSelectionPanel();
                selectionCount.setText(getString(R.string.selected_items_count, count));
              } else if (count == 0 && !pendingClipboard.isEmpty()) {
                selectionCount.setText("0");
                showSelectionPanel();
              }
            }

            @Override
            public void onSelectionModeStarted() {}

            @Override
            public void onSelectionModeEnded() {
              if (pendingClipboard.isEmpty() && selectionPanel != null) {
                hideSelectionPanel();
              }
            }
          });

      stepMoreAdapter();

      String currentPath = viewModel.getCurrentPath().getValue();
      if (currentPath != null) viewModel.loadFiles(currentPath);
    }

    if (!zipModeHelper.isZipMode()) {
      String currentPath = viewModel.getCurrentPath().getValue();
      if (currentPath != null) {
        gitHelper.updateGitActionVisibility(currentPath);
      }
      gitHelper.refreshGitStatus();
    }
  }

  @Override
  protected void onPause() {
    coilPulseRoot();
    bind.fab.clearRunningAnimation();
    bind.rvfiles.saveScrollPosition();
    super.onPause();
  }

  void stepButton(View v) {
    List<String> items =
        List.of(
            getString(R.string.settings_title),
            getString(R.string.sort_title),
            getString(R.string.search_hint),
            getString(R.string.serachdata),
            getString(R.string.openlogcat),
            getString(R.string.history_title),
            getString(R.string.bookmark_title),
            getString(R.string.ftp_connect),
            getString(R.string.sd_card_menu_item),
            getString(R.string.store),
            getString(R.string.translator_title),
            getString(R.string.terminal_title),
            getString(R.string.postman_title),
            getString(R.string.plugin_manager_title),
            getString(R.string.aboutapp));
    ObjectUtil.showGlassMenu(
        this,
        v,
        items,
        (index, title) -> {
          switch (index) {
            case 0 -> startActivity(new Intent(getApplicationContext(), SettingActivity.class));
            case 1 -> showSortDialog();
            case 2 -> {
              if (!bind.ser.isShow()) {
                bind.ser.show();
                setFabVisible(false);
              } else {
                bind.ser.hide();
                setFabVisible(true);
              }
            }
            case 3 -> {
              openSearchSheet();
            }
            case 4 -> {
              var log = new BottomSheetLogView();
              log.show(getSupportFragmentManager(), "log");
            }
            case 5 -> {
              HistoryBottomSheet sheet = HistoryBottomSheet.newInstance();
              sheet.setOnHistoryItemSelectedListener(
                  item -> {
                    if (item.isDirectory) {
                      pendingAnimation = true;
                      viewModel.navigateTo(item.path);
                    } else {
                      setupClick(item.path, item.name, null);
                    }
                  });
              sheet.show(getSupportFragmentManager(), HistoryBottomSheet.TAG);
            }
            case 6 -> {
              BookmarkBottomSheet bsheet = BookmarkBottomSheet.newInstance();
              bsheet.setOnBookmarkSelectedListener(
                  item -> {
                    if (item.isDirectory) {
                      pendingAnimation = true;
                      viewModel.navigateTo(item.path);
                    } else {
                      setupClick(item.path, item.name, null);
                    }
                  });
              bsheet.show(getSupportFragmentManager(), BookmarkBottomSheet.TAG);
            }
            case 7 -> {
              showFtpConnectSheet();
            }
            case 8 -> openSdCard();
            case 9 -> startActivity(new Intent(FileManagerActivity.this, StoreActivity.class));
            case 10 -> StringsTranslatorSheet.newInstance(viewModel.getCurrentPath().getValue())
                .show(getSupportFragmentManager(), StringsTranslatorSheet.TAG);
            case 11 -> startActivity(new Intent(FileManagerActivity.this, TerminalActivity.class));
            case 12 -> startActivity(new Intent(FileManagerActivity.this, PostManActivity.class));
            case 13 -> startActivity(
                new Intent(FileManagerActivity.this, PluginManagerActivity.class));
            case 14 -> startActivity(new Intent(FileManagerActivity.this, AboutActivity.class));
          }
        });
  }

  private void showSortDialog() {
    String currentPath = viewModel.getCurrentPath().getValue();
    if (currentPath == null) return;
    new FileSortManager(this, currentPath, () -> viewModel.loadFiles(currentPath)).show();
  }

  private void openSearchSheet() {
    String currentPath = viewModel.getCurrentPath().getValue();
    if (currentPath == null) return;
    SearchBottomSheet sheet = SearchBottomSheet.newInstance(currentPath);
    sheet.setOnLineClickListener(
        new OnLineClickListener() {
          @Override
          public void onLineClick(String filePath, int lineNumber) {
            setupClick(filePath, new File(filePath).getName(), null);
          }

          @Override
          public void onFileClick(FileSearchResult result) {
            if (new File(result.getFilePath()).isDirectory()) {
              viewModel.navigateTo(result.getFilePath());
            } else {
              setupClick(result.getFilePath(), result.getFileName(), null);
            }
          }
        });
    sheet.show(getSupportFragmentManager(), SearchBottomSheet.TAG);
  }

  void stepSearch() {
    bind.ser.setOnTextChangedListener(
        qer -> {
          if (qer.length() > 0) adapter.search(qer);
          else adapter.search("");
        });
    bind.ser.setIconClose(R.drawable.ic_close);
    bind.ser.setIconSearch(R.drawable.outline_search);
  }

  private void showGoToDirMenu(View anchor) {
    List<String> items =
        List.of(
            getString(R.string.goto_dir),
            getString(R.string.internal_storage_label),
            getString(R.string.sd_card_menu_item),
            getString(R.string.goto_root));
    ObjectUtil.showGlassMenu(
        this,
        anchor,
        items,
        (index, title) -> {
          switch (index) {
            case 0 -> bind.navmodel.showGoToDirDialog();
            case 1 -> navigateToPath(Environment.getExternalStorageDirectory().getAbsolutePath());
            case 2 -> {
              StorageUtils.StorageEntry sd = StorageUtils.getSdCardVolume(this);
              if (sd != null) {
                navigateToPath(sd.path);
              } else {
                GhostToast.makeText(this, R.string.sd_card_not_found, GhostToast.LENGTH_SHORT)
                    .show();
              }
            }
            case 3 -> {
                
                navigateToPath(getCacheDir().getAbsolutePath());
            }
          }
        });
  }

  private void navigateToPath(String path) {
    if (path == null || path.isEmpty()) return;
    pendingAnimation = true;
    viewModel.navigateTo(path);
    gitHelper.updateGitActionVisibility(path);
  }

  @Override
  public void ConnectionNOT() {}

  @Override
  public void ConnectionIS() {
    app.init();
  }

  public void refreshFileList() {
    runOnUiThread(
        () -> {
          viewModel.clearFileEvents();
          String currentPath = viewModel.getCurrentPath().getValue();
          if (currentPath != null) {
            viewModel.loadFiles(currentPath);
          }
          if (zipModeHelper.isZipMode()) {
            zipModeHelper.reloadCurrentEntry();
          }
        });
  }

  private void observePulseRoot() {
    viewModel
        .getCurrentPath()
        .observe(
            this,
            path -> {
              if (changePulse != null && path != null) {
                changePulse.point(new File(path));
              }
            });
  }

  private void relaxPulseRoot() {
    if (changePulse != null) return;
    changePulse = new PulseBridge(changePulseListener);
    String path = viewModel.getCurrentPath().getValue();
    if (path != null) changePulse.point(new File(path));
    try {
      boolean ok =
          bindService(new Intent(this, PulseService.class), changePulse, Context.BIND_AUTO_CREATE);
      if (!ok) {
        changePulse = null;
      }
    } catch (RuntimeException e) {
      changePulse = null;
    }
  }

  private void coilPulseRoot() {
    if (changePulse == null) return;
    try {
      unbindService(changePulse);
    } catch (RuntimeException ignored) {
    }
    changePulse.quell();
    changePulse = null;
  }

  private void echoTreeChange(String mutated) {
    if (isFinishing() || zipModeHelper.isZipMode()) return;
    String shown = viewModel.getCurrentPath().getValue();
    if (shown == null) return;
    if (affectsVisibleList(mutated, shown)) {
      viewModel.loadFiles(shown);
    }
  }

  private static boolean affectsVisibleList(String mutated, String shown) {
    if (mutated == null || shown == null) return false;
    if (mutated.equals(shown)) return true;
    File mutatedFile = new File(mutated);
    File parent = mutatedFile.getParentFile();
    return parent != null && parent.getAbsolutePath().equals(shown);
  }

  private void showFtpConnectSheet() {
    FtpConnectSheet sheet = FtpConnectSheet.newInstance();
    sheet.setOnConnectedListener(this::openFtpBrowser);
    sheet.show(getSupportFragmentManager(), FtpConnectSheet.TAG);
  }

  private void openSdCard() {
    StorageUtils.StorageEntry sdCard = StorageUtils.getSdCardVolume(this);
    if (sdCard != null) {
      viewModel.navigateTo(sdCard.path);
      gitHelper.updateGitActionVisibility(sdCard.path);
      GhostToast.makeText(
              this,
              getString(
                  R.string.sd_card_space_info,
                  sdCard.getFreeFormatted(),
                  sdCard.getTotalFormatted()),
              GhostToast.LENGTH_SHORT)
          .show();
    } else {
      GhostToast.makeText(this, R.string.sd_card_not_found, GhostToast.LENGTH_SHORT).show();
    }
  }

  private void openFtpBrowser(RemoteClient client, String host, boolean isSftp) {
    FtpBrowserSheet sheet = FtpBrowserSheet.newInstance(client, host, isSftp);
    sheet.setOnDownloadListener(
        new FtpBrowserSheet.OnDownloadListener() {
          @Override
          public void onDownload(String remotePath, String fileName) {
            currentDir = viewModel.getCurrentPath().getValue();
            if (currentDir == null || !new File(currentDir).exists()) {
              currentDir = getFilesDir().getAbsolutePath();
            }
            File localFile = new File(currentDir, fileName);
            ftpExecutor.execute(
                () -> {
                  try {
                    client.download(remotePath, localFile.getAbsolutePath());
                    runOnUiThread(
                        () -> { 
                          GhostToast.makeText(
                                  FileManagerActivity.this,
                                  R.string.ftp_download_success,
                                  GhostToast.LENGTH_LONG)
                              .show();
                          viewModel.loadFiles(currentDir);
                        });
                  } catch (Exception e) {
                    runOnUiThread(
                        () -> {
                          GhostToast.makeText(
                                  FileManagerActivity.this,
                                  R.string.ftp_download_error + ": " + e.getMessage(),
                                  GhostToast.LENGTH_LONG)
                              .show();
                        });
                  }
                });
          }
        });
    sheet.show(getSupportFragmentManager(), FtpBrowserSheet.TAG);
  }

  private void installApk(String path) {
    if (ShizukuManager.isAvailable() && ShizukuManager.hasPermission()) {
      ShizukuManager.exec(
          new String[] {"pm", "install", "-r", "-i", getPackageName(), path},
          new ShizukuManager.ExecCallback() {
            @Override
            public void onResult(String output) {
              boolean ok = output.toLowerCase().contains("success");
              GhostToast.makeText(
                      FileManagerActivity.this,
                      ok ? "installsuccess" : output,
                      GhostToast.LENGTH_LONG)
                  .show();
            }

            @Override
            public void onUnavailable() {
              installApkNormal(path);
            }
          });
    } else if (ShizukuManager.isAvailable()) {
      ShizukuManager.requestPermission();
      GhostToast.makeText(this, "Error", GhostToast.LENGTH_LONG).show();
    } else {
      installApkNormal(path);
    }
  }

  private void installApkNormal(String path) {
    apkPendingInstall = new File(path);
    new ApkInstallerCompat(this, apkPendingInstall, installPermissionLauncher, null).install();
  }

  private static int fallback(Integer value, int def) {
    return value != null ? value : def;
  }
}
