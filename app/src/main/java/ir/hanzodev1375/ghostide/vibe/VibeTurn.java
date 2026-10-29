package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import java.util.Objects;

/**
 * One earlier message of a conversation, sent along with a run so Hermes builds that do not keep
 * the conversation server-side (0.19) can continue it. Newer builds treat explicit history as
 * authoritative as well, so passing it is always safe.
 */
public final class VibeTurn {

  /** Who wrote the message. */
  public enum Role {
    USER("user"),
    ASSISTANT("assistant");

    private final String wireValue;

    Role(String wireValue) {
      this.wireValue = wireValue;
    }

    String wireValue() {
      return wireValue;
    }
  }

  private final Role role;
  private final String text;

  public VibeTurn(@NonNull Role role, @NonNull String text) {
    this.role = Objects.requireNonNull(role, "role");
    this.text = Objects.requireNonNull(text, "text");
  }

  @NonNull
  public Role getRole() {
    return role;
  }

  @NonNull
  public String getText() {
    return text;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof VibeTurn
        && role == ((VibeTurn) other).role
        && text.equals(((VibeTurn) other).text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, text);
  }
}
