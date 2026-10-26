package ir.hanzodev1375.ghostide.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import com.blankj.utilcode.util.FileIOUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class UriFileImporter {

  private static final String TAG = "UriFileImporter";
  private static final String TEMP_DIR = "GhostIDE/temp";

  private UriFileImporter() {}

  public static String getRealPathFromUri(Context context, Uri uri) {
    if (uri == null) return null;
    if ("file".equals(uri.getScheme())) {
      return uri.getPath();
    }
    if ("content".equals(uri.getScheme())) {
      return copyFileFromContentUri(context, uri);
    }
    return null;
  }

  /**
   * Copies the content behind {@code uri} into the app cache and returns the copy's absolute path.
   *
   * <p>The display name reported by the providing app is untrusted: only its last path segment is
   * used, and the copy is refused unless it lands directly inside the cache's temp directory, so a
   * name such as {@code ../../shared_prefs/x.xml} cannot overwrite private app files.
   *
   * @return the copy's absolute path, or {@code null} when the content could not be read or copied
   */
  public static String copyFileFromContentUri(Context context, Uri uri) {
    String fileName = safeFileName(queryDisplayName(context, uri));
    File tempDir = new File(context.getCacheDir(), TEMP_DIR);
    if (!tempDir.isDirectory() && !tempDir.mkdirs()) {
      Log.w(TAG, "Cannot create the import directory");
      return null;
    }
    File destFile = new File(tempDir, fileName);
    if (!isDirectChild(tempDir, destFile)) {
      Log.w(TAG, "Rejected an imported file name outside the import directory");
      return null;
    }
    try (InputStream is = context.getContentResolver().openInputStream(uri)) {
      if (is == null) {
        return null;
      }
      try (FileOutputStream os = new FileOutputStream(destFile)) {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) os.write(buffer, 0, len);
      }
      return destFile.getAbsolutePath();
    } catch (IOException | RuntimeException e) {
      Log.w(TAG, "Could not copy the shared content", e);
      if (destFile.exists() && !destFile.delete()) {
        Log.w(TAG, "Could not delete a partial import");
      }
      return null;
    }
  }

  private static String queryDisplayName(Context context, Uri uri) {
    try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (nameIndex != -1) return cursor.getString(nameIndex);
      }
    } catch (RuntimeException e) {
      Log.w(TAG, "Could not read the shared file name", e);
    }
    return null;
  }

  /**
   * Reduces an untrusted display name to a single, non-special file name.
   *
   * @param rawName name reported by another app, may be {@code null}
   * @return the last path segment of {@code rawName} without NUL characters, or a generated name
   *     when nothing usable remains
   */
  static String safeFileName(String rawName) {
    String name = rawName == null ? "" : rawName.replace("\0", "");
    int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    name = name.substring(separator + 1).trim();
    if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
      return "temp_file_" + System.currentTimeMillis();
    }
    return name;
  }

  private static boolean isDirectChild(File dir, File child) {
    try {
      File parent = child.getCanonicalFile().getParentFile();
      return parent != null && parent.equals(dir.getCanonicalFile());
    } catch (IOException e) {
      Log.w(TAG, "Cannot resolve the import path", e);
      return false;
    }
  }

  public static String saveTextToCache(Context context, String text) {
    File dir = new File(context.getCacheDir(), TEMP_DIR);
    if (!dir.exists()) dir.mkdirs();
    String fileName = "shared_text_" + System.currentTimeMillis() + ".txt";
    File file = new File(dir, fileName);
    FileIOUtils.writeFileFromString(file.getAbsolutePath(), text);
    return file.getAbsolutePath();
  }
}
