package ir.hanzodev1375.ghostide.vibe.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.HermesApiException;
import ir.hanzodev1375.ghostide.vibe.HermesRunSession;
import ir.hanzodev1375.ghostide.vibe.VibeEvent;
import ir.hanzodev1375.ghostide.vibe.VibeTurn;
import ir.hanzodev1375.ghostide.vibe.VibeUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The ordered, bounded list of rows shown in the chat, plus the rules that turn {@link VibeEvent}s
 * into rows. It has no Android dependency and is not thread safe: use it from one thread (the main
 * thread in the app).
 *
 * <p>How events map to rows:
 *
 * <ul>
 *   <li>text chunks extend the open assistant row, or start a new one after a tool or approval;
 *   <li>{@code tool.started} adds a running tool row and {@code tool.completed} settles it (found
 *       by call id, else the newest running row of the same tool);
 *   <li>an approval request adds an open approval row that stays until it is settled by this
 *       client, by another client or by the end of the run;
 *   <li>{@code RunCompleted.output()} is authoritative: it replaces the open assistant row (or is
 *       appended when none is open), because streamed text can be incomplete after a reconnect;
 *   <li>reasoning, unknown and malformed events are ignored.
 * </ul>
 */
public final class VibeTranscript {

  /** Rows kept; older rows are dropped from the front. */
  public static final int MAX_ITEMS = 300;

  /** Longest text kept for one row. */
  public static final int MAX_TEXT_CHARS = 60_000;

  /** Total characters of conversation sent back to the agent as history. */
  public static final int MAX_HISTORY_CHARS = 120_000;

  private static final String ELLIPSIS = "…";

  private final ArrayList<ChatItem> items = new ArrayList<>();
  private long nextId;
  private String sessionId;

  /**
   * @param restored rows loaded from disk, oldest first; may be empty
   * @param sessionId the conversation key passed to the agent
   */
  public VibeTranscript(@NonNull List<ChatItem> restored, @NonNull String sessionId) {
    this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    long maxId = 0;
    for (ChatItem item : restored) {
      items.add(item);
      maxId = Math.max(maxId, item.id());
    }
    nextId = maxId + 1;
    trimToLimit();
  }

  /** An immutable copy of the current rows, safe to hand to a list adapter. */
  @NonNull
  public List<ChatItem> snapshot() {
    return List.copyOf(items);
  }

  @NonNull
  public String sessionId() {
    return sessionId;
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  /** Removes every row and starts a new conversation under {@code newSessionId}. */
  public void clear(@NonNull String newSessionId) {
    items.clear();
    sessionId = Objects.requireNonNull(newSessionId, "newSessionId");
  }

  /** Appends the user's message and returns its row id. */
  public long addUser(@NonNull String text) {
    long id = nextId++;
    items.add(new ChatItem.UserMessage(id, clip(text)));
    trimToLimit();
    return id;
  }

  /**
   * Removes the last user message and everything after it so it can be sent again.
   *
   * @return the removed user text, or {@code null} when there is no user message
   */
  @Nullable
  public String rewindToLastUser() {
    for (int i = items.size() - 1; i >= 0; i--) {
      if (items.get(i) instanceof ChatItem.UserMessage user) {
        String text = user.text();
        while (items.size() > i) {
          items.remove(items.size() - 1);
        }
        return text;
      }
    }
    return null;
  }

  /** Applies a batch of events from the run, in order. */
  public void applyEvents(@NonNull List<VibeEvent> events) {
    for (VibeEvent event : events) {
      apply(event);
    }
    trimToLimit();
  }

  /**
   * Closes the run's open rows and reports a failure row for outcomes that carry no terminal
   * event. Call it from {@code HermesRunSession.Listener.onFinished}.
   */
  public void finish(@NonNull HermesRunSession.Outcome outcome, @Nullable HermesApiException cause) {
    finalizeOpenItems();
    if (outcome == HermesRunSession.Outcome.START_FAILED
        || outcome == HermesRunSession.Outcome.CONNECTION_LOST) {
      ChatItem.FailureKind kind =
          outcome == HermesRunSession.Outcome.START_FAILED
              ? ChatItem.FailureKind.START_FAILED
              : ChatItem.FailureKind.CONNECTION_LOST;
      String apiKind = cause == null ? "" : cause.getKind().name();
      String detail = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
      items.add(new ChatItem.Failure(nextId++, kind, apiKind, clip(detail)));
      trimToLimit();
    }
  }

  /**
   * Settles whatever the run left open (streaming text, running tools, unanswered approvals) after
   * the run was lost or abandoned, and optionally appends a notice row.
   */
  public void abandonRun(@Nullable ChatItem.NoticeKind notice) {
    finalizeOpenItems();
    if (notice != null) {
      items.add(new ChatItem.Notice(nextId++, notice, ""));
      trimToLimit();
    }
  }

  /** Changes the status of an approval row; returns false when the row does not exist. */
  public boolean setApprovalStatus(
      long id, @NonNull ChatItem.ApprovalStatus status, @NonNull String choice) {
    int index = indexOfId(id);
    if (index < 0 || !(items.get(index) instanceof ChatItem.Approval approval)) {
      return false;
    }
    items.set(index, withStatus(approval, status, choice));
    return true;
  }

  /** The approval row with {@code id}, or {@code null}. */
  @Nullable
  public ChatItem.Approval findApproval(long id) {
    int index = indexOfId(id);
    return index >= 0 && items.get(index) instanceof ChatItem.Approval approval ? approval : null;
  }

  /** True if some approval is still waiting for the user. */
  public boolean hasOpenApproval() {
    for (ChatItem item : items) {
      if (item instanceof ChatItem.Approval approval && approval.isOpen()) {
        return true;
      }
    }
    return false;
  }

  /**
   * The conversation so far as turns for the agent, oldest first: each user message followed by
   * the last non-blank assistant text before the next user message. Consecutive user messages
   * (a failed turn) are merged so roles always alternate; the result starts with a user turn and
   * is cut from the front to at most {@link #MAX_HISTORY_CHARS} characters.
   */
  @NonNull
  public List<VibeTurn> history() {
    List<VibeTurn> turns = new ArrayList<>();
    String assistantText = null;
    boolean sawUser = false;
    for (ChatItem item : items) {
      if (item instanceof ChatItem.UserMessage user) {
        if (sawUser) {
          addAssistantTurn(turns, assistantText);
        }
        assistantText = null;
        sawUser = true;
        addUserTurn(turns, user.text());
      } else if (item instanceof ChatItem.AssistantMessage assistant
          && !assistant.text().isBlank()) {
        assistantText = assistant.text();
      }
    }
    if (sawUser) {
      addAssistantTurn(turns, assistantText);
    }
    return cutToBudget(turns);
  }

  private static void addUserTurn(List<VibeTurn> turns, String text) {
    int last = turns.size() - 1;
    if (last >= 0 && turns.get(last).getRole() == VibeTurn.Role.USER) {
      String merged = turns.get(last).getText() + "\n\n" + text;
      turns.set(last, new VibeTurn(VibeTurn.Role.USER, merged));
    } else {
      turns.add(new VibeTurn(VibeTurn.Role.USER, text));
    }
  }

  private static void addAssistantTurn(List<VibeTurn> turns, @Nullable String text) {
    if (text != null && !turns.isEmpty()) {
      turns.add(new VibeTurn(VibeTurn.Role.ASSISTANT, text));
    }
  }

  private static List<VibeTurn> cutToBudget(List<VibeTurn> turns) {
    long total = 0;
    int start = turns.size();
    while (start > 0) {
      int length = turns.get(start - 1).getText().length();
      if (total + length > MAX_HISTORY_CHARS && start < turns.size()) {
        break;
      }
      total += length;
      start--;
    }
    while (start < turns.size() && turns.get(start).getRole() != VibeTurn.Role.USER) {
      start++;
    }
    return List.copyOf(turns.subList(start, turns.size()));
  }

  private void apply(VibeEvent event) {
    if (event instanceof VibeEvent.MessageDelta delta) {
      appendDelta(delta.text());
    } else if (event instanceof VibeEvent.MessageInterim interim) {
      if (!interim.alreadyStreamed() && !interim.text().isBlank()) {
        finishStreaming();
        items.add(new ChatItem.AssistantMessage(nextId++, clip(interim.text()), false));
      }
    } else if (event instanceof VibeEvent.ToolStarted started) {
      toolStarted(started);
    } else if (event instanceof VibeEvent.ToolCompleted completed) {
      toolCompleted(completed);
    } else if (event instanceof VibeEvent.ApprovalRequested request) {
      approvalRequested(request);
    } else if (event instanceof VibeEvent.ApprovalResolved resolved) {
      approvalResolved(resolved);
    } else if (event instanceof VibeEvent.RunSteered steered) {
      if (steered.accepted()) {
        addNotice(ChatItem.NoticeKind.STEERED, "");
      }
    } else if (event instanceof VibeEvent.Subagent subagent) {
      addNotice(
          "start".equals(subagent.phase())
              ? ChatItem.NoticeKind.SUBAGENT_STARTED
              : ChatItem.NoticeKind.SUBAGENT_FINISHED,
          subagent.preview());
    } else if (event instanceof VibeEvent.RunQueued) {
      addNotice(ChatItem.NoticeKind.QUEUED, "");
    } else if (event instanceof VibeEvent.ReplayTruncated) {
      addNotice(ChatItem.NoticeKind.REPLAY_TRUNCATED, "");
    } else if (event instanceof VibeEvent.RunCompleted completed) {
      runCompleted(completed);
    } else if (event instanceof VibeEvent.RunFailed failed) {
      finalizeOpenItems();
      items.add(new ChatItem.Failure(nextId++, ChatItem.FailureKind.RUN_FAILED, "", clip(failed.error())));
    } else if (event instanceof VibeEvent.RunCancelled) {
      finalizeOpenItems();
      addNotice(ChatItem.NoticeKind.STOPPED, "");
    } else if (event instanceof VibeEvent.RunInterrupted interrupted) {
      finalizeOpenItems();
      addNotice(ChatItem.NoticeKind.INTERRUPTED, interrupted.reason());
    }
  }

  private void appendDelta(String text) {
    if (text.isEmpty()) {
      return;
    }
    int last = items.size() - 1;
    if (last >= 0
        && items.get(last) instanceof ChatItem.AssistantMessage open
        && open.streaming()) {
      if (open.text().length() < MAX_TEXT_CHARS) {
        items.set(
            last, new ChatItem.AssistantMessage(open.id(), clip(open.text() + text), true));
      }
    } else {
      items.add(new ChatItem.AssistantMessage(nextId++, clip(text), true));
    }
  }

  private void toolStarted(VibeEvent.ToolStarted started) {
    finishStreaming();
    int existing = indexOfTool(started.toolCallId(), started.tool(), false);
    if (existing >= 0 && !started.toolCallId().isEmpty()) {
      return;
    }
    items.add(
        new ChatItem.ToolCall(
            nextId++,
            started.toolCallId(),
            started.tool(),
            clip(started.preview()),
            ChatItem.ToolState.RUNNING,
            0));
  }

  private void toolCompleted(VibeEvent.ToolCompleted completed) {
    finishStreaming();
    ChatItem.ToolState state =
        completed.error() ? ChatItem.ToolState.FAILED : ChatItem.ToolState.DONE;
    int index = indexOfTool(completed.toolCallId(), completed.tool(), true);
    if (index < 0) {
      items.add(
          new ChatItem.ToolCall(
              nextId++,
              completed.toolCallId(),
              completed.tool(),
              clip(completed.preview()),
              state,
              completed.durationSeconds()));
      return;
    }
    ChatItem.ToolCall call = (ChatItem.ToolCall) items.get(index);
    String preview = completed.preview().isEmpty() ? call.preview() : clip(completed.preview());
    items.set(
        index,
        new ChatItem.ToolCall(
            call.id(), call.callId(), call.tool(), preview, state, completed.durationSeconds()));
  }

  private int indexOfTool(String callId, String tool, boolean runningOnly) {
    for (int i = items.size() - 1; i >= 0; i--) {
      if (!(items.get(i) instanceof ChatItem.ToolCall call)) {
        continue;
      }
      if (!callId.isEmpty()) {
        if (callId.equals(call.callId())) {
          return i;
        }
      } else if (call.callId().isEmpty()
          && call.tool().equals(tool)
          && (!runningOnly || call.state() == ChatItem.ToolState.RUNNING)) {
        return i;
      }
    }
    return -1;
  }

  private void approvalRequested(VibeEvent.ApprovalRequested request) {
    finishStreaming();
    for (ChatItem item : items) {
      if (item instanceof ChatItem.Approval approval
          && approval.isOpen()
          && !request.promptId().isEmpty()
          && request.promptId().equals(approval.promptId())) {
        return;
      }
    }
    items.add(
        new ChatItem.Approval(
            nextId++,
            request,
            request.promptId(),
            clip(request.command()),
            clip(request.description()),
            List.copyOf(request.choices()),
            ChatItem.ApprovalStatus.PENDING,
            ""));
  }

  private void approvalResolved(VibeEvent.ApprovalResolved resolved) {
    int target = -1;
    for (int i = items.size() - 1; i >= 0; i--) {
      if (items.get(i) instanceof ChatItem.Approval approval && approval.isOpen()) {
        if (!resolved.promptId().isEmpty() && resolved.promptId().equals(approval.promptId())) {
          target = i;
          break;
        }
        if (target < 0 && (resolved.promptId().isEmpty() || approval.promptId().isEmpty())) {
          target = i;
        }
      }
    }
    if (target < 0) {
      return;
    }
    ChatItem.Approval approval = (ChatItem.Approval) items.get(target);
    String choice = resolved.choice();
    ChatItem.ApprovalStatus status;
    if (approval.status() == ChatItem.ApprovalStatus.SUBMITTING && !choice.isEmpty()) {
      status = "deny".equals(choice) ? ChatItem.ApprovalStatus.DENIED : ChatItem.ApprovalStatus.ALLOWED;
    } else {
      status = ChatItem.ApprovalStatus.RESOLVED_ELSEWHERE;
    }
    items.set(target, withStatus(approval, status, choice));
  }

  private void runCompleted(VibeEvent.RunCompleted completed) {
    String output = clip(completed.output());
    int last = items.size() - 1;
    boolean openTail =
        last >= 0
            && items.get(last) instanceof ChatItem.AssistantMessage tail
            && tail.streaming();
    if (output.isBlank()) {
      finishStreaming();
    } else if (openTail) {
      long id = items.get(last).id();
      items.set(last, new ChatItem.AssistantMessage(id, output, false));
    } else if (!(last >= 0
        && items.get(last) instanceof ChatItem.AssistantMessage tail
        && tail.text().equals(output))) {
      items.add(new ChatItem.AssistantMessage(nextId++, output, false));
    }
    finalizeOpenItems();
    VibeUsage usage = completed.usage();
    if (usage != null
        && (usage.getInputTokens() > 0 || usage.getOutputTokens() > 0 || usage.getTotalTokens() > 0)) {
      items.add(
          new ChatItem.Usage(
              nextId++,
              usage.getInputTokens(),
              usage.getOutputTokens(),
              usage.getTotalTokens()));
    }
  }

  private void addNotice(ChatItem.NoticeKind kind, String text) {
    items.add(new ChatItem.Notice(nextId++, kind, clip(text)));
  }

  private void finishStreaming() {
    int last = items.size() - 1;
    if (last >= 0
        && items.get(last) instanceof ChatItem.AssistantMessage open
        && open.streaming()) {
      items.set(last, new ChatItem.AssistantMessage(open.id(), open.text(), false));
    }
  }

  private void finalizeOpenItems() {
    for (int i = 0; i < items.size(); i++) {
      ChatItem item = items.get(i);
      if (item instanceof ChatItem.AssistantMessage message && message.streaming()) {
        items.set(i, new ChatItem.AssistantMessage(message.id(), message.text(), false));
      } else if (item instanceof ChatItem.ToolCall call
          && call.state() == ChatItem.ToolState.RUNNING) {
        items.set(
            i,
            new ChatItem.ToolCall(
                call.id(),
                call.callId(),
                call.tool(),
                call.preview(),
                ChatItem.ToolState.UNKNOWN,
                call.seconds()));
      } else if (item instanceof ChatItem.Approval approval && approval.isOpen()) {
        items.set(i, withStatus(approval, settledStatus(approval), approval.choice()));
      }
    }
  }

  private static ChatItem.ApprovalStatus settledStatus(ChatItem.Approval approval) {
    if (approval.status() != ChatItem.ApprovalStatus.SUBMITTING) {
      return ChatItem.ApprovalStatus.EXPIRED;
    }
    return "deny".equals(approval.choice())
        ? ChatItem.ApprovalStatus.DENIED
        : ChatItem.ApprovalStatus.ALLOWED;
  }

  private static ChatItem.Approval withStatus(
      ChatItem.Approval approval, ChatItem.ApprovalStatus status, String choice) {
    return new ChatItem.Approval(
        approval.id(),
        approval.request(),
        approval.promptId(),
        approval.command(),
        approval.description(),
        approval.choices(),
        status,
        choice);
  }

  private int indexOfId(long id) {
    for (int i = items.size() - 1; i >= 0; i--) {
      if (items.get(i).id() == id) {
        return i;
      }
    }
    return -1;
  }

  private void trimToLimit() {
    int excess = items.size() - MAX_ITEMS;
    if (excess > 0) {
      items.subList(0, excess).clear();
    }
  }

  /** Cuts {@code text} to {@link #MAX_TEXT_CHARS}, marking the cut. */
  @NonNull
  static String clip(@Nullable String text) {
    if (text == null) {
      return "";
    }
    if (text.length() <= MAX_TEXT_CHARS) {
      return text;
    }
    return text.substring(0, MAX_TEXT_CHARS) + ELLIPSIS;
  }
}
