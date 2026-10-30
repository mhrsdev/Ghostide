package ir.hanzodev1375.ghostide.vibe.ui.chat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.vibe.HermesApiClient;
import ir.hanzodev1375.ghostide.vibe.ui.VibePalette;
import ir.hanzodev1375.ghostide.vibe.ui.VibeStyle;
import ir.theme.M3Theme;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Draws the transcript. Rows have stable ids and are compared by content with {@link DiffUtil}, so
 * a streaming answer only rebinds its own row; change animations are switched off by the screen to
 * avoid flicker. No row contains a glass view or a blur: message lists use opaque tonal surfaces.
 */
public final class ChatAdapter extends ListAdapter<ChatItem, RecyclerView.ViewHolder> {

  /** What the rows can ask the screen to do. */
  public interface Actions {
    /** The user chose {@code decision} for approval row {@code itemId}. */
    void onApproval(long itemId, @NonNull HermesApiClient.Decision decision);

    /** The user wants the last prompt sent again. */
    void onRetry();

    /** The user wants {@code text} copied; {@code code} tells a code block from a message. */
    void onCopy(@NonNull CharSequence text, boolean code);
  }

  private static final int TYPE_USER = 1;
  private static final int TYPE_ASSISTANT = 2;
  private static final int TYPE_TOOL = 3;
  private static final int TYPE_APPROVAL = 4;
  private static final int TYPE_NOTICE = 5;
  private static final int TYPE_FAILURE = 6;
  private static final Object PAYLOAD_LOOK = new Object();
  private static final float USER_MAX_WIDTH_FRACTION = 0.85f;
  private static final float BUBBLE_RADIUS_DP = 20f;
  private static final float BUBBLE_TAIL_RADIUS_DP = 6f;
  private static final float CARD_RADIUS_DP = 16f;
  private static final float COMMAND_RADIUS_DP = 10f;
  private static final float OUTLINE_DP = 1.5f;
  private static final int EXPANDED_PREVIEW_LINES = 12;

  private static final DiffUtil.ItemCallback<ChatItem> DIFF =
      new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatItem a, @NonNull ChatItem b) {
          return a.id() == b.id() && a.getClass() == b.getClass();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatItem a, @NonNull ChatItem b) {
          return a.equals(b);
        }
      };

  private final Actions actions;
  private final boolean rtl;
  private final Set<Long> expanded = new HashSet<>();
  private VibePalette palette;
  private boolean retryEnabled = true;

  public ChatAdapter(@NonNull Context context, @NonNull Actions actions) {
    super(DIFF);
    this.actions = actions;
    this.rtl = LocaleDirection.isRtl(context);
    this.palette = VibePalette.read(context);
    setHasStableIds(true);
  }

  /** Applies a new palette to every row after a theme change. */
  public void setPalette(@NonNull VibePalette next) {
    palette = next;
    notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LOOK);
  }

  /** Enables or disables the retry button of the last failure row. */
  public void setRetryEnabled(boolean enabled) {
    if (retryEnabled == enabled) {
      return;
    }
    retryEnabled = enabled;
    notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LOOK);
  }

  @Override
  public long getItemId(int position) {
    return getItem(position).id();
  }

  @Override
  public int getItemViewType(int position) {
    ChatItem item = getItem(position);
    if (item instanceof ChatItem.UserMessage) {
      return TYPE_USER;
    } else if (item instanceof ChatItem.AssistantMessage) {
      return TYPE_ASSISTANT;
    } else if (item instanceof ChatItem.ToolCall) {
      return TYPE_TOOL;
    } else if (item instanceof ChatItem.Approval) {
      return TYPE_APPROVAL;
    } else if (item instanceof ChatItem.Failure) {
      return TYPE_FAILURE;
    }
    return TYPE_NOTICE;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    switch (viewType) {
      case TYPE_USER:
        return new UserHolder(inflate(inflater, R.layout.item_vibe_user, parent), parent);
      case TYPE_ASSISTANT:
        return new AssistantHolder(inflate(inflater, R.layout.item_vibe_assistant, parent));
      case TYPE_TOOL:
        return new ToolHolder(inflate(inflater, R.layout.item_vibe_tool, parent));
      case TYPE_APPROVAL:
        return new ApprovalHolder(inflate(inflater, R.layout.item_vibe_approval, parent));
      case TYPE_FAILURE:
        return new FailureHolder(inflate(inflater, R.layout.item_vibe_failure, parent));
      default:
        return new NoticeHolder(inflate(inflater, R.layout.item_vibe_notice, parent));
    }
  }

  private View inflate(LayoutInflater inflater, int layout, ViewGroup parent) {
    View view = inflater.inflate(layout, parent, false);
    M3Theme.listCard(view);
    return view;
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    ((Bindable) holder).bind(getItem(position), position);
  }

  private interface Bindable {
    void bind(ChatItem item, int position);
  }

  private final class UserHolder extends RecyclerView.ViewHolder implements Bindable {
    private final TextView text;
    private final GradientDrawable bubble = new GradientDrawable();
    private ChatItem.UserMessage current;

    UserHolder(View view, ViewGroup parent) {
      super(view);
      text = view.findViewById(R.id.userText);
      int width = parent.getWidth() > 0 ? parent.getWidth() : view.getResources().getDisplayMetrics().widthPixels;
      text.setMaxWidth(Math.round(width * USER_MAX_WIDTH_FRACTION));
      ((FrameLayout.LayoutParams) text.getLayoutParams()).gravity =
          rtl ? Gravity.LEFT : Gravity.RIGHT;
      float big = VibePalette.dp(view.getContext(), BUBBLE_RADIUS_DP);
      float tail = VibePalette.dp(view.getContext(), BUBBLE_TAIL_RADIUS_DP);
      bubble.setCornerRadii(
          rtl
              ? new float[] {big, big, big, big, big, big, tail, tail}
              : new float[] {big, big, big, big, tail, tail, big, big});
      text.setBackground(bubble);
      text.setOnLongClickListener(
          v -> {
            actions.onCopy(text.getText(), false);
            return true;
          });
    }

    @Override
    public void bind(ChatItem item, int position) {
      current = (ChatItem.UserMessage) item;
      bubble.setColor(palette.primaryContainer);
      text.setTextColor(palette.onPrimaryContainer);
      text.setText(current.text());
    }
  }

  private final class AssistantHolder extends RecyclerView.ViewHolder implements Bindable {
    private final AssistantBlocks blocks;

    AssistantHolder(View view) {
      super(view);
      blocks = new AssistantBlocks((LinearLayout) view, actions::onCopy);
    }

    @Override
    public void bind(ChatItem item, int position) {
      blocks.bind(((ChatItem.AssistantMessage) item).text(), palette);
    }
  }

  private final class ToolHolder extends RecyclerView.ViewHolder implements Bindable {
    private final View card;
    private final CircularProgressIndicator spinner;
    private final ImageView status;
    private final TextView name;
    private final TextView time;
    private final TextView preview;
    private final GradientDrawable background = new GradientDrawable();
    private ChatItem.ToolCall current;

    ToolHolder(View view) {
      super(view);
      card = view.findViewById(R.id.toolCard);
      spinner = view.findViewById(R.id.toolSpinner);
      status = view.findViewById(R.id.toolStatus);
      name = view.findViewById(R.id.toolName);
      time = view.findViewById(R.id.toolTime);
      preview = view.findViewById(R.id.toolPreview);
      background.setCornerRadius(VibePalette.dp(view.getContext(), CARD_RADIUS_DP));
      card.setBackground(background);
      LocaleDirection.mirrorRow((LinearLayout) card);
      LocaleDirection.mirrorRow((LinearLayout) view.findViewById(R.id.toolHeader));
      int gravity = rtl ? Gravity.RIGHT : Gravity.LEFT;
      name.setGravity(gravity);
      preview.setGravity(gravity);
      card.setOnClickListener(v -> toggle());
    }

    private void toggle() {
      if (current == null || current.preview().isEmpty()) {
        return;
      }
      if (!expanded.remove(current.id())) {
        expanded.add(current.id());
      }
      applyPreview();
    }

    private void applyPreview() {
      boolean open = current != null && expanded.contains(current.id());
      preview.setMaxLines(open ? EXPANDED_PREVIEW_LINES : 1);
    }

    @Override
    public void bind(ChatItem item, int position) {
      current = (ChatItem.ToolCall) item;
      Context context = card.getContext();
      background.setColor(palette.containerHigh);
      name.setText(current.tool());
      name.setTextColor(palette.onSurface);
      preview.setText(current.preview());
      preview.setTextColor(palette.onSurfaceVariant);
      preview.setVisibility(current.preview().isEmpty() ? View.GONE : View.VISIBLE);
      card.setClickable(!current.preview().isEmpty());
      applyPreview();
      boolean running = current.state() == ChatItem.ToolState.RUNNING;
      spinner.setVisibility(running ? View.VISIBLE : View.GONE);
      status.setVisibility(running ? View.GONE : View.VISIBLE);
      int stateLabel;
      switch (current.state()) {
        case DONE:
          status.setImageResource(R.drawable.ic_vibe_check);
          VibeStyle.tint(status, palette.primary);
          stateLabel = R.string.vibe_tool_done;
          break;
        case FAILED:
          status.setImageResource(R.drawable.ic_vibe_warning);
          VibeStyle.tint(status, palette.error);
          stateLabel = R.string.vibe_tool_failed;
          break;
        case UNKNOWN:
          status.setImageResource(R.drawable.ic_vibe_code);
          VibeStyle.tint(status, palette.onSurfaceVariant);
          stateLabel = R.string.vibe_tool_unknown;
          break;
        default:
          stateLabel = R.string.vibe_tool_running;
          spinner.setIndicatorColor(palette.primary);
          break;
      }
      boolean showTime = current.state() == ChatItem.ToolState.DONE || current.state() == ChatItem.ToolState.FAILED;
      time.setVisibility(showTime ? View.VISIBLE : View.GONE);
      time.setText(context.getString(R.string.vibe_tool_seconds, current.seconds()));
      time.setTextColor(palette.onSurfaceVariant);
      card.setContentDescription(
          context.getString(R.string.vibe_tool_cd, current.tool(), context.getString(stateLabel)));
    }
  }

  private final class ApprovalHolder extends RecyclerView.ViewHolder implements Bindable {
    private final View card;
    private final ImageView icon;
    private final TextView title;
    private final TextView description;
    private final View commandScroll;
    private final TextView command;
    private final View actionsRow;
    private final MaterialButton allow;
    private final MaterialButton deny;
    private final View moreRow;
    private final MaterialButton session;
    private final MaterialButton always;
    private final TextView result;
    private final GradientDrawable background = new GradientDrawable();
    private final GradientDrawable commandBackground = new GradientDrawable();
    private ChatItem.Approval current;

    ApprovalHolder(View view) {
      super(view);
      card = view.findViewById(R.id.approvalCard);
      icon = view.findViewById(R.id.approvalIcon);
      title = view.findViewById(R.id.approvalTitle);
      description = view.findViewById(R.id.approvalDescription);
      commandScroll = view.findViewById(R.id.approvalCommandScroll);
      command = view.findViewById(R.id.approvalCommand);
      actionsRow = view.findViewById(R.id.approvalActions);
      allow = view.findViewById(R.id.approvalAllow);
      deny = view.findViewById(R.id.approvalDeny);
      moreRow = view.findViewById(R.id.approvalMore);
      session = view.findViewById(R.id.approvalSession);
      always = view.findViewById(R.id.approvalAlways);
      result = view.findViewById(R.id.approvalResult);
      Context context = view.getContext();
      background.setCornerRadius(VibePalette.dp(context, CARD_RADIUS_DP));
      card.setBackground(background);
      commandBackground.setCornerRadius(VibePalette.dp(context, COMMAND_RADIUS_DP));
      commandScroll.setBackground(commandBackground);
      LocaleDirection.mirrorRow((LinearLayout) view.findViewById(R.id.approvalHeader));
      LocaleDirection.mirrorRow((LinearLayout) actionsRow);
      LocaleDirection.mirrorRow((LinearLayout) moreRow);
      int gravity = rtl ? Gravity.RIGHT : Gravity.LEFT;
      description.setGravity(gravity);
      result.setGravity(gravity);
      allow.setOnClickListener(v -> answer(HermesApiClient.Decision.ONCE));
      deny.setOnClickListener(v -> answer(HermesApiClient.Decision.DENY));
      session.setOnClickListener(v -> answer(HermesApiClient.Decision.SESSION));
      always.setOnClickListener(v -> answer(HermesApiClient.Decision.ALWAYS));
    }

    private void answer(HermesApiClient.Decision decision) {
      if (current != null && current.status() == ChatItem.ApprovalStatus.PENDING) {
        actions.onApproval(current.id(), decision);
      }
    }

    @Override
    public void bind(ChatItem item, int position) {
      current = (ChatItem.Approval) item;
      Context context = card.getContext();
      boolean pending = current.status() == ChatItem.ApprovalStatus.PENDING;
      boolean open = current.isOpen();
      background.setColor(palette.containerHigh);
      background.setStroke(
          open ? VibePalette.dp(context, OUTLINE_DP) : 0, open ? palette.primary : palette.containerHigh);
      commandBackground.setColor(palette.containerLowest);
      VibeStyle.tint(icon, open ? palette.primary : palette.onSurfaceVariant);
      title.setText(open ? R.string.vibe_approval_title : R.string.vibe_approval_title_settled);
      title.setTextColor(palette.onSurface);
      description.setText(current.description());
      description.setTextColor(palette.onSurface);
      description.setVisibility(current.description().isEmpty() ? View.GONE : View.VISIBLE);
      command.setText(current.command());
      command.setTextColor(palette.onSurface);
      actionsRow.setVisibility(open ? View.VISIBLE : View.GONE);
      VibeStyle.filled(allow, palette);
      VibeStyle.tonal(deny, palette);
      VibeStyle.text(session, palette);
      VibeStyle.text(always, palette);
      boolean offerSession = pending && offers(HermesApiClient.Decision.SESSION);
      boolean offerAlways = pending && offers(HermesApiClient.Decision.ALWAYS);
      moreRow.setVisibility(offerSession || offerAlways ? View.VISIBLE : View.GONE);
      session.setVisibility(offerSession ? View.VISIBLE : View.GONE);
      always.setVisibility(offerAlways ? View.VISIBLE : View.GONE);
      allow.setEnabled(pending);
      deny.setEnabled(pending);
      session.setEnabled(pending);
      always.setEnabled(pending);
      result.setVisibility(open && pending ? View.GONE : View.VISIBLE);
      result.setText(resultLabel(context));
      result.setTextColor(
          current.status() == ChatItem.ApprovalStatus.DENIED ? palette.error : palette.onSurfaceVariant);
    }

    private boolean offers(HermesApiClient.Decision decision) {
      return current.choices().contains(decision.wireValue());
    }

    private String resultLabel(Context context) {
      switch (current.status()) {
        case SUBMITTING:
          return context.getString(R.string.vibe_approval_sending);
        case ALLOWED:
          switch (current.choice()) {
            case "session":
              return context.getString(R.string.vibe_approval_result_session);
            case "always":
              return context.getString(R.string.vibe_approval_result_always);
            default:
              return context.getString(R.string.vibe_approval_result_once);
          }
        case DENIED:
          return context.getString(R.string.vibe_approval_result_denied);
        case RESOLVED_ELSEWHERE:
          return context.getString(R.string.vibe_approval_result_elsewhere);
        case EXPIRED:
          return context.getString(R.string.vibe_approval_result_expired);
        default:
          return "";
      }
    }
  }

  private final class NoticeHolder extends RecyclerView.ViewHolder implements Bindable {
    private final ImageView icon;
    private final TextView text;

    NoticeHolder(View view) {
      super(view);
      icon = view.findViewById(R.id.noticeIcon);
      text = view.findViewById(R.id.noticeText);
      LocaleDirection.mirrorRow((LinearLayout) view.findViewById(R.id.noticeRow));
      text.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
    }

    @Override
    public void bind(ChatItem item, int position) {
      Context context = text.getContext();
      VibeStyle.tint(icon, palette.onSurfaceVariant);
      text.setTextColor(palette.onSurface);
      if (item instanceof ChatItem.Usage usage) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.getDefault());
        text.setText(
            context.getString(
                R.string.vibe_usage,
                format.format(usage.inputTokens()),
                format.format(usage.outputTokens())));
        icon.setImageResource(R.drawable.ic_vibe_check);
        return;
      }
      ChatItem.Notice notice = (ChatItem.Notice) item;
      icon.setImageResource(R.drawable.ic_info);
      text.setText(noticeText(context, notice));
    }

    private String noticeText(Context context, ChatItem.Notice notice) {
      switch (notice.kind()) {
        case SUBAGENT_STARTED:
          return context.getString(R.string.vibe_notice_subagent_started, notice.text());
        case SUBAGENT_FINISHED:
          return context.getString(R.string.vibe_notice_subagent_finished, notice.text());
        case QUEUED:
          return context.getString(R.string.vibe_notice_queued);
        case STEERED:
          return context.getString(R.string.vibe_notice_steered);
        case REPLAY_TRUNCATED:
          return context.getString(R.string.vibe_notice_truncated);
        case STOPPED:
          return context.getString(R.string.vibe_notice_stopped);
        case INTERRUPTED:
          return context.getString(R.string.vibe_notice_interrupted);
        default:
          return context.getString(R.string.vibe_notice_abandoned);
      }
    }
  }

  private final class FailureHolder extends RecyclerView.ViewHolder implements Bindable {
    private final View card;
    private final ImageView icon;
    private final TextView title;
    private final TextView body;
    private final TextView detail;
    private final MaterialButton retry;
    private final GradientDrawable background = new GradientDrawable();

    FailureHolder(View view) {
      super(view);
      card = view.findViewById(R.id.failureCard);
      icon = view.findViewById(R.id.failureIcon);
      title = view.findViewById(R.id.failureTitle);
      body = view.findViewById(R.id.failureBody);
      detail = view.findViewById(R.id.failureDetail);
      retry = view.findViewById(R.id.failureRetry);
      background.setCornerRadius(VibePalette.dp(view.getContext(), CARD_RADIUS_DP));
      card.setBackground(background);
      LocaleDirection.mirrorRow((LinearLayout) view.findViewById(R.id.failureHeader));
      int gravity = rtl ? Gravity.RIGHT : Gravity.LEFT;
      body.setGravity(gravity);
      LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) retry.getLayoutParams();
      params.gravity = gravity;
      retry.setOnClickListener(v -> actions.onRetry());
    }

    @Override
    public void bind(ChatItem item, int position) {
      ChatItem.Failure failure = (ChatItem.Failure) item;
      Context context = card.getContext();
      background.setColor(palette.errorContainer);
      VibeStyle.tint(icon, palette.onErrorContainer);
      title.setText(titleFor(failure));
      title.setTextColor(palette.onErrorContainer);
      body.setText(bodyFor(failure));
      body.setTextColor(palette.onErrorContainer);
      detail.setText(failure.detail());
      detail.setTextColor(palette.onErrorContainer);
      detail.setVisibility(failure.detail().isEmpty() ? View.GONE : View.VISIBLE);
      boolean last = position == getItemCount() - 1;
      retry.setVisibility(last ? View.VISIBLE : View.GONE);
      retry.setEnabled(retryEnabled);
      VibeStyle.onError(retry, palette);
      retry.setContentDescription(context.getString(R.string.vibe_retry));
    }

    private int titleFor(ChatItem.Failure failure) {
      switch (failure.kind()) {
        case CONNECTION_LOST:
          return R.string.vibe_failure_connection_title;
        case START_FAILED:
          return R.string.vibe_failure_start_title;
        default:
          return R.string.vibe_failure_run_title;
      }
    }

    private int bodyFor(ChatItem.Failure failure) {
      switch (failure.kind()) {
        case CONNECTION_LOST:
          return R.string.vibe_failure_connection_body;
        case START_FAILED:
          return startBody(failure.apiKind());
        default:
          return R.string.vibe_failure_run_body;
      }
    }

    private int startBody(String apiKind) {
      switch (apiKind) {
        case "UNAUTHORIZED":
          return R.string.vibe_failure_unauthorized;
        case "RATE_LIMITED":
          return R.string.vibe_failure_rate_limited;
        case "UNAVAILABLE":
          return R.string.vibe_failure_unavailable;
        default:
          return R.string.vibe_failure_start_body;
      }
    }
  }

  @Override
  public void onBindViewHolder(
      @NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    onBindViewHolder(holder, position);
  }

  @Override
  public void onCurrentListChanged(
      @NonNull List<ChatItem> previousList, @NonNull List<ChatItem> currentList) {
    int before = previousList.size() - 1;
    if (before >= 0 && currentList.size() > previousList.size() && previousList.get(before) instanceof ChatItem.Failure) {
      notifyItemChanged(before, PAYLOAD_LOOK);
    }
  }
}
