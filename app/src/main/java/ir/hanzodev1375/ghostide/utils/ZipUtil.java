package ir.hanzodev1375.ghostide.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.activity.FileManagerActivity;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import net.lingala.zip4j.progress.ProgressMonitor;

public class ZipUtil {

  private static final ExecutorService executor = Executors.newFixedThreadPool(2);
  private static final Handler mainHandler = new Handler(Looper.getMainLooper());
  private static AlertDialog progressDialog;
  private static ProgressBar progressBar;
  private static TextView tvFileName, tvPercent, tvDetails;
  private static String defaultName = "archive.zip";

  private static final CompressionLevel[] COMPRESSION_LEVELS = {
    CompressionLevel.FASTEST, CompressionLevel.NORMAL, CompressionLevel.MAXIMUM
  };

  private static final long[] PRESET_SPLIT_SIZES = {
    10L * 1024 * 1024,        // 10 MB
    50L * 1024 * 1024,        // 50 MB
    100L * 1024 * 1024,       // 100 MB
    700L * 1024 * 1024,       // 700 MB (CD)
    2L * 1024 * 1024 * 1024,  // 2 GB
    4L * 1024 * 1024 * 1024,  // 4 GB (DVD/FAT32)
    -1                        // Custom
  };

  private static CompressionLevel getSelectedCompressionLevel(Spinner spinner) {
    int position = spinner.getSelectedItemPosition();
    if (position < 0 || position >= COMPRESSION_LEVELS.length) {
      return CompressionLevel.NORMAL;
    }
    return COMPRESSION_LEVELS[position];
  }

  private static long calculateTotalSize(List<File> files) {
    long total = 0;
    for (File f : files) {
      total += getSize(f);
    }
    return total;
  }

  private static long getSize(File f) {
    if (f.isFile()) return f.length();
    long size = 0;
    File[] children = f.listFiles();
    if (children != null) {
      for (File child : children) {
        size += getSize(child);
      }
    }
    return size;
  }

  private static void showProgressDialog(Context context, long totalBytes) {
    if (isHostGone(context)) return;
    View view = LayoutInflater.from(context).inflate(R.layout.dialog_zip_progress, null);
    progressBar = view.findViewById(R.id.progressBar);
    tvFileName = view.findViewById(R.id.tvZipFileName);
    tvPercent = view.findViewById(R.id.tvZipPercent);
    tvDetails = view.findViewById(R.id.tvZipDetails);
    progressBar.setMax(100);
    progressDialog =
        new DialogCompat(context)
            .setTitle(R.string.creating_zip)
            .setView(view)
            .setCancelable(false)
            .create();
    progressDialog.show();
    String totalStr = formatSize(totalBytes);
    tvFileName.setText(R.string.preparing);
    tvPercent.setText("0%");
    tvDetails.setText(String.format(Locale.getDefault(), "0 / %s", totalStr));
  }

  private static void updateProgress(String fileName, long processedBytes, long totalBytes) {
    if (progressDialog == null || !progressDialog.isShowing()) return;
    int percent = totalBytes <= 0 ? 100 : (int) (processedBytes * 100 / totalBytes);
    progressBar.setProgress(percent);
    tvPercent.setText(percent + "%");
    tvFileName.setText(fileName);
    String processedStr = formatSize(processedBytes);
    String totalStr = formatSize(totalBytes);
    tvDetails.setText(String.format(Locale.getDefault(), "%s / %s", processedStr, totalStr));
  }

  public static String formatSize(long bytes) {
    if (bytes >= 1024 * 1024 * 1024) {
      return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    } else if (bytes >= 1024 * 1024) {
      return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024));
    } else if (bytes >= 1024) {
      return String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0);
    } else {
      return bytes + " B";
    }
  }

  /**
   * Dismisses the progress dialog and drops every static reference to it and its views, so the
   * hosting Activity is not retained after the operation. A dialog whose Activity was already
   * destroyed (for example by a rotation while zipping) lost its window together with the
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

  private static void zipFilesWithProgress(
      List<File> sources,
      File destination,
      String password,
      Context context,
      Runnable onSuccess,
      CompressionLevel compressionLevel,
      long splitLength) {
    executor.execute(
        () -> {
          try {
            long totalBytes = calculateTotalSize(sources);
            mainHandler.post(() -> showProgressDialog(context, totalBytes));

            ZipParameters parameters = new ZipParameters();
            parameters.setCompressionMethod(CompressionMethod.DEFLATE);
            parameters.setCompressionLevel(compressionLevel);
            if (password != null && !password.isEmpty()) {
              parameters.setEncryptFiles(true);
              parameters.setEncryptionMethod(EncryptionMethod.AES);
              parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            }

            ZipFile zipFile =
                new ZipFile(destination, password != null ? password.toCharArray() : null);

            if (splitLength >= 65536) {
              zipFile.setRunInThread(true);
              if (sources.size() == 1 && sources.get(0).isDirectory()) {
                zipFile.createSplitZipFileFromFolder(sources.get(0), parameters, true, splitLength);
              } else {
                zipFile.createSplitZipFile(sources, parameters, true, splitLength);
              }

              ProgressMonitor monitor = zipFile.getProgressMonitor();
              while (monitor.getState() == ProgressMonitor.State.BUSY) {
                int percent = monitor.getPercentDone();
                String name = monitor.getFileName();
                long done = monitor.getWorkCompleted();
                long total = monitor.getTotalWork() > 0 ? monitor.getTotalWork() : totalBytes;
                mainHandler.post(() -> updateProgress(name != null ? name : "", done, total));
                try {
                  Thread.sleep(80);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                }
              }
              if (monitor.getResult() == ProgressMonitor.Result.ERROR) {
                if (monitor.getException() != null) throw monitor.getException();
              }
            } else {
              zipFile.setRunInThread(true);
              for (File src : sources) {
                if (src.isDirectory()) {
                  zipFile.addFolder(src, parameters);
                } else {
                  zipFile.addFile(src, parameters);
                }
              }

              ProgressMonitor monitor = zipFile.getProgressMonitor();
              while (monitor.getState() == ProgressMonitor.State.BUSY) {
                int percent = monitor.getPercentDone();
                String name = monitor.getFileName();
                long done = monitor.getWorkCompleted();
                long total = monitor.getTotalWork() > 0 ? monitor.getTotalWork() : totalBytes;
                mainHandler.post(() -> updateProgress(name != null ? name : "", done, total));
                try {
                  Thread.sleep(80);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                }
              }
              if (monitor.getResult() == ProgressMonitor.Result.ERROR) {
                if (monitor.getException() != null) throw monitor.getException();
              }
            }

            mainHandler.post(
                () -> {
                  dismissProgressDialog();
                  if (isHostGone(context)) {
                    GhostToast.makeText(
                            context.getApplicationContext(),
                            R.string.zip_created_success,
                            GhostToast.LENGTH_LONG)
                        .show();
                    return;
                  }
                  if (onSuccess != null) onSuccess.run();
                  if (context instanceof FileManagerActivity) {
                    ((FileManagerActivity) context).refreshFileList();
                  }
                });

          } catch (Exception e) {
            mainHandler.post(
                () -> {
                  dismissProgressDialog();
                  GhostToast.makeText(
                          isHostGone(context) ? context.getApplicationContext() : context,
                          context.getString(R.string.zip_error) + " " + e.getMessage(),
                          GhostToast.LENGTH_LONG)
                      .show();
                });
          }
        });
  }

  public static void showZipDialog(Context context, List<File> selectedFiles) {
    if (selectedFiles == null || selectedFiles.isEmpty()) {
      GhostToast.makeText(context, R.string.no_items_selected, GhostToast.LENGTH_SHORT).show();
      return;
    }

    View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_zip, null);
    TextInputLayout tilName = dialogView.findViewById(R.id.til_zip_name);
    TextInputEditText etName = dialogView.findViewById(R.id.et_zip_name);
    TextInputLayout tilPassword = dialogView.findViewById(R.id.til_zip_password);
    TextInputEditText etPassword = dialogView.findViewById(R.id.et_zip_password);
    SwitchMaterial swPassword = dialogView.findViewById(R.id.sw_zip_password);
    Spinner zipspinner = dialogView.findViewById(R.id.zipspinner);

    SwitchMaterial swSplit = dialogView.findViewById(R.id.sw_zip_split);
    View layoutSplitOptions = dialogView.findViewById(R.id.layout_split_options);
    Spinner spinnerSplitSize = dialogView.findViewById(R.id.spinner_split_size);
    TextInputLayout tilCustomSplit = dialogView.findViewById(R.id.til_custom_split_size);
    TextInputEditText etCustomSplit = dialogView.findViewById(R.id.et_custom_split_size);

    ArrayAdapter<String> compressionAdapter =
        new ArrayAdapter<>(
            context,
            android.R.layout.simple_spinner_item,
            new String[] {"سریع‌ترین", "عادی", "حداکثر"});
    compressionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    zipspinner.setAdapter(compressionAdapter);
    zipspinner.setSelection(1); // default: NORMAL

    tilPassword.setVisibility(View.GONE);
    swPassword.setOnCheckedChangeListener(
        (btn, isChecked) -> {
          tilPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
          if (!isChecked) etPassword.setText("");
        });

    String[] splitPresets =
        new String[] {
          "10 MB",
          "50 MB",
          "100 MB",
          "700 MB (CD)",
          "2 GB",
          "4 GB (DVD)",
          context.getString(R.string.split_custom_mb)
        };
    ArrayAdapter<String> splitAdapter =
        new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, splitPresets);
    splitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinnerSplitSize.setAdapter(splitAdapter);
    spinnerSplitSize.setSelection(0);

    spinnerSplitSize.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            tilCustomSplit.setVisibility(position == 6 ? View.VISIBLE : View.GONE);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    swSplit.setOnCheckedChangeListener(
        (btn, isChecked) -> {
          layoutSplitOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
          if (!isChecked) {
            tilCustomSplit.setVisibility(View.GONE);
            etCustomSplit.setText("");
          }
        });

    if (selectedFiles.size() == 1) {
      String name = selectedFiles.get(0).getName();
      int dot = name.lastIndexOf('.');
      if (dot > 0) name = name.substring(0, dot);
      defaultName = name + ".zip";
    }
    etName.setText(defaultName);

    DialogCompat builder =
        new DialogCompat(context)
            .setTitle(R.string.create_zip_title)
            .setView(dialogView)
            .setPositiveButton(R.string.create, null)
            .setNegativeButton(R.string.cancel, null);

    AlertDialog dialog = builder.create();
    dialog.show();
    dialog
        .getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(
            v -> {
              String zipName = etName.getText().toString().trim();
              if (zipName.isEmpty()) zipName = defaultName;
              if (!zipName.endsWith(".zip")) zipName += ".zip";
              File parentDir = selectedFiles.get(0).getParentFile();
              File zipFile = new File(parentDir, zipName);
              String password = swPassword.isChecked() ? etPassword.getText().toString() : null;
              if (password != null && password.isEmpty()) password = null;

              long splitLength = 0;
              if (swSplit.isChecked()) {
                int selectedPos = spinnerSplitSize.getSelectedItemPosition();
                if (selectedPos >= 0 && selectedPos < 6) {
                  splitLength = PRESET_SPLIT_SIZES[selectedPos];
                } else if (selectedPos == 6) {
                  String customStr =
                      etCustomSplit.getText() != null
                          ? etCustomSplit.getText().toString().trim()
                          : "";
                  if (!customStr.isEmpty()) {
                    try {
                      long mb = Long.parseLong(customStr);
                      if (mb > 0) {
                        splitLength = mb * 1024 * 1024;
                      }
                    } catch (NumberFormatException ignored) {
                    }
                  }
                  if (splitLength <= 0) {
                    splitLength = 10L * 1024 * 1024;
                  }
                }
              }

              dialog.dismiss();
              zipFilesWithProgress(
                  selectedFiles,
                  zipFile,
                  password,
                  context,
                  () -> {
                    GhostToast.makeText(
                            context, R.string.zip_created_success, GhostToast.LENGTH_LONG)
                        .show();
                  },
                  getSelectedCompressionLevel(zipspinner),
                  splitLength);
            });
  }
}
