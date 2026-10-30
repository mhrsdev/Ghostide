package ir.hanzodev1375.ghostide.vibe.ui.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.vibe.ui.VibePalette;
import ir.hanzodev1375.ghostide.vibe.ui.VibeStyle;
import java.util.List;

/**
 * Fills the container of one assistant row with views for the Markdown blocks of its text.
 *
 * <p>Streaming updates arrive up to about 20 times per second, so existing child views are reused
 * when the block types still match: only the text is replaced. Paragraphs, headings and list entries
 * are {@link TextView}s; code blocks are a monospace, left-to-right view inside a horizontal
 * scroller. Nothing in the text can create a view of another kind or a clickable span.
 */
final class AssistantBlocks {

  /** Receives the user's request to copy text. */
  interface CopyListener {
    void onCopy(@NonNull CharSequence text, boolean code);
  }

  private static final int TYPE_TEXT = 1;
  private static final int TYPE_CODE = 2;
  private static final float HEADING_SIZE_SP = 18f;
  private static final float BODY_SIZE_SP = 16f;
  private static final float CODE_SCALE = 0.9f;
  private static final float LINE_SPACING_DP = 3f;
  private static final float BLOCK_GAP_DP = 8f;
  private static final float LIST_INDENT_DP = 22f;
  private static final float CODE_RADIUS_DP = 12f;

  private final LinearLayout container;
  private final CopyListener copyListener;
  private final boolean rtl;
  private String fullText = "";

  AssistantBlocks(@NonNull LinearLayout container, @NonNull CopyListener copyListener) {
    this.container = container;
    this.copyListener = copyListener;
    this.rtl = LocaleDirection.isRtl(container.getContext());
  }

  void bind(@NonNull String text, @NonNull VibePalette palette) {
    fullText = text;
    List<MarkdownLite.Block> blocks = MarkdownLite.parse(text);
    for (int i = 0; i < blocks.size(); i++) {
      MarkdownLite.Block block = blocks.get(i);
      boolean code = block instanceof MarkdownLite.Block.Code;
      View existing = i < container.getChildCount() ? container.getChildAt(i) : null;
      int wanted = code ? TYPE_CODE : TYPE_TEXT;
      if (existing == null || !Integer.valueOf(wanted).equals(existing.getTag())) {
        View created = code ? createCode() : createText();
        if (existing != null) {
          container.removeViewAt(i);
        }
        container.addView(created, i);
        existing = created;
      }
      if (code) {
        bindCode(existing, (MarkdownLite.Block.Code) block, palette);
      } else {
        bindText((TextView) existing, block, palette, i == 0);
      }
    }
    while (container.getChildCount() > blocks.size()) {
      container.removeViewAt(container.getChildCount() - 1);
    }
  }

  private View createText() {
    Context context = container.getContext();
    TextView view = new TextView(context);
    view.setTag(TYPE_TEXT);
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SIZE_SP);
    view.setLineSpacing(VibePalette.dp(context, LINE_SPACING_DP), 1f);
    view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
    view.setOnLongClickListener(
        v -> {
          copyListener.onCopy(fullText, false);
          return true;
        });
    view.setLayoutParams(blockParams(context));
    return view;
  }

  private View createCode() {
    Context context = container.getContext();
    View view =
        LayoutInflater.from(context).inflate(R.layout.view_vibe_code_block, container, false);
    view.setTag(TYPE_CODE);
    ImageButton copy = view.findViewById(R.id.codeCopy);
    TextView codeText = view.findViewById(R.id.codeText);
    copy.setOnClickListener(v -> copyListener.onCopy(codeText.getText(), true));
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
    params.topMargin = VibePalette.dp(context, BLOCK_GAP_DP);
    params.bottomMargin = VibePalette.dp(context, BLOCK_GAP_DP / 2);
    return view;
  }

  private static LinearLayout.LayoutParams blockParams(Context context) {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = VibePalette.dp(context, BLOCK_GAP_DP / 2);
    return params;
  }

  private void bindText(
      TextView view, MarkdownLite.Block block, VibePalette palette, boolean first) {
    view.setTextColor(palette.onSurface);
    view.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
    params.topMargin = first ? 0 : VibePalette.dp(view.getContext(), BLOCK_GAP_DP / 2);
    if (block instanceof MarkdownLite.Block.Heading heading) {
      SpannableStringBuilder styled = styled(heading.text(), palette);
      styled.setSpan(new StyleSpan(Typeface.BOLD), 0, styled.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, heading.level() <= 2 ? HEADING_SIZE_SP : BODY_SIZE_SP);
      view.setText(styled);
    } else if (block instanceof MarkdownLite.Block.ListItem item) {
      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SIZE_SP);
      SpannableStringBuilder styled = new SpannableStringBuilder(item.marker() + " ");
      styled.append(styled(item.text(), palette));
      int base = VibePalette.dp(view.getContext(), LIST_INDENT_DP);
      int margin = base * (item.depth() + 1);
      styled.setSpan(
          new LeadingMarginSpan.Standard(margin - base, margin),
          0,
          styled.length(),
          Spanned.SPAN_INCLUSIVE_INCLUSIVE);
      view.setText(styled);
    } else if (block instanceof MarkdownLite.Block.Paragraph paragraph) {
      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SIZE_SP);
      view.setText(styled(paragraph.text(), palette));
    }
  }

  private void bindCode(View view, MarkdownLite.Block.Code block, VibePalette palette) {
    Context context = view.getContext();
    view.setBackground(
        VibePalette.rounded(palette.containerHighest, VibePalette.dp(context, CODE_RADIUS_DP)));
    TextView language = view.findViewById(R.id.codeLanguage);
    TextView code = view.findViewById(R.id.codeText);
    ImageButton copy = view.findViewById(R.id.codeCopy);
    language.setText(block.language());
    language.setTextColor(palette.onSurfaceVariant);
    code.setText(block.code());
    code.setTextColor(palette.onSurface);
    VibeStyle.tint(copy, palette.onSurfaceVariant);
  }

  private static SpannableStringBuilder styled(String raw, VibePalette palette) {
    SpannableStringBuilder out = new SpannableStringBuilder();
    for (MarkdownLite.Run run : MarkdownLite.inline(raw)) {
      int start = out.length();
      out.append(run.text());
      int end = out.length();
      if ((run.styles() & MarkdownLite.BOLD) != 0) {
        out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if ((run.styles() & MarkdownLite.ITALIC) != 0) {
        out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if ((run.styles() & MarkdownLite.CODE) != 0) {
        out.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(CODE_SCALE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(
            new BackgroundColorSpan(palette.containerHighest),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return out;
  }
}
