package ir.hanzodev1375.ghostide.utils;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class FileUtil {
  private static final String TAG = "FileUtil";
  private static final String TEMP_SUFFIX = ".tmp";

  /** Writes the complete new content of a file to the given stream. */
  public interface ContentWriter {
    void writeTo(OutputStream out) throws IOException;
  }

  public static boolean isExists(String path) {
    return new File(path).exists();
  }

  /**
   * Reads a whole file into a byte array in a way that works on every Android API level.
   *
   * <p>{@link FileInputStream#readAllBytes()} was only added in API 33, so older devices (Android
   * 8–12) crash with {@link NoSuchMethodError}. Always use this helper instead of the newer
   * {@code readAllBytes()} stream methods.
   */
  public static byte[] readBytesCompat(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] chunk = new byte[4096];
      int bytesRead;
      while ((bytesRead = in.read(chunk)) != -1) {
        out.write(chunk, 0, bytesRead);
      }
      return out.toByteArray();
    }
  }

  /**
   * Replaces the content of {@code target} so that a failed write never leaves a truncated file.
   *
   * <p>The new content is written to a hidden temporary file in the same directory, synced to disk
   * and then renamed over the target. A failure while writing (full storage, revoked permission,
   * process death) therefore keeps the previous content intact. A symbolic link is resolved first
   * so the link itself survives, and the POSIX permissions of an existing file (for example the
   * executable bit of a script) are copied to the new file where the file system supports it.
   * Missing parent directories are created. When the directory itself is not writable, the file
   * is written in place as a last resort, which is not atomic.
   *
   * @param target the file to create or replace.
   * @param writer writes the complete new content; it must not close the stream.
   * @throws IOException when the content could not be written or the target could not be
   *     replaced. Except for the in-place fallback, the target is unchanged in that case.
   */
  public static void writeAtomically(File target, ContentWriter writer) throws IOException {
    File destination = target.exists() ? target.getCanonicalFile() : target.getAbsoluteFile();
    File dir = destination.getParentFile();
    if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
      throw new IOException("Cannot create directory " + dir);
    }
    if (dir == null || !dir.canWrite()) {
      try (OutputStream out = new FileOutputStream(destination)) {
        writer.writeTo(out);
      }
      return;
    }
    File temp = File.createTempFile("." + destination.getName() + ".", TEMP_SUFFIX, dir);
    boolean replaced = false;
    try {
      try (FileOutputStream out = new FileOutputStream(temp)) {
        writer.writeTo(out);
        out.flush();
        out.getFD().sync();
      }
      copyPermissions(destination, temp);
      if (!temp.renameTo(destination)) {
        throw new IOException("Cannot replace " + destination);
      }
      replaced = true;
    } finally {
      if (!replaced && temp.exists() && !temp.delete()) {
        Log.w(TAG, "Could not delete temporary file " + temp);
      }
    }
  }

  private static void copyPermissions(File from, File to) {
    if (!from.exists()) return;
    try {
      Files.setPosixFilePermissions(to.toPath(), Files.getPosixFilePermissions(from.toPath()));
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      Log.w(TAG, "Could not keep the permissions of " + from + ": " + e.getMessage());
    }
  }
}
