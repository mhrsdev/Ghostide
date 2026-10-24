package ir.hanzodev1375.ghostide.models;

import androidx.annotation.Nullable;
import java.io.File;
import java.util.Objects;

import ir.hanzodev1375.ghostide.enums.FileState;

public class FileManagerModel {
  /** Marker for a size or child count that was not read from disk. */
  public static final int UNKNOWN = -1;

  private String path;
  private String name;
  private FileState state;
  private long lastModified;
  @Nullable private final Boolean directory;
  private final long size;
  private final int childCount;
  @Nullable private final String projectTag;

  public FileManagerModel(String path, String name, FileState state, long lastModified) {
    this(path, name, state, lastModified, null, UNKNOWN, UNKNOWN, null);
  }

  /**
   * Creates a model whose file-system facts were read once on a background thread, so binding a
   * row never has to touch the disk again.
   *
   * @param directory whether the entry is a directory, or {@code null} to resolve it lazily
   * @param size length in bytes for a file, {@link #UNKNOWN} for a directory
   * @param childCount number of visible entries for a directory, {@link #UNKNOWN} if unreadable
   * @param projectTag short build-system label (for example "gradle"), or {@code null}
   */
  public FileManagerModel(
      String path,
      String name,
      FileState state,
      long lastModified,
      @Nullable Boolean directory,
      long size,
      int childCount,
      @Nullable String projectTag) {
    this.path = path;
    this.name = name;
    this.state = state;
    this.lastModified = lastModified;
    this.directory = directory;
    this.size = size;
    this.childCount = childCount;
    this.projectTag = projectTag;
  }

  public String getPath() {
    return path;
  }

  public String getName() {
    return name;
  }

  public FileState getState() {
    return state;
  }

  public void setState(FileState state) {
    this.state = state;
  }

  public long getLastModified() {
    return lastModified;
  }

  /**
   * @return the directory flag captured when the listing was read; models built without it fall
   *     back to querying the file system.
   */
  public boolean isDirectory() {
    if (directory != null) return directory;
    File file = new File(path);
    return file.exists() && file.isDirectory();
  }

  public long getSize() {
    return size;
  }

  public int getChildCount() {
    return childCount;
  }

  @Nullable
  public String getProjectTag() {
    return projectTag;
  }

  /** @return true when the listing facts shown in a row (size, children, tag) are equal. */
  public boolean hasSameListingFacts(FileManagerModel other) {
    return size == other.size
        && childCount == other.childCount
        && Objects.equals(directory, other.directory)
        && Objects.equals(projectTag, other.projectTag);
  }
}
