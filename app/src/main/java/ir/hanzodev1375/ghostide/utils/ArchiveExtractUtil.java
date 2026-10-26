package ir.hanzodev1375.ghostide.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.github.junrar.Archive;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.rarfile.FileHeader;
import com.google.android.material.textfield.TextInputEditText;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.activity.FileManagerActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.progress.ProgressMonitor;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

/**
 * Universal archive extraction utility supporting ZIP, RAR, 7Z, TAR and compressed TAR formats.
 *
 * <p>Handles password-protected archives (AES/standard), multi-volume split archives (e.g.
 * .part1.rar/.part2.rar, .z01/.zip, .r00/.rar), real-time progress dialogs, and safe file
 * extraction.
 */
public final class ArchiveExtractUtil {

  private static final ExecutorService executor = Executors.newFixedThreadPool(2);
  private static final Handler mainHandler = new Handler(Looper.getMainLooper());
  private static final long HOST_CHECK_INTERVAL_MS = 250L;
  private static AlertDialog progressDialog;
  private static ProgressBar progressBar;
  private static TextView tvFileName, tvPercent, tvDetails;

  private ArchiveExtractUtil() {}

  /**
   * Checks whether the specified file path represents a supported archive format.
   *
   * @param path file path or name
   * @return true if the file is an archive
   */
  public static boolean isArchive(String path) {
    if (path == null) return false;
    String p = path.toLowerCase(Locale.ROOT);
    return isZip(p)
        || isRar(p)
        || is7z(p)
        || isTar(p);
  }

  /**
   * Checks if path is a ZIP archive or a split zip volume.
   */
  public static boolean isZip(String path) {
    if (path == null) return false;
    String p = path.toLowerCase(Locale.ROOT);
    return p.endsWith(".zip") || p.matches(".*\\.z\\d{2}$");
  }

  /**
   * Checks if path is a RAR archive or a multi-part RAR volume.
   */
  public static boolean isRar(String path) {
    if (path == null) return false;
    String p = path.toLowerCase(Locale.ROOT);
    return p.endsWith(".rar") || p.matches(".*\\.part\\d+\\.rar$") || p.matches(".*\\.r\\d{2}$");
  }

  /**
   * Checks if path is a 7-Zip archive.
   */
  public static boolean is7z(String path) {
    return path != null && path.toLowerCase(Locale.ROOT).endsWith(".7z");
  }

  /**
   * Checks if path is a TAR or compressed TAR archive.
   */
  public static boolean isTar(String path) {
    if (path == null) return false;
    String p = path.toLowerCase(Locale.ROOT);
    return p.endsWith(".tar")
        || p.endsWith(".tar.gz")
        || p.endsWith(".tgz")
        || p.endsWith(".tar.bz2")
        || p.endsWith(".tbz2")
        || p.endsWith(".tar.xz")
        || p.endsWith(".txz");
  }

  /**
   * Resolves the primary starting volume for multi-part archives (e.g. part2.rar -> part1.rar,
   * .z01 -> .zip, .r01 -> .rar).
   *
   * @param file the selected archive file
   * @return the first volume file if found, or the original file
   */
  @NonNull
  public static File resolveFirstVolume(@NonNull File file) {
    String name = file.getName();
    File parent = file.getParentFile();
    if (parent == null) return file;

    // 1. New RAR multi-part naming: name.part2.rar -> name.part1.rar or name.part01.rar
    Pattern partPattern = Pattern.compile("^(.*?)\\.part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);
    Matcher partMatcher = partPattern.matcher(name);
    if (partMatcher.matches()) {
      String base = partMatcher.group(1);
      String numStr = partMatcher.group(2);
      try {
        int partNum = Integer.parseInt(numStr);
        if (partNum != 1) {
          String p1 = String.format(Locale.ROOT, "%0" + numStr.length() + "d", 1);
          File first1 = new File(parent, base + ".part" + p1 + ".rar");
          if (first1.exists()) return first1;
          File first2 = new File(parent, base + ".part1.rar");
          if (first2.exists()) return first2;
        }
      } catch (NumberFormatException ignored) {
      }
    }

    // 2. Old RAR multi-part naming: name.r01 -> name.rar or name.r00
    Pattern rPattern = Pattern.compile("^(.*?)\\.r\\d{2}$", Pattern.CASE_INSENSITIVE);
    Matcher rMatcher = rPattern.matcher(name);
    if (rMatcher.matches()) {
      String base = rMatcher.group(1);
      File firstRar = new File(parent, base + ".rar");
      if (firstRar.exists()) return firstRar;
      File firstR00 = new File(parent, base + ".r00");
      if (firstR00.exists()) return firstR00;
    }

    // 3. Split ZIP: name.z01 -> name.zip
    Pattern zPattern = Pattern.compile("^(.*?)\\.z\\d{2}$", Pattern.CASE_INSENSITIVE);
    Matcher zMatcher = zPattern.matcher(name);
    if (zMatcher.matches()) {
      String base = zMatcher.group(1);
      File zipMain = new File(parent, base + ".zip");
      if (zipMain.exists()) return zipMain;
    }

    return file;
  }

  /**
   * Extracts clean folder base name for extracting archive into a named subfolder.
   */
  public static String getBaseArchiveName(String fileName) {
    if (fileName == null) return "Extracted";
    String name = fileName;
    if (name.toLowerCase(Locale.ROOT).endsWith(".tar.gz")) {
      return name.substring(0, name.length() - 7);
    }
    if (name.toLowerCase(Locale.ROOT).endsWith(".tar.bz2")
        || name.toLowerCase(Locale.ROOT).endsWith(".tar.xz")) {
      return name.substring(0, name.length() - 8);
    }
    Pattern partPattern = Pattern.compile("^(.*?)\\.part\\d+\\.rar$", Pattern.CASE_INSENSITIVE);
    Matcher m = partPattern.matcher(name);
    if (m.matches()) {
      return m.group(1);
    }
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  /**
   * Prompts the user with extraction options ("Extract Here" or "Extract to Folder/").
   */
  public static void showExtractOptionsDialog(
      Context context, File archiveFile, Runnable onComplete) {
    if (archiveFile == null || !archiveFile.exists()) return;

    File firstVolume = resolveFirstVolume(archiveFile);
    String baseName = getBaseArchiveName(firstVolume.getName());
    File currentDir = firstVolume.getParentFile();
    File folderDest = new File(currentDir, baseName);

    List<String> options = new ArrayList<>();
    options.add(context.getString(R.string.zip_extract_here));
    options.add(context.getString(R.string.extract_to_named_folder, baseName));

    new DialogCompat(context)
        .setTitle(R.string.extract_archive)
        .setItems(
            options.toArray(new String[0]),
            (dialog, which) -> {
              dialog.dismiss();
              File targetDir = (which == 0) ? currentDir : folderDest;
              extractArchive(context, firstVolume, targetDir, onComplete);
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  /**
   * Extracts the specified archive to the target destination directory asynchronously.
   */
  public static void extractArchive(
      Context context, File archiveFile, File destinationDir, Runnable onSuccess) {
    if (archiveFile == null || !archiveFile.exists()) {
      GhostToast.makeText(context, R.string.zip_error, GhostToast.LENGTH_SHORT).show();
      return;
    }

    File firstVolume = resolveFirstVolume(archiveFile);
    AtomicBoolean isCancelled = new AtomicBoolean(false);

    showProgressDialog(context, firstVolume.getName(), () -> isCancelled.set(true));

    executor.execute(
        () -> {
          try {
            String pathLower = firstVolume.getName().toLowerCase(Locale.ROOT);
            if (isZip(pathLower)) {
              extractZip(context, firstVolume, destinationDir, isCancelled);
            } else if (isRar(pathLower)) {
              extractRar(context, firstVolume, destinationDir, isCancelled);
            } else if (is7z(pathLower)) {
              extract7z(context, firstVolume, destinationDir, isCancelled);
            } else if (isTar(pathLower)) {
              extractTar(context, firstVolume, destinationDir, isCancelled);
            } else {
              // Fallback to Zip4j
              extractZip(context, firstVolume, destinationDir, isCancelled);
            }

            mainHandler.post(
                () -> {
                  dismissProgressDialog();
                  if (!isCancelled.get()) {
                    boolean hostGone = isHostGone(context);
                    GhostToast.makeText(
                            hostGone ? context.getApplicationContext() : context,
                            R.string.zip_extracted_ok,
                            GhostToast.LENGTH_SHORT)
                        .show();
                    if (hostGone) return;
                    if (onSuccess != null) onSuccess.run();
                    if (context instanceof FileManagerActivity) {
                      ((FileManagerActivity) context).refreshFileList();
                    }
                  }
                });

          } catch (Exception e) {
            mainHandler.post(
                () -> {
                  dismissProgressDialog();
                  if (!isCancelled.get()) {
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    GhostToast.makeText(
                            isHostGone(context) ? context.getApplicationContext() : context,
                            context.getString(R.string.zip_error_prefix, msg),
                            GhostToast.LENGTH_LONG)
                        .show();
                  }
                });
          }
        });
  }

  private static void extractZip(
      Context context, File archiveFile, File destDir, AtomicBoolean isCancelled)
      throws Exception {
    ZipFile zipFile = new ZipFile(archiveFile);
    String password = null;

    if (zipFile.isEncrypted()) {
      password = promptPasswordSync(context, archiveFile.getName());
      if (password == null) {
        isCancelled.set(true);
        return;
      }
      zipFile.setPassword(password.toCharArray());
    }

    if (!destDir.exists()) {
      destDir.mkdirs();
    }

    zipFile.setRunInThread(true);
    zipFile.extractAll(destDir.getAbsolutePath());

    ProgressMonitor monitor = zipFile.getProgressMonitor();
    while (monitor.getState() == ProgressMonitor.State.BUSY) {
      if (isCancelled.get()) {
        monitor.setCancelAllTasks(true);
        break;
      }
      int percent = monitor.getPercentDone();
      String name = monitor.getFileName();
      mainHandler.post(() -> updateProgress(name != null ? name : "", percent));
      try {
        Thread.sleep(80);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    if (monitor.getResult() == ProgressMonitor.Result.ERROR) {
      if (monitor.getException() != null) {
        throw monitor.getException();
      }
      throw new Exception(context.getString(R.string.archive_wrong_password));
    }
  }

  private static void extractRar(
      Context context, File archiveFile, File destDir, AtomicBoolean isCancelled)
      throws Exception {
    File firstVolume = resolveFirstVolume(archiveFile);
        String password = null;
    Archive archive = null;
    try {
      archive = new Archive(firstVolume);
      if (archive.isEncrypted()) {
        archive.close();
        archive = null;
        password = promptPasswordSync(context, firstVolume.getName());
        if (password == null) {
          isCancelled.set(true);
          return;
        }
        archive = new Archive(firstVolume, password);
      }

      List<FileHeader> headers = archive.getFileHeaders();
      if (headers == null || headers.isEmpty()) {
        return;
      }

      int total = headers.size();
      int count = 0;
      for (FileHeader header : headers) {
        if (isCancelled.get()) break;

        if (header.isEncrypted() && (password == null || password.isEmpty())) {
          archive.close();
          archive = null;
          password = promptPasswordSync(context, firstVolume.getName());
          if (password == null) {
            isCancelled.set(true);
            return;
          }
          archive = new Archive(firstVolume, password);
        }

        count++;
        String rawName = header.getFileName().trim();
        String sanitized = rawName.replace('\\', '/');
        if (sanitized.contains("..")) {
          continue;
        }

        File out = new File(destDir, sanitized);
        if (header.isDirectory()) {
          out.mkdirs();
        } else {
          File parent = out.getParentFile();
          if (parent != null && !parent.exists()) {
            parent.mkdirs();
          }
          try (FileOutputStream fos = new FileOutputStream(out)) {
            archive.extractFile(header, fos);
          }
        }
        final int percent = (int) ((count * 100.0) / total);
        final String name = out.getName();
        mainHandler.post(() -> updateProgress(name, percent));
      }
    } catch (UnsupportedRarV5Exception v5) {
      throw new Exception(context.getString(R.string.unsupported_rar_v5), v5);
    } finally {
      if (archive != null) {
        try {
          archive.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static void extract7z(
      Context context, File archiveFile, File destDir, AtomicBoolean isCancelled)
      throws Exception {
    SevenZFile sevenZFile = null;
    try {
      try {
        sevenZFile = new SevenZFile(archiveFile);
      } catch (Exception e) {
        if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("password")) {
          String password = promptPasswordSync(context, archiveFile.getName());
          if (password == null) {
            isCancelled.set(true);
            return;
          }
          sevenZFile = new SevenZFile(archiveFile, password.getBytes(StandardCharsets.UTF_16LE));
        } else {
          throw e;
        }
      }

      if (!destDir.exists()) destDir.mkdirs();

      SevenZArchiveEntry entry;
      while ((entry = sevenZFile.getNextEntry()) != null) {
        if (isCancelled.get()) break;
        String name = entry.getName().replace('\\', '/');
        if (name.contains("..")) continue;

        File out = new File(destDir, name);
        if (entry.isDirectory()) {
          out.mkdirs();
        } else {
          File parent = out.getParentFile();
          if (parent != null && !parent.exists()) parent.mkdirs();
          try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = sevenZFile.read(buffer)) != -1) {
              fos.write(buffer, 0, count);
            }
          }
        }
        final String entryName = out.getName();
        mainHandler.post(() -> updateProgress(entryName, 50));
      }
    } finally {
      if (sevenZFile != null) {
        sevenZFile.close();
      }
    }
  }

  private static void extractTar(
      Context context, File archiveFile, File destDir, AtomicBoolean isCancelled)
      throws Exception {
    InputStream fis = new FileInputStream(archiveFile);
    String lower = archiveFile.getName().toLowerCase(Locale.ROOT);
    if (lower.endsWith(".gz") || lower.endsWith(".tgz")) {
      fis = new GZIPInputStream(fis);
    } else if (lower.endsWith(".bz2") || lower.endsWith(".tbz2")) {
      fis = new BZip2CompressorInputStream(fis);
    } else if (lower.endsWith(".xz") || lower.endsWith(".txz")) {
      fis = new XZCompressorInputStream(fis);
    }

    if (!destDir.exists()) destDir.mkdirs();

    try (TarArchiveInputStream tis = new TarArchiveInputStream(fis)) {
      TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        if (isCancelled.get()) break;
        String name = entry.getName().replace('\\', '/');
        if (name.contains("..")) continue;

        File out = new File(destDir, name);
        if (entry.isDirectory()) {
          out.mkdirs();
        } else {
          File parent = out.getParentFile();
          if (parent != null && !parent.exists()) parent.mkdirs();
          try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = tis.read(buffer)) != -1) {
              fos.write(buffer, 0, count);
            }
          }
        }
        final String entryName = out.getName();
        mainHandler.post(() -> updateProgress(entryName, 50));
      }
    }
  }

  private static String promptPasswordSync(Context context, String archiveName) {
    CountDownLatch latch = new CountDownLatch(1);
    final String[] result = new String[1];
    mainHandler.post(
        () -> {
          if (isHostGone(context)) {
            latch.countDown();
            return;
          }
          View view = LayoutInflater.from(context).inflate(R.layout.dialog_archive_password, null);
          TextInputEditText etPassword = view.findViewById(R.id.et_archive_password);
          TextView tvMsg = view.findViewById(R.id.tv_password_msg);
          tvMsg.setText(context.getString(R.string.archive_enter_password) + "\n" + archiveName);

          new DialogCompat(context)
              .setTitle(R.string.archive_password_required)
              .setView(view)
              .setCancelable(false)
              .setPositiveButton(
                  R.string.ok,
                  (d, w) -> {
                    result[0] =
                        etPassword.getText() != null ? etPassword.getText().toString() : "";
                    latch.countDown();
                  })
              .setNegativeButton(
                  R.string.cancel,
                  (d, w) -> {
                    result[0] = null;
                    latch.countDown();
                  })
              .show();
        });
    try {
      while (!latch.await(HOST_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
        if (isHostGone(context)) return null;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    return result[0];
  }

  private static void showProgressDialog(
      Context context, String title, Runnable onCancel) {
    mainHandler.post(
        () -> {
          if (isHostGone(context)) return;
          View view = LayoutInflater.from(context).inflate(R.layout.dialog_zip_progress, null);
          progressBar = view.findViewById(R.id.progressBar);
          tvFileName = view.findViewById(R.id.tvZipFileName);
          tvPercent = view.findViewById(R.id.tvZipPercent);
          tvDetails = view.findViewById(R.id.tvZipDetails);
          progressBar.setMax(100);
          progressBar.setProgress(0);
          tvFileName.setText(title);
          tvPercent.setText("0%");
          tvDetails.setText(R.string.extracting_archive);

          progressDialog =
              new DialogCompat(context)
                  .setTitle(R.string.extracting_archive)
                  .setView(view)
                  .setCancelable(false)
                  .setNegativeButton(
                      R.string.cancel,
                      (d, w) -> {
                        if (onCancel != null) onCancel.run();
                        dismissProgressDialog();
                      })
                  .create();
          progressDialog.show();
        });
  }

  private static void updateProgress(String fileName, int percent) {
    if (progressDialog == null || !progressDialog.isShowing()) return;
    progressBar.setProgress(percent);
    tvPercent.setText(percent + "%");
    tvFileName.setText(fileName);
  }

  /**
   * Dismisses the progress dialog and drops every static reference to it and its views, so the
   * hosting Activity is not retained after the operation. A dialog whose Activity was already
   * destroyed (for example by a rotation during extraction) lost its window together with the
   * Activity; dismissing it would throw, so it is only forgotten.
   */
  private static void dismissProgressDialog() {
    AlertDialog dialog = progressDialog;
    progressDialog = null;
    progressBar = null;
    tvFileName = null;
    tvPercent = null;
    tvDetails = null;
    if (dialog == null || !dialog.isShowing()) return;
    Activity owner = Glass.findActivity(dialog.getContext());
    if (owner != null && owner.isDestroyed()) return;
    dialog.dismiss();
  }

  /** True when {@code context} belongs to an Activity that is finishing or already destroyed. */
  private static boolean isHostGone(Context context) {
    Activity activity = Glass.findActivity(context);
    return activity != null && (activity.isFinishing() || activity.isDestroyed());
  }
}
