package ir.hanzodev1375.ghostide.enums;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Build system or version-control marker that identifies a folder as a project root.
 *
 * <p>The constants are declared from the strongest marker to the weakest, and {@link
 * #detect(String[])} relies on that order: a Gradle build file wins over everything, a plain {@code
 * .git} directory only labels a folder that has no stronger marker.
 */
public enum ProjectType {
  GRADLE("gradle"),
  MAVEN("maven"),
  NPM("npm"),
  CARGO("cargo"),
  GIT("git");

  private final String label;

  ProjectType(String label) {
    this.label = label;
  }

  /** @return the short lowercase label shown next to the folder name. */
  @NonNull
  public String label() {
    return label;
  }

  /**
   * Picks the strongest project type from the names a folder directly contains, so a project root
   * can be recognised without any extra disk access.
   *
   * @param names entry names of the folder, in any order; may be {@code null} when unreadable
   * @return the detected type, or {@code null} for a plain folder
   */
  @Nullable
  public static ProjectType detect(@Nullable String[] names) {
    if (names == null) return null;
    ProjectType strongest = null;
    for (String name : names) {
      ProjectType found = fromMarker(name);
      if (found == GRADLE) return GRADLE;
      if (found != null && (strongest == null || found.ordinal() < strongest.ordinal())) {
        strongest = found;
      }
    }
    return strongest;
  }

  @Nullable
  private static ProjectType fromMarker(String name) {
    return switch (name) {
      case "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts" -> GRADLE;
      case "pom.xml" -> MAVEN;
      case "package.json" -> NPM;
      case "Cargo.toml" -> CARGO;
      case ".git" -> GIT;
      default -> null;
    };
  }
}
