package ir.hanzodev1375.ghostide.mvvm.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import ir.hanzodev1375.ghostide.utils.FileUtil;
import ir.hanzodev1375.ghostide.utils.PathManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Environment;
import ir.hanzodev1375.ghostide.enums.FileState;
import ir.hanzodev1375.ghostide.enums.ProjectType;
import ir.hanzodev1375.ghostide.enums.SortType;
import ir.hanzodev1375.ghostide.fileevents.FileCreatedEvent;
import ir.hanzodev1375.ghostide.fileevents.FileEventCheck;
import ir.hanzodev1375.ghostide.fileevents.FileRenamedEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.models.FileManagerModel;
import ir.hanzodev1375.ghostide.codeeditors.setting.PreferencesUtils;

public class FileViewModel extends AndroidViewModel {

  private static final String TAG = "FileViewModel";
  private static final int BUFFER_SIZE = 256 * 1024;
  private static final int MAX_NAME_SUFFIX = 10_000;

  /** Outcome of {@link #renameFile(FileManagerModel, String)}. */
  public enum RenameResult {
    RENAMED,
    TARGET_EXISTS,
    FAILED
  }

  /**
   * Folders with more entries than this skip the per-subfolder listing that feeds the item count
   * and project tag, so opening a huge directory does not cost one extra directory read per row.
   */
  private static final int FOLDER_INSPECT_LIMIT = 400;

  private MutableLiveData<List<FileManagerModel>> filesLiveData = new MutableLiveData<>();
  private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
  private MutableLiveData<String> currentPath = new MutableLiveData<>();
  private MutableLiveData<CopyProgress> copyProgress = new MutableLiveData<>();
  private MutableLiveData<DeleteProgress> deleteProgress = new MutableLiveData<>();
  private MutableLiveData<List<FileEventCheck>> fileEventsLiveData =
      new MutableLiveData<>(Collections.emptyList());
  private PathManager pathManager;
  private FileState fileState = FileState.NONE;
  private PreferencesUtils prefs;

  private volatile List<FileEventCheck> activeFileEvents = Collections.emptyList();

  private final ExecutorService ioExecutor =
      Executors.newFixedThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));

  public static class CopyProgress {
    public final String fileName;
    public final String fromDir;
    public final String toDir;
    public final int remaining;
    public final long bytesCopied;
    public final long totalBytes;
    public final long speedBps;
    public final boolean isRunning;

    public CopyProgress(
        String fileName,
        String fromDir,
        String toDir,
        int remaining,
        long bytesCopied,
        long totalBytes,
        long speedBps,
        boolean isRunning) {
      this.fileName = fileName;
      this.fromDir = fromDir;
      this.toDir = toDir;
      this.remaining = remaining;
      this.bytesCopied = bytesCopied;
      this.totalBytes = totalBytes;
      this.speedBps = speedBps;
      this.isRunning = isRunning;
    }
  }

  private static class DirStats {
    long totalBytes = 0;
    int totalFiles = 0;
  }

  public static class DeleteProgress {
    public final String fileName;
    public final int deleted;
    public final int total;
    public final boolean isRunning;

    public DeleteProgress(String fileName, int deleted, int total, boolean isRunning) {
      this.fileName = fileName;
      this.deleted = deleted;
      this.total = total;
      this.isRunning = isRunning;
    }
  }

  public FileViewModel(Application app) {
    super(app);
    prefs = new PreferencesUtils(app.getApplicationContext());
    pathManager = new PathManager(app.getApplicationContext());
    String initialPath;
    if (pathManager.isSaveEnabled()) {
      String savedPath = pathManager.getLastPath();
      initialPath =
          FileUtil.isExists(savedPath)
              ? savedPath
              : Environment.getExternalStorageDirectory().getAbsolutePath();
    } else {
      initialPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    }
    currentPath.setValue(initialPath);
    loadFiles(initialPath);
  }

  public LiveData<List<FileManagerModel>> getFiles() {
    return filesLiveData;
  }

  public LiveData<List<FileEventCheck>> getFileEvents() {
    return fileEventsLiveData;
  }

  public LiveData<Boolean> getIsLoading() {
    return isLoading;
  }

  public LiveData<String> getCurrentPath() {
    return currentPath;
  }

  public LiveData<CopyProgress> getCopyProgress() {
    return copyProgress;
  }

  public LiveData<DeleteProgress> getDeleteProgress() {
    return deleteProgress;
  }

  public void savePath(boolean save) {
    pathManager.savePath(save);
    if (save) pathManager.setLastPath(currentPath.getValue());
    else pathManager.clearLastPath();
  }

  public void navigateTo(String path) {
    File file = new File(path);
    if (file.exists() && file.isDirectory()) {
      currentPath.setValue(path);
      loadFiles(path);
      if (pathManager.isSaveEnabled()) pathManager.setLastPath(path);
    }
  }

  public void navigateUp() {
    String parent = new File(currentPath.getValue()).getParent();
    if (parent != null) navigateTo(parent);
  }

  public void loadFiles(String dirPath) {
    isLoading.postValue(true);
    ioExecutor.execute(
        () -> {
          List<FileManagerModel> list = new ArrayList<>();
          File dir = new File(dirPath);
          File[] files = dir.listFiles();
          if (files != null) {
            SortType sortType = SortType.fromKey(prefs.getFileSortType(dirPath));
            boolean reverse = prefs.isFileSortReverse(dirPath);
            Arrays.sort(files, buildSortComparator(sortType, reverse));
            boolean showHidden = prefs.isShowHiddenFiles();
            boolean inspectFolders = files.length <= FOLDER_INSPECT_LIMIT;
            for (File file : files) {
              String name = file.getName();
              if (showHidden || !name.startsWith(".")) {
                FileEventCheck event = eventFor(activeFileEvents, file.getAbsolutePath());
                boolean isDir = file.isDirectory();
                String[] children = isDir && inspectFolders ? file.list() : null;
                list.add(
                    new FileManagerModel(
                        file.getAbsolutePath(),
                        name,
                        event != null ? event.getState() : fileState,
                        file.lastModified(),
                        isDir,
                        isDir ? FileManagerModel.UNKNOWN : file.length(),
                        isDir ? countVisible(children, showHidden) : FileManagerModel.UNKNOWN,
                        isDir ? ProjectType.detect(children) : null));
              }
            }
          }
          filesLiveData.postValue(list);
          isLoading.postValue(false);
        });
  }

  private static int countVisible(String[] names, boolean showHidden) {
    if (names == null) return FileManagerModel.UNKNOWN;
    if (showHidden) return names.length;
    int count = 0;
    for (String name : names) {
      if (!name.startsWith(".")) count++;
    }
    return count;
  }

  private static Comparator<File> buildSortComparator(SortType sortType, boolean reverse) {
    Comparator<File> primary;
    switch (sortType) {
      case SIZE ->
          primary = Comparator.comparingLong((File file) -> file.isFile() ? file.length() : 0L);
      case DATE -> primary = Comparator.comparingLong((File file) -> file.lastModified());
      case TYPE ->
          primary =
              Comparator.comparing(
                  (File file) -> extensionOf(file), String.CASE_INSENSITIVE_ORDER);
      default ->
          primary = Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER);
    }
    Comparator<File> within =
        primary.thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER);
    return (a, b) -> {
      boolean aDir = a.isDirectory();
      boolean bDir = b.isDirectory();
      if (aDir != bDir) return aDir ? -1 : 1;
      int result = within.compare(a, b);
      return reverse ? -result : result;
    };
  }

  private static String extensionOf(File file) {
    if (file.isDirectory()) return "";
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    if (dot <= 0 || dot == name.length() - 1) return "";
    return name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private FileEventCheck eventFor(List<FileEventCheck> events, String path) {
    if (events == null || events.isEmpty()) return null;
    for (FileEventCheck event : events) {
      if (event.matches(path)) return event;
    }
    return null;
  }

  private void armEvent(FileEventCheck event) {
    armEvents(Collections.singletonList(event));
  }

  private void armEvents(List<FileEventCheck> events) {
    if (events == null || events.isEmpty()) return;
    List<FileEventCheck> next = new ArrayList<>(activeFileEvents);
    next.addAll(events);
    activeFileEvents = next;
    fileEventsLiveData.setValue(next);
  }

  public void clearFileEvents() {
    activeFileEvents = Collections.emptyList();
    fileEventsLiveData.setValue(Collections.emptyList());
  }

  private MutableLiveData<String> scrollToPathLiveData = new MutableLiveData<>();

  public LiveData<String> getScrollToPath() {
    return scrollToPathLiveData;
  }

  /**
   * Renames {@code model} inside its folder. An existing file or folder with the new name is never
   * replaced; a name that only differs in letter case from the current one is allowed because on a
   * case-insensitive storage it refers to the same file.
   *
   * @param newName the new name; it must not contain a path separator.
   * @return the outcome, so the caller can tell the user why nothing changed.
   */
  public RenameResult renameFile(FileManagerModel model, String newName) {
    File oldFile = new File(model.getPath());
    if (newName == null
        || newName.isEmpty()
        || newName.indexOf(File.separatorChar) >= 0
        || newName.equals(".")
        || newName.equals("..")) {
      return RenameResult.FAILED;
    }
    if (newName.equals(oldFile.getName())) {
      return RenameResult.RENAMED;
    }
    File newFile = new File(oldFile.getParent(), newName);
    if (existsNoFollow(newFile) && !isSameFile(oldFile, newFile)) {
      return RenameResult.TARGET_EXISTS;
    }
    if (oldFile.renameTo(newFile)) {
      model.setState(FileState.RENAME);
      armEvent(new FileRenamedEvent(newFile.getAbsolutePath()));
      IdeEvents.post(FileEvent.renamed(oldFile.getAbsolutePath(), newFile.getAbsolutePath()));
      loadFiles(currentPath.getValue());
      return RenameResult.RENAMED;
    }
    return RenameResult.FAILED;
  }

  public void deleteFile(FileManagerModel model) {
    ioExecutor.execute(
        () -> {
          File file = new File(model.getPath());
          if (!deleteRecursive(file)) {
            Log.w(TAG, "Could not delete everything in " + file);
          }
          if (!existsNoFollow(file)) {
            IdeEvents.post(FileEvent.deleted(file.getAbsolutePath()));
          }
          loadFiles(currentPath.getValue());
        });
  }

  /**
   * Deletes the items one after another on a single IO thread. Running them as nested tasks of the
   * same pool and waiting for them could dead-lock when the pool has fewer free threads than items.
   */
  public void deleteFiles(List<FileManagerModel> items) {
    ioExecutor.execute(
        () -> {
          DirStats stats = new DirStats();
          for (FileManagerModel item : items) collectStats(new File(item.getPath()), stats);
          int total = stats.totalFiles;
          int[] deleted = {0};

          for (FileManagerModel item : items) {
            File file = new File(item.getPath());
            if (!deleteRecursiveWithProgress(file, deleted, total)) {
              Log.w(TAG, "Could not delete everything in " + file);
            }
          }

          deleteProgress.postValue(new DeleteProgress("", 0, 0, false));
          for (FileManagerModel item : items) {
            if (!existsNoFollow(new File(item.getPath()))) {
              IdeEvents.post(FileEvent.deleted(item.getPath()));
            }
          }
          loadFiles(currentPath.getValue());
        });
  }

  /**
   * Deletes {@code file} and, for a real directory, everything below it. A symbolic link is
   * removed itself and never followed, so the folder it points to is left untouched.
   *
   * @return true when nothing of {@code file} is left.
   */
  private boolean deleteRecursiveWithProgress(File file, int[] deleted, int total) {
    boolean ok = true;
    if (isRealDirectory(file)) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) ok &= deleteRecursiveWithProgress(child, deleted, total);
      }
      return file.delete() && ok;
    }
    boolean removed = file.delete();
    deleted[0]++;
    deleteProgress.postValue(new DeleteProgress(file.getName(), deleted[0], total, true));
    return removed;
  }

  /** Same as {@link #deleteRecursiveWithProgress} without progress updates. */
  private boolean deleteRecursive(File file) {
    boolean ok = true;
    if (isRealDirectory(file)) {
      File[] children = file.listFiles();
      if (children != null) for (File child : children) ok &= deleteRecursive(child);
    }
    return file.delete() && ok;
  }

  private static boolean isRealDirectory(File file) {
    return file.isDirectory() && !Files.isSymbolicLink(file.toPath());
  }

  private static boolean existsNoFollow(File file) {
    return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
  }

  private static boolean isSameFile(File a, File b) {
    try {
      return Files.isSameFile(a.toPath(), b.toPath());
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Returns {@code dir/name}, or {@code dir/name (n)} when that name is taken, so a paste never
   * replaces an existing entry and never writes a file onto itself.
   *
   * @return null when no free name was found.
   */
  private static File uniqueDestination(File dir, String name, boolean isFile) {
    File candidate = new File(dir, name);
    if (!existsNoFollow(candidate)) return candidate;
    int dot = isFile ? name.lastIndexOf('.') : -1;
    String base = dot > 0 ? name.substring(0, dot) : name;
    String extension = dot > 0 ? name.substring(dot) : "";
    for (int i = 1; i <= MAX_NAME_SUFFIX; i++) {
      candidate = new File(dir, base + " (" + i + ")" + extension);
      if (!existsNoFollow(candidate)) return candidate;
    }
    return null;
  }

  /** True when {@code dir} is {@code folder} itself or lies anywhere below it. */
  private static boolean isInside(File dir, File folder) {
    try {
      String d = dir.getCanonicalPath();
      String f = folder.getCanonicalPath();
      return d.equals(f) || d.startsWith(f.endsWith(File.separator) ? f : f + File.separator);
    } catch (IOException e) {
      return true;
    }
  }

  private static boolean sameFolder(File a, File b) {
    try {
      return a.getCanonicalPath().equals(b.getCanonicalPath());
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Copies or moves {@code sources} into {@code destDir}.
   *
   * <p>An entry whose name is already taken in the destination gets a free name ({@code name (1)})
   * instead of replacing it, and copying into the same folder therefore makes a copy instead of
   * truncating the source. A folder is never pasted into itself or below itself. When a move has to
   * fall back to copying (for example across storage volumes), the source is deleted only after the
   * whole copy succeeded; a failed copy is removed again and the source is kept. {@code callback}
   * receives false when any entry failed.
   */
  public void pasteFiles(
      List<FileManagerModel> sources, String destDir, boolean isCut, OnPasteComplete callback) {
    ioExecutor.execute(
        () -> {
          boolean success = true;
          File destFolder = new File(destDir);
          if (!destFolder.isDirectory() && !destFolder.mkdirs()) {
            success = false;
          }

          DirStats stats = new DirStats();
          for (FileManagerModel model : sources) collectStats(new File(model.getPath()), stats);

          long[] bytesCopied = {0};
          int[] remaining = {stats.totalFiles};
          long[] lastTime = {System.currentTimeMillis()};
          long[] lastBytes = {0};

          String fromDir = new File(sources.get(0).getPath()).getParent();
          String fromName = (fromDir != null) ? new File(fromDir).getName() : "Unknown";
          String toName = new File(destDir).getName();

          List<String> createdDests = isCut ? null : new ArrayList<>();
          for (FileManagerModel model : sources) {
            if (!destFolder.isDirectory()) break;
            File src = new File(model.getPath());
            File srcParent = src.getAbsoluteFile().getParentFile();

            if (!existsNoFollow(src)) {
              success = false;
              continue;
            }
            if (isCut && srcParent != null && sameFolder(srcParent, destFolder)) {
              continue;
            }
            if (isRealDirectory(src) && isInside(destFolder, src)) {
              Log.w(TAG, "Refusing to paste " + src + " into itself");
              success = false;
              continue;
            }
            File dest = uniqueDestination(destFolder, src.getName(), !isRealDirectory(src));
            if (dest == null) {
              success = false;
              continue;
            }

            if (isCut) {

              if (!src.renameTo(dest)) {

                boolean copied =
                    copyRecursiveFast(
                        src,
                        dest,
                        bytesCopied,
                        remaining,
                        lastTime,
                        lastBytes,
                        stats.totalBytes,
                        fromName,
                        toName);
                if (copied) {
                  if (!deleteRecursive(src)) {
                    Log.w(TAG, "Moved " + src + " but could not delete every source file");
                    success = false;
                  }
                } else {
                  success = false;
                  if (!deleteRecursive(dest)) {
                    Log.w(TAG, "Could not remove the incomplete copy " + dest);
                  }
                }
              } else {

                remaining[0]--;
                copyProgress.postValue(
                    new CopyProgress(
                        src.getName(),
                        fromName,
                        toName,
                        remaining[0],
                        stats.totalBytes,
                        stats.totalBytes,
                        0,
                        remaining[0] > 0));
              }
            } else {

              boolean copied =
                  copyRecursiveFast(
                      src,
                      dest,
                      bytesCopied,
                      remaining,
                      lastTime,
                      lastBytes,
                      stats.totalBytes,
                      fromName,
                      toName);
              if (copied) {
                createdDests.add(dest.getAbsolutePath());
              } else {
                success = false;
                if (!deleteRecursive(dest)) {
                  Log.w(TAG, "Could not remove the incomplete copy " + dest);
                }
              }
            }
          }

          copyProgress.postValue(new CopyProgress("", "", "", 0, 0, 0, 0, false));
          List<String> destPaths = createdDests;
          boolean result = success;
          new Handler(Looper.getMainLooper())
              .post(
                  () -> {
                    if (destPaths != null && !destPaths.isEmpty()) {
                      List<FileEventCheck> events = new ArrayList<>();
                      for (String dest : destPaths) events.add(new FileCreatedEvent(dest));
                      armEvents(events);
                    }
                    loadFiles(currentPath.getValue());
                    if (callback != null) {
                      callback.onComplete(result);
                    }
                  });
        });
  }

  private void collectStats(File f, DirStats stats) {
    if (!isRealDirectory(f)) {
      if (!existsNoFollow(f)) return;
      stats.totalBytes += f.isFile() ? f.length() : 0;
      stats.totalFiles++;
    } else {
      File[] children = f.listFiles();
      if (children != null) for (File c : children) collectStats(c, stats);
    }
  }

  /**
   * Copies {@code src} to {@code dest}, recursing into folders.
   *
   * @return true only when every file was copied completely.
   */
  private boolean copyRecursiveFast(
      File src,
      File dest,
      long[] bytesCopied,
      int[] remaining,
      long[] lastTime,
      long[] lastBytes,
      long totalBytes,
      String fromDir,
      String toDir) {
    try {
      if (src.isDirectory()) {
        if (!dest.isDirectory() && !dest.mkdirs()) return false;
        File[] children = src.listFiles();
        if (children == null) return false;
        boolean ok = true;
        for (File child : children) {
          ok &=
              copyRecursiveFast(
                  child,
                  new File(dest, child.getName()),
                  bytesCopied,
                  remaining,
                  lastTime,
                  lastBytes,
                  totalBytes,
                  fromDir,
                  toDir);
        }
        return ok;
      }

      long transferred = 0;
      long fileSize;
      try (FileChannel inCh = new FileInputStream(src).getChannel();
          FileChannel outCh = new FileOutputStream(dest).getChannel()) {

        fileSize = inCh.size();
        long chunkSize = BUFFER_SIZE;

        while (transferred < fileSize) {
          long count = Math.min(chunkSize, fileSize - transferred);
          long n = inCh.transferTo(transferred, count, outCh);
          if (n <= 0) break;
          transferred += n;
          bytesCopied[0] += n;

          long now = System.currentTimeMillis();
          long elapsed = now - lastTime[0];
          if (elapsed >= 300) {
            long speed = (bytesCopied[0] - lastBytes[0]) * 1000 / Math.max(elapsed, 1);
            lastTime[0] = now;
            lastBytes[0] = bytesCopied[0];
            copyProgress.postValue(
                new CopyProgress(
                    src.getName(),
                    fromDir,
                    toDir,
                    remaining[0],
                    bytesCopied[0],
                    totalBytes,
                    speed,
                    true));
          }
        }
      }
      remaining[0]--;
      if (transferred < fileSize) {
        Log.w(TAG, "Copy of " + src + " stopped after " + transferred + " of " + fileSize);
        return false;
      }
      return true;
    } catch (IOException | SecurityException e) {
      Log.w(TAG, "Could not copy " + src + " to " + dest, e);
      return false;
    }
  }

  public void createFolder(String folderName) {
    String currentDir = currentPath.getValue();
    if (currentDir == null) return;
    File newFolder = new File(currentDir, folderName);
    if (!newFolder.exists()) {
      newFolder.mkdirs();
      armEvent(new FileCreatedEvent(newFolder.getAbsolutePath()));
      scrollToPathLiveData.postValue(newFolder.getAbsolutePath());
      loadFiles(currentDir);
    }
  }

  public void createFile(String fileName) {
    String currentDir = currentPath.getValue();
    if (currentDir == null) return;
    File newFile = new File(currentDir, fileName);
    if (!newFile.exists()) {
      try {
        newFile.createNewFile();
        armEvent(new FileCreatedEvent(newFile.getAbsolutePath()));
        scrollToPathLiveData.postValue(newFile.getAbsolutePath());
        loadFiles(currentDir);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    ioExecutor.shutdown();
  }

  public interface OnPasteComplete {
    void onComplete(boolean success);
  }
}
