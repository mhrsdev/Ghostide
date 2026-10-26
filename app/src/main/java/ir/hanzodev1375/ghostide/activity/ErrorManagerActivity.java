package ir.hanzodev1375.ghostide.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.ClipboardUtils;
import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.ThreadUtils;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.databinding.ErrormanagerBinding;
import ir.hanzodev1375.ghostide.utils.InfoRows;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.utils.StackTraceInfo;
import ir.theme.M3Theme;
import ir.theme.ThemeUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Crash report screen started by the app's default uncaught-exception handler with the stack trace
 * in the {@code "error"} extra. Shows the exception first, then the trace with line numbers, then
 * app/device/runtime facts; the plain-text report can be copied (also saved to {@link #LOG_PATH})
 * or shared.
 */
public class ErrorManagerActivity extends BaseCompat {

  private static final String TAG = "ErrorManagerActivity";
  private static final String EXTRA_ERROR = "error";
  private static final String LOG_PATH = "/sdcard/Ghostide/error.log";
  private static final String RULE = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n";
  private static final int MAX_TRACE_LINES = 300;
  private static final float TRACE_LINE_HEIGHT_SP = 18f;
  private static final long BYTES_PER_MB = 1024L * 1024L;

  private static final Map<String, Integer> EXCEPTION_MEANINGS = new HashMap<>();

  static {
    EXCEPTION_MEANINGS.put("StringIndexOutOfBoundsException", R.string.crash_meaning_string_index);
    EXCEPTION_MEANINGS.put("IndexOutOfBoundsException", R.string.crash_meaning_index);
    EXCEPTION_MEANINGS.put("ArrayIndexOutOfBoundsException", R.string.crash_meaning_index);
    EXCEPTION_MEANINGS.put("ArithmeticException", R.string.crash_meaning_arithmetic);
    EXCEPTION_MEANINGS.put("NumberFormatException", R.string.crash_meaning_number_format);
    EXCEPTION_MEANINGS.put("ActivityNotFoundException", R.string.crash_meaning_activity_not_found);
    EXCEPTION_MEANINGS.put("NullPointerException", R.string.crash_meaning_null_pointer);
    EXCEPTION_MEANINGS.put("ClassCastException", R.string.crash_meaning_class_cast);
    EXCEPTION_MEANINGS.put("OutOfMemoryError", R.string.crash_meaning_out_of_memory);
    EXCEPTION_MEANINGS.put("StackOverflowError", R.string.crash_meaning_stack_overflow);
  }

  private ErrormanagerBinding bind;
  private StackTraceInfo trace;
  private String report = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    bind = ErrormanagerBinding.inflate(getLayoutInflater());
    setContentView(bind.getRoot());
    setupBackgroundBlur(bind.backgroundIconError, bind.appbar, bind.toolbar, bind.errorContent);
    applyWindowInsets();
    setSupportActionBar(bind.toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle(R.string.crash_title);
    }

    Intent intent = getIntent();
    String error = intent != null ? intent.getStringExtra(EXTRA_ERROR) : null;
    trace = StackTraceInfo.parse(error, ownPackagePrefix());
    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

    alignToReadingSide();
    bindHeadline();
    bindFacts(time);
    LocaleDirection.mirrorRow(bind.actionBar);
    report = buildReport(time);

    bind.btnCopyReport.setOnClickListener(v -> copyReport());
    bind.btnShareReport.setOnClickListener(v -> shareReport());

    M3Theme.apply(bind.getRoot());
    applyAccents();
  }

  @Override
  protected void applyOwnTheme(ThemeUtils themeUtils) {
    if (bind != null) {
      applyAccents();
    }
  }

  /**
   * Applies the system bar and display cutout insets once, at the root: the app bar background runs
   * under the status bar while its toolbar sits below it, the report clears the side bars, and the
   * copy/share row clears the navigation bar. The insets are consumed so no child applies them a
   * second time.
   */
  private void applyWindowInsets() {
    View actions = bind.actionBar;
    int actionsLeft = actions.getPaddingLeft();
    int actionsRight = actions.getPaddingRight();
    int actionsBottom = actions.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(
        bind.getRoot(),
        (v, insets) -> {
          Insets safe =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          bind.appbar.setPadding(safe.left, safe.top, safe.right, 0);
          bind.crashScroll.setPadding(safe.left, 0, safe.right, 0);
          actions.setPadding(
              actionsLeft + safe.left,
              actions.getPaddingTop(),
              actionsRight + safe.right,
              actionsBottom + safe.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
  }

  private String ownPackagePrefix() {
    String pkg = getPackageName();
    int first = pkg.indexOf('.');
    int second = first < 0 ? -1 : pkg.indexOf('.', first + 1);
    return (second > 0 ? pkg.substring(0, second) : pkg) + ".";
  }

  /**
   * The app runs with RTL support off, so Persian text views would keep a left gravity for Latin
   * content (class names, file positions). Pins the header column to the reading side instead.
   */
  private void alignToReadingSide() {
    int gravity = LocaleDirection.startGravity(this);
    TextView[] column = {
      bind.tvExceptionName,
      bind.tvExceptionPackage,
      bind.tvExceptionMessage,
      bind.tvExceptionMeaning,
      bind.tvOriginLabel,
      bind.tvOriginFile,
      bind.tvOriginMethod,
      bind.tvRootCauseLabel,
      bind.tvRootCause,
      bind.tvTraceTitle,
      bind.tvTraceTruncated,
      bind.tvSectionApp,
      bind.tvSectionDevice,
      bind.tvSectionRuntime
    };
    for (TextView view : column) {
      view.setGravity(gravity);
    }
  }

  private void bindHeadline() {
    if (trace.isEmpty()) {
      bind.tvExceptionName.setText(R.string.crash_no_trace_title);
      bind.tvExceptionName.setTypeface(Typeface.DEFAULT_BOLD);
      bind.tvExceptionPackage.setVisibility(View.GONE);
      bind.tvExceptionMessage.setText(R.string.crash_no_trace_desc);
      bind.tvExceptionMeaning.setVisibility(View.GONE);
      bind.originBlock.setVisibility(View.GONE);
      bind.rootCauseBlock.setVisibility(View.GONE);
      bind.tvTraceTitle.setVisibility(View.GONE);
      bind.cardTrace.setVisibility(View.GONE);
      return;
    }
    String qualified = trace.getExceptionName();
    String simple = StackTraceInfo.simpleName(qualified);
    bind.tvExceptionName.setText(simple);
    String pkg =
        qualified.length() > simple.length()
            ? qualified.substring(0, qualified.length() - simple.length() - 1)
            : "";
    showOrHide(bind.tvExceptionPackage, pkg);
    showOrHide(bind.tvExceptionMessage, trace.getMessage());

    Integer meaning = EXCEPTION_MEANINGS.get(simple);
    showOrHide(bind.tvExceptionMeaning, meaning == null ? "" : getString(meaning));

    if (trace.getOriginFile() != null) {
      bind.tvOriginFile.setText(trace.getOriginFile());
      bind.tvOriginMethod.setText(trace.getOriginMethod() + "()");
    } else {
      bind.originBlock.setVisibility(View.GONE);
    }

    String rootCause = trace.getRootCauseName();
    if (rootCause != null) {
      bind.tvRootCause.setText(StackTraceInfo.simpleName(rootCause));
    } else {
      bind.rootCauseBlock.setVisibility(View.GONE);
    }

    int total = trace.getLines().size();
    int shown = Math.min(total, MAX_TRACE_LINES);
    StringBuilder gutter = new StringBuilder();
    for (int i = 1; i <= shown; i++) {
      if (i > 1) gutter.append('\n');
      gutter.append(i);
    }
    bind.tvTraceGutter.setText(gutter);
    int lineHeight =
        Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                TRACE_LINE_HEIGHT_SP,
                getResources().getDisplayMetrics()));
    TextViewCompat.setLineHeight(bind.tvTraceGutter, lineHeight);
    TextViewCompat.setLineHeight(bind.tvTrace, lineHeight);
    if (total > shown) {
      bind.tvTraceTruncated.setText(getString(R.string.crash_trace_truncated, total - shown));
      bind.tvTraceTruncated.setVisibility(View.VISIBLE);
    }
  }

  private static void showOrHide(TextView view, @Nullable CharSequence text) {
    if (text == null || text.length() == 0) {
      view.setVisibility(View.GONE);
    } else {
      view.setText(text);
      view.setVisibility(View.VISIBLE);
    }
  }

  private void bindFacts(String time) {
    InfoRows.add(
        bind.factsApp,
        R.string.info_version,
        AppUtils.getAppVersionName() + " (" + AppUtils.getAppVersionCode() + ")");
    InfoRows.add(bind.factsApp, R.string.info_package, getPackageName());
    InfoRows.add(bind.factsApp, R.string.info_build_type, AppUtils.isAppDebug() ? "debug" : "release");
    InfoRows.add(bind.factsApp, R.string.info_crashed_at, time);

    InfoRows.add(bind.factsDevice, R.string.info_manufacturer, Build.MANUFACTURER);
    InfoRows.add(bind.factsDevice, R.string.info_model, Build.MODEL);
    InfoRows.add(
        bind.factsDevice,
        R.string.info_android,
        Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
    InfoRows.add(bind.factsDevice, R.string.info_abi, primaryAbi());
    InfoRows.add(bind.factsDevice, R.string.info_fingerprint, Build.FINGERPRINT);

    Runtime rt = Runtime.getRuntime();
    long used = (rt.totalMemory() - rt.freeMemory()) / BYTES_PER_MB;
    InfoRows.add(bind.factsRuntime, R.string.info_heap_used, used + " MB");
    InfoRows.add(bind.factsRuntime, R.string.info_heap_max, rt.maxMemory() / BYTES_PER_MB + " MB");
    InfoRows.add(
        bind.factsRuntime, R.string.info_cpu_cores, String.valueOf(rt.availableProcessors()));
  }

  private static String primaryAbi() {
    return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
  }

  /** Colours that M3Theme's generic pass would flatten to onSurface; re-run after each theme pass. */
  private void applyAccents() {
    int onSurface = orDefault(M3Theme.onSurface(), 0);
    int muted = orDefault(M3Theme.onSurfaceVariant(), onSurface);
    int error = orDefault(M3Theme.error(), onSurface);
    int primary = orDefault(M3Theme.primary(), onSurface);
    if (!trace.isEmpty()) {
      bind.tvExceptionName.setTextColor(error);
    }
    bind.tvOriginFile.setTextColor(primary);
    bind.tvRootCause.setTextColor(error);
    Integer outline = M3Theme.outlineVariant();
    if (outline != null) {
      bind.actionDivider.setBackgroundColor(outline);
    }
    if (!trace.isEmpty()) {
      bind.tvTrace.setText(styledTrace(onSurface, muted, error, primary));
    }
  }

  private CharSequence styledTrace(int onSurface, int muted, int error, int primary) {
    List<String> lines = trace.getLines();
    List<StackTraceInfo.LineKind> kinds = trace.getKinds();
    int shown = Math.min(lines.size(), MAX_TRACE_LINES);
    SpannableStringBuilder out = new SpannableStringBuilder();
    for (int i = 0; i < shown; i++) {
      if (i > 0) out.append('\n');
      int start = out.length();
      out.append(lines.get(i));
      int end = out.length();
      int color;
      boolean bold = false;
      switch (kinds.get(i)) {
        case HEADER:
          color = onSurface;
          bold = true;
          break;
        case CAUSE:
          color = error;
          bold = true;
          break;
        case OWN_FRAME:
          color = primary;
          break;
        case MESSAGE:
          color = onSurface;
          break;
        default:
          color = muted;
          break;
      }
      out.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      if (bold) {
        out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return out;
  }

  private static int orDefault(@Nullable Integer value, int fallback) {
    return value != null ? value : fallback;
  }

  private String buildReport(String time) {
    StringBuilder sb = new StringBuilder();
    sb.append(RULE).append("  GHOST IDE — CRASH REPORT\n").append(RULE);

    sb.append("\n📦 APP INFO\n");
    line(sb, "App Name", AppUtils.getAppName());
    line(sb, "Version", AppUtils.getAppVersionName() + " (" + AppUtils.getAppVersionCode() + ")");
    line(sb, "Package", getPackageName());
    line(sb, "Debug Build", AppUtils.isAppDebug() ? "Yes" : "No");

    sb.append("\n📱 DEVICE INFO\n");
    line(sb, "Manufacturer", Build.MANUFACTURER);
    line(sb, "Model", Build.MODEL);
    line(sb, "Android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
    line(sb, "ABI", primaryAbi());
    line(sb, "Fingerprint", Build.FINGERPRINT);

    Runtime rt = Runtime.getRuntime();
    sb.append("\n⚙️ RUNTIME\n");
    line(sb, "Memory Used", (rt.totalMemory() - rt.freeMemory()) / BYTES_PER_MB + " MB");
    line(sb, "Memory Total", rt.totalMemory() / BYTES_PER_MB + " MB");
    line(sb, "Memory Max", rt.maxMemory() / BYTES_PER_MB + " MB");
    line(sb, "CPU Cores", String.valueOf(rt.availableProcessors()));

    sb.append("\n🕒 TIMESTAMP\n");
    line(sb, "Crashed At", time);

    sb.append("\n🔴 ERROR DETAILS\n");
    if (trace.isEmpty()) {
      sb.append("No error message available.\n");
    } else {
      line(sb, "Exception", trace.getExceptionName());
      Integer meaning = EXCEPTION_MEANINGS.get(StackTraceInfo.simpleName(trace.getExceptionName()));
      line(sb, "Meaning", meaning != null ? getString(meaning) : "Unknown error");
      sb.append("\n📋 Stack Trace:\n");
      for (String traceLine : trace.getLines()) {
        sb.append("  ").append(traceLine).append('\n');
      }
    }
    sb.append('\n').append(RULE);
    return sb.toString();
  }

  private static void line(StringBuilder sb, String key, String value) {
    sb.append(key).append(": ").append(value).append('\n');
  }

  private void copyReport() {
    ClipboardUtils.copyText(report);
    GhostToast.makeText(this, getString(R.string.msg_copied), GhostToast.LENGTH_SHORT).show();
    final String text = report;
    ThreadUtils.getIoPool()
        .execute(
            () -> {
              if (!FileIOUtils.writeFileFromString(LOG_PATH, text)) {
                Log.w(TAG, "Could not write " + LOG_PATH);
              }
            });
  }

  private void shareReport() {
    Intent send = new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_share_subject));
    send.putExtra(Intent.EXTRA_TEXT, report);
    try {
      startActivity(Intent.createChooser(send, getString(R.string.crash_share_report)));
    } catch (ActivityNotFoundException e) {
      GhostToast.makeText(
              this, getString(R.string.crash_share_unavailable), GhostToast.LENGTH_SHORT)
          .show();
    }
  }
}
