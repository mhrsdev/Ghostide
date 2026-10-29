package ir.hanzodev1375.ghostide.vibe;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** File helpers shared by the Vibe Coding classes. */
final class VibeFiles {

  private static final Set<PosixFilePermission> OWNER_ONLY_FILE =
      PosixFilePermissions.fromString("rw-------");
  private static final Set<PosixFilePermission> OWNER_ONLY_DIR =
      PosixFilePermissions.fromString("rwx------");

  private VibeFiles() {}

  /**
   * Writes {@code content} to {@code target} by writing a sibling temporary file and renaming it, so
   * a reader never sees a half-written file. The file is restricted to its owner when the file
   * system supports POSIX permissions.
   */
  static void writeAtomically(Path target, byte[] content) throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
    try {
      Files.write(temp, content);
      restrictToOwner(temp, false);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** Creates {@code dir} (and parents) and restricts the leaf to its owner where supported. */
  static void createPrivateDirectory(Path dir) throws IOException {
    Files.createDirectories(dir);
    restrictToOwner(dir, true);
  }

  private static void restrictToOwner(Path path, boolean directory) throws IOException {
    try {
      Files.setPosixFilePermissions(path, directory ? OWNER_ONLY_DIR : OWNER_ONLY_FILE);
    } catch (UnsupportedOperationException notPosix) {
      return;
    }
  }

  /**
   * Deletes {@code root} and everything below it. Symbolic links are removed but never followed, so
   * a link inside a rootfs that points at shared storage cannot make this delete user files.
   */
  static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException error)
              throws IOException {
            if (error != null) {
              throw error;
            }
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
