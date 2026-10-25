package ir.hanzodev1375.ghostide.postman;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.text.PrecomputedTextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import ir.hanzodev1375.components.glass.Glass;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.activity.BaseCompat;
import ir.hanzodev1375.ghostide.activity.SettingActivity;
import ir.hanzodev1375.ghostide.databinding.ActivityPostmanBinding;
import ir.hanzodev1375.ghostide.postman.adapter.KeyValueAdapter;
import ir.hanzodev1375.ghostide.postman.data.AppRepository;
import ir.hanzodev1375.ghostide.postman.model.HistoryItem;
import ir.hanzodev1375.ghostide.postman.model.KeyValueItem;
import ir.hanzodev1375.ghostide.postman.model.RequestSnapshot;
import ir.hanzodev1375.ghostide.postman.network.HttpEngine;
import ir.hanzodev1375.ghostide.postman.util.AuthHeaders;
import ir.hanzodev1375.ghostide.postman.util.JsonUtils;
import ir.hanzodev1375.ghostide.postman.util.PrefsManager;
import ir.hanzodev1375.ghostide.postman.util.ResponseFormatter;
import ir.hanzodev1375.ghostide.postman.util.TimeUtils;
import ir.hanzodev1375.ghostide.postman.util.UiUtils;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The API client screen: a pinned request bar (method, URL, send/cancel), request tabs for
 * params, headers, body and auth, and a persistent response sheet with idle, loading, error,
 * cancelled and success states.
 *
 * <p>All database work, response formatting and file sharing run on one background executor that
 * is shut down with the activity; results are dropped when the activity is gone or when a newer
 * request has replaced the one they belong to.
 */
public class PostManActivity extends BaseCompat {

  public static final String EXTRA_SNAPSHOT_JSON = "extra_snapshot_json";

  private static final String DEFAULT_METHOD = "GET";
  private static final String DEFAULT_CONTENT_TYPE = "application/json";
  private static final int COPY_CHAR_LIMIT = 200_000;
  private static final int SHARE_TEXT_CHAR_LIMIT = 100_000;
  private static final String SHARE_DIR = "postman";
  private static final float RESPONSE_AREA_SCREEN_FRACTION = 0.5f;
  private static final int RESPONSE_AREA_MIN_DP = 180;
  private static final int RESPONSE_AREA_MAX_DP = 520;
  private static final int CONTENT_BOTTOM_GAP_DP = 16;

  private enum ResponseState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR,
    CANCELLED
  }

  private ActivityPostmanBinding binding;
  private AppRepository repository;
  private PrefsManager prefs;
  private final Gson gson = new Gson();
  private final ExecutorService io = Executors.newSingleThreadExecutor();

  private final List<KeyValueItem> paramItems = new ArrayList<>();
  private final List<KeyValueItem> headerItems = new ArrayList<>();
  private final List<KeyValueItem> formItems = new ArrayList<>();
  private KeyValueAdapter paramsAdapter;
  private KeyValueAdapter headersAdapter;
  private KeyValueAdapter formAdapter;

  private int bodyType = 0;
  private int authType = AuthHeaders.TYPE_NONE;
  @Nullable private String authHeaderWritten;
  private HttpEngine.HttpResult lastResult;
  private HttpEngine engine;
  private boolean requestInFlight;
  private int requestSerial;
  private ResponseState responseState = ResponseState.IDLE;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;
  private MaterialShapeDrawable sheetSurface;
  private int sheetPeekBase;
  private boolean rtl;

  private final ActivityResultLauncher<Intent> historyLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), this::handlePickedSnapshotResult);

  private final ActivityResultLauncher<Intent> collectionsLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), this::handlePickedSnapshotResult);

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityPostmanBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    setSupportActionBar(binding.toolbar);
    binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    rtl = LocaleDirection.isRtl(this);
    setupBackgroundBlur(
        binding.backgroundIconPostman, binding.appBarLayout, binding.contentScroll);
    UiUtils.tintContentForBackground(this, binding.appBarLayout, binding.contentScroll);
    repository = new AppRepository(this);
    prefs = new PrefsManager(this);
    setupMethodAndContentTypeDropdowns();
    setupRecyclerViews();
    setupRequestTabs();
    setupBodyTypeToggle();
    setupAuth();
    setupResponseSheet();
    setupRequestCard();
    setupSendButton();
    setupInsets();
    mirrorForReadingDirection();

    M3Theme.apply(binding.getRoot());
    M3Theme.toggleGroup(binding.bodyTypeToggleGroup);
    M3Theme.toggleGroup(binding.authTypeToggleGroup);
    applyScreenColors();
    showResponseState(ResponseState.IDLE);
  }

  @Override
  protected void onThemeReapplied() {
    super.onThemeReapplied();
    if (binding != null) {
      applyScreenColors();
    }
  }

  @Override
  protected void onDestroy() {
    requestSerial++;
    if (engine != null) {
      engine.shutdown();
      engine = null;
    }
    io.shutdown();
    super.onDestroy();
  }

  private void setupMethodAndContentTypeDropdowns() {
    ArrayAdapter<String> methodAdapter =
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            getResources().getStringArray(R.array.http_methods));
    binding.methodDropdown.setAdapter(methodAdapter);
    binding.methodDropdown.setText(DEFAULT_METHOD, false);
    binding.methodDropdown.setOnItemClickListener((parent, view, position, id) -> tintMethod());

    ArrayAdapter<String> contentTypeAdapter =
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            getResources().getStringArray(R.array.content_types));
    binding.contentTypeDropdown.setAdapter(contentTypeAdapter);
    binding.contentTypeDropdown.setText(DEFAULT_CONTENT_TYPE, false);
  }

  private void setupRecyclerViews() {
    paramItems.add(new KeyValueItem());
    paramsAdapter = new KeyValueAdapter(paramItems);
    binding.paramsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.paramsRecyclerView.setAdapter(paramsAdapter);
    binding.paramsRecyclerView.setNestedScrollingEnabled(false);
    binding.addParamButton.setOnClickListener(v -> paramsAdapter.addRow());

    headerItems.add(new KeyValueItem());
    headersAdapter = new KeyValueAdapter(headerItems);
    binding.headersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.headersRecyclerView.setAdapter(headersAdapter);
    binding.headersRecyclerView.setNestedScrollingEnabled(false);
    binding.addHeaderButton.setOnClickListener(v -> headersAdapter.addRow());

    formItems.add(new KeyValueItem());
    formAdapter = new KeyValueAdapter(formItems);
    binding.formRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.formRecyclerView.setAdapter(formAdapter);
    binding.formRecyclerView.setNestedScrollingEnabled(false);
    binding.addFormFieldButton.setOnClickListener(v -> formAdapter.addRow());
  }

  private void setupRequestTabs() {
    View[] panels = {
      binding.paramsPanel, binding.headersPanel, binding.bodyPanel, binding.authPanel
    };
    int[] titles = {
      R.string.tab_params, R.string.tab_headers, R.string.tab_body, R.string.postman_tab_auth
    };
    addPanelTabs(binding.requestTabs, titles, panels);
  }

  /**
   * Adds one tab per panel, in reading order (reversed for a right-to-left language, since the app
   * cannot mirror a TabLayout itself), and keeps exactly the selected tab's panel visible.
   */
  private void addPanelTabs(TabLayout tabs, int[] titles, View[] panels) {
    tabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            Object shown = tab.getTag();
            for (View panel : panels) {
              panel.setVisibility(panel == shown ? View.VISIBLE : View.GONE);
            }
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {}
        });
    TabLayout.Tab first = null;
    for (int n = 0; n < panels.length; n++) {
      int i = rtl ? panels.length - 1 - n : n;
      TabLayout.Tab tab = tabs.newTab().setText(titles[i]).setTag(panels[i]);
      tabs.addTab(tab, false);
      if (i == 0) {
        first = tab;
      }
    }
    if (first != null) {
      first.select();
    }
  }

  private void setupBodyTypeToggle() {
    binding.bodyTypeToggleGroup.addOnButtonCheckedListener(
        (group, checkedId, isChecked) -> {
          if (!isChecked) return;
          if (checkedId == binding.bodyTypeRaw.getId()) {
            bodyType = 1;
          } else if (checkedId == binding.bodyTypeForm.getId()) {
            bodyType = 2;
          } else {
            bodyType = 0;
          }
          binding.noneBodyText.setVisibility(bodyType == 0 ? View.VISIBLE : View.GONE);
          binding.rawBodyContainer.setVisibility(bodyType == 1 ? View.VISIBLE : View.GONE);
          binding.formBodyContainer.setVisibility(bodyType == 2 ? View.VISIBLE : View.GONE);
        });

    binding.formatJsonButton.setOnClickListener(
        v -> {
          String raw =
              binding.bodyRawInput.getText() == null
                  ? ""
                  : binding.bodyRawInput.getText().toString();
          binding.bodyRawInput.setText(JsonUtils.prettyPrint(raw));
        });

    binding.bodyTypeToggleGroup.check(binding.bodyTypeNone.getId());
  }

  private void setupAuth() {
    binding.authTypeToggleGroup.addOnButtonCheckedListener(
        (group, checkedId, isChecked) -> {
          if (!isChecked) return;
          int type;
          if (checkedId == binding.authTypeBearer.getId()) {
            type = AuthHeaders.TYPE_BEARER;
          } else if (checkedId == binding.authTypeBasic.getId()) {
            type = AuthHeaders.TYPE_BASIC;
          } else {
            type = AuthHeaders.TYPE_NONE;
          }
          if (type == AuthHeaders.TYPE_NONE && authType != AuthHeaders.TYPE_NONE) {
            removeWrittenAuthHeader();
          }
          authType = type;
          showAuthFields();
        });
    binding.authTypeToggleGroup.check(binding.authTypeNone.getId());
  }

  private void showAuthFields() {
    binding.authTokenInputLayout.setVisibility(
        authType == AuthHeaders.TYPE_BEARER ? View.VISIBLE : View.GONE);
    binding.authUserInputLayout.setVisibility(
        authType == AuthHeaders.TYPE_BASIC ? View.VISIBLE : View.GONE);
    binding.authPasswordInputLayout.setVisibility(
        authType == AuthHeaders.TYPE_BASIC ? View.VISIBLE : View.GONE);
    binding.authHintText.setText(
        authType == AuthHeaders.TYPE_NONE
            ? R.string.postman_auth_none_hint
            : R.string.postman_auth_set_hint);
  }

  /**
   * Writes the Auth tab's credentials into the header list as an enabled {@code Authorization}
   * row, reusing an existing row or a single empty one. Incomplete credentials leave the headers
   * untouched.
   */
  private void syncAuthHeader() {
    String value =
        AuthHeaders.build(
            authType,
            textOf(binding.authTokenInput),
            textOf(binding.authUserInput),
            textOf(binding.authPasswordInput));
    if (value == null) {
      return;
    }
    int index = AuthHeaders.find(headerItems);
    if (index >= 0) {
      KeyValueItem row = headerItems.get(index);
      row.setValue(value);
      row.setEnabled(true);
      headersAdapter.notifyItemChanged(index);
    } else if (headerItems.size() == 1 && headerItems.get(0).isBlank()) {
      headerItems.set(0, new KeyValueItem(AuthHeaders.HEADER_NAME, value, true));
      headersAdapter.notifyItemChanged(0);
    } else {
      headerItems.add(new KeyValueItem(AuthHeaders.HEADER_NAME, value, true));
      headersAdapter.notifyItemInserted(headerItems.size() - 1);
    }
    authHeaderWritten = value;
  }

  private void removeWrittenAuthHeader() {
    int index = AuthHeaders.find(headerItems);
    if (index < 0 || authHeaderWritten == null) {
      return;
    }
    if (!authHeaderWritten.equals(headerItems.get(index).getValue())) {
      return;
    }
    headerItems.remove(index);
    headersAdapter.notifyItemRemoved(index);
    if (headerItems.isEmpty()) {
      headersAdapter.addRow();
    }
    authHeaderWritten = null;
  }

  private void setupRequestCard() {
    if (Glass.isGlassEnabled(this)
        && binding.backgroundIconPostman.getVisibility() == View.VISIBLE) {
      binding.cardStatus.setBackdropSource(binding.backgroundIconPostman);
    }
  }

  private void setupResponseSheet() {
    sheetBehavior = BottomSheetBehavior.from(binding.responseSheet);
    sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    sheetPeekBase = getResources().getDimensionPixelSize(R.dimen.postman_sheet_peek);
    float corner = getResources().getDimension(R.dimen.postman_sheet_corner);
    sheetSurface =
        new MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, corner)
                .setTopRightCorner(CornerFamily.ROUNDED, corner)
                .build());
    binding.responseSheet.setBackground(sheetSurface);

    int screenHeightDp = getResources().getConfiguration().screenHeightDp;
    int areaDp =
        Math.max(
            RESPONSE_AREA_MIN_DP,
            Math.min(RESPONSE_AREA_MAX_DP, (int) (screenHeightDp * RESPONSE_AREA_SCREEN_FRACTION)));
    binding.responseStateFrame.getLayoutParams().height = dp(areaDp);

    OnBackPressedCallback collapseOnBack =
        new OnBackPressedCallback(false) {
          @Override
          public void handleOnBackPressed() {
            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
          }
        };
    getOnBackPressedDispatcher().addCallback(this, collapseOnBack);
    sheetBehavior.addBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View sheet, int newState) {
            collapseOnBack.setEnabled(newState == BottomSheetBehavior.STATE_EXPANDED);
          }

          @Override
          public void onSlide(@NonNull View sheet, float slideOffset) {}
        });

    binding.handleClickArea.setOnClickListener(
        v -> {
          int state = sheetBehavior.getState();
          sheetBehavior.setState(
              state == BottomSheetBehavior.STATE_EXPANDED
                  ? BottomSheetBehavior.STATE_COLLAPSED
                  : BottomSheetBehavior.STATE_EXPANDED);
        });

    addPanelTabs(
        binding.responseTabs,
        new int[] {R.string.tab_response_body, R.string.tab_response_headers},
        new View[] {binding.responseBodyScroll, binding.responseHeadersScroll});

    binding.copyBodyButton.setOnClickListener(v -> copyBody());
    binding.shareBodyButton.setOnClickListener(v -> shareBody());
    binding.retryButton.setOnClickListener(v -> sendRequest());
    binding.cancelRequestButton.setOnClickListener(v -> cancelRequest());
  }

  private void setupSendButton() {
    binding.sendButton.setOnClickListener(
        v -> {
          if (requestInFlight) {
            cancelRequest();
          } else {
            sendRequest();
          }
        });
    binding.urlInput.setOnEditorActionListener(
        (v, actionId, event) -> {
          sendRequest();
          return true;
        });
  }

  /**
   * Edge to edge: the request bar clears the status bar, the response sheet clears the navigation
   * bar (its peek grows by the inset), and the editor scrolls above both the collapsed sheet and
   * the keyboard.
   */
  private void setupInsets() {
    int gap = dp(CONTENT_BOTTOM_GAP_DP);
    ViewCompat.setOnApplyWindowInsetsListener(
        binding.rootCoordinator,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
          binding.appBarLayout.setPadding(bars.left, bars.top, bars.right, 0);
          binding.responseSheet.setPadding(bars.left, 0, bars.right, bars.bottom);
          sheetBehavior.setPeekHeight(sheetPeekBase + bars.bottom);
          int bottom = Math.max(ime.bottom, sheetPeekBase + bars.bottom) + gap;
          binding.contentScroll.setPadding(bars.left, 0, bars.right, bottom);
          if (ime.bottom > 0) {
            View focused = getCurrentFocus();
            if (focused != null) {
              focused.post(
                  () -> {
                    android.graphics.Rect area = new android.graphics.Rect();
                    focused.getDrawingRect(area);
                    focused.requestRectangleOnScreen(area, false);
                  });
            }
          }
          return insets;
        });
  }

  /** Puts label/action rows and toggle groups in reading order for right-to-left languages. */
  private void mirrorForReadingDirection() {
    if (!rtl) {
      return;
    }
    LocaleDirection.mirrorRow(binding.responseHeaderRow);
    LocaleDirection.mirrorRow(binding.rawBodyToolsRow);
    int padStart = binding.responseHeaderRow.getPaddingLeft();
    binding.responseHeaderRow.setPadding(
        binding.responseHeaderRow.getPaddingRight(), 0, padStart, 0);
    reverseChildren(binding.bodyTypeToggleGroup);
    reverseChildren(binding.authTypeToggleGroup);
    int start = LocaleDirection.startGravity(this);
    for (TextView text :
        new TextView[] {binding.paramsHint, binding.headersHint, binding.authHintText}) {
      text.setGravity(start);
    }
    binding.responseNoticeText.setGravity(start);
    binding.responseMetaText.setGravity(start | Gravity.CENTER_VERTICAL);
    for (View button :
        new View[] {binding.addParamButton, binding.addHeaderButton, binding.addFormFieldButton}) {
      ((LinearLayout.LayoutParams) button.getLayoutParams()).gravity = start;
    }
  }

  private static void reverseChildren(LinearLayout group) {
    int count = group.getChildCount();
    View[] children = new View[count];
    for (int i = 0; i < count; i++) {
      children[i] = group.getChildAt(i);
    }
    group.removeAllViews();
    for (int i = count - 1; i >= 0; i--) {
      group.addView(children[i]);
    }
  }

  private void applyScreenColors() {
    int surface = fallback(M3Theme.surfaceContainerLow(), fallback(M3Theme.surface(), 0));
    int outline = fallback(M3Theme.outlineVariant(), 0);
    sheetSurface.setFillColor(ColorStateList.valueOf(surface));
    sheetSurface.setStroke(dp(1), outline);

    int muted = fallback(M3Theme.onSurfaceVariant(), 0);
    ColorStateList mutedTint = ColorStateList.valueOf(muted);
    binding.copyBodyButton.setImageTintList(mutedTint);
    binding.shareBodyButton.setImageTintList(mutedTint);
    binding.responseEmptyIcon.setImageTintList(mutedTint);
    binding.responseErrorIcon.setImageTintList(
        responseState == ResponseState.CANCELLED
            ? mutedTint
            : ColorStateList.valueOf(fallback(M3Theme.error(), muted)));
    binding.responseLoadingIndicator.setIndicatorColor(fallback(M3Theme.primary(), muted));
    binding.responseLoadingIndicator.setTrackColor(
        ColorUtils.setAlphaComponent(fallback(M3Theme.primary(), muted), 0x33));
    binding.progressIndicator.setTrackColor(android.graphics.Color.TRANSPARENT);
    UiUtils.tintToolbar(binding.toolbar);
    tintMethod();
    tintStatusChip();
  }

  private void tintMethod() {
    String method = currentMethod();
    binding.methodDropdown.setTextColor(UiUtils.readableAccent(UiUtils.methodColor(this, method)));
  }

  private void tintStatusChip() {
    HttpEngine.HttpResult result = lastResult;
    if (responseState == ResponseState.SUCCESS && result != null) {
      UiUtils.tonalPill(binding.responseStatusChip, UiUtils.statusColor(this, result.statusCode));
    } else if (responseState == ResponseState.ERROR) {
      UiUtils.tonalPill(binding.responseStatusChip, fallback(M3Theme.error(), 0));
    }
  }

  private void sendRequest() {
    if (requestInFlight) {
      return;
    }
    syncAuthHeader();
    RequestSnapshot snapshot = buildSnapshot();
    if (snapshot.url.trim().isEmpty()) {
      binding.urlInputLayout.setError(getString(R.string.msg_invalid_url));
      binding.urlInput.requestFocus();
      return;
    }
    binding.urlInputLayout.setError(null);
    binding.urlInputLayout.setErrorEnabled(false);
    hideKeyboard();

    String historyJson = gson.toJson(snapshot);
    String method = snapshot.method;
    String url = snapshot.url;
    int serial = ++requestSerial;
    setSending(true);
    showResponseState(ResponseState.LOADING);
    sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

    obtainEngine()
        .execute(
            snapshot,
            result -> {
              if (isDestroyed() || serial != requestSerial) {
                return;
              }
              setSending(false);
              if (result.errorKind == HttpEngine.ErrorKind.CANCELLED) {
                lastResult = null;
                showResponseState(ResponseState.CANCELLED);
                return;
              }
              saveToHistory(method, url, historyJson, result);
              showResponse(result, serial);
            });
  }

  private HttpEngine obtainEngine() {
    int timeout = prefs.getTimeoutSeconds();
    boolean insecure = prefs.isSslVerificationDisabled();
    if (engine == null || !engine.matches(timeout, insecure)) {
      if (engine != null) {
        engine.shutdown();
      }
      engine = new HttpEngine(timeout, insecure);
    }
    return engine;
  }

  private void cancelRequest() {
    if (requestInFlight && engine != null) {
      engine.cancel();
    }
  }

  private void setSending(boolean sending) {
    requestInFlight = sending;
    binding.progressIndicator.setVisibility(sending ? View.VISIBLE : View.INVISIBLE);
    MaterialButton send = binding.sendButton;
    send.setText(sending ? R.string.action_cancel : R.string.action_send);
    send.setIconResource(sending ? R.drawable.ic_close : R.drawable.ic_send);
    M3Theme.materialButton(send);
  }

  private RequestSnapshot buildSnapshot() {
    RequestSnapshot snapshot = new RequestSnapshot();
    snapshot.method = currentMethod();
    snapshot.url = textOf(binding.urlInput).trim();
    snapshot.params = paramItems;
    snapshot.headers = headerItems;
    snapshot.bodyType = bodyType;
    snapshot.rawBody = textOf(binding.bodyRawInput);
    String contentType = textOf(binding.contentTypeDropdown).trim();
    snapshot.rawContentType = contentType.isEmpty() ? DEFAULT_CONTENT_TYPE : contentType;
    snapshot.formFields = formItems;
    return snapshot;
  }

  private String currentMethod() {
    String method = textOf(binding.methodDropdown).trim();
    return method.isEmpty() ? DEFAULT_METHOD : method;
  }

  private static String textOf(TextView view) {
    CharSequence text = view.getText();
    return text == null ? "" : text.toString();
  }

  private void showResponseState(ResponseState state) {
    responseState = state;
    binding.responseEmptyState.setVisibility(
        state == ResponseState.IDLE ? View.VISIBLE : View.GONE);
    binding.responseLoadingState.setVisibility(
        state == ResponseState.LOADING ? View.VISIBLE : View.GONE);
    boolean problem = state == ResponseState.ERROR || state == ResponseState.CANCELLED;
    binding.responseErrorState.setVisibility(problem ? View.VISIBLE : View.GONE);
    binding.responseContentContainer.setVisibility(
        state == ResponseState.SUCCESS ? View.VISIBLE : View.GONE);
    boolean hasBody = state == ResponseState.SUCCESS;
    binding.copyBodyButton.setVisibility(hasBody ? View.VISIBLE : View.GONE);
    binding.shareBodyButton.setVisibility(hasBody ? View.VISIBLE : View.GONE);

    switch (state) {
      case LOADING:
        binding.responseStatusChip.setVisibility(View.GONE);
        binding.responseMetaText.setText(R.string.postman_waiting);
        break;
      case CANCELLED:
        binding.responseStatusChip.setVisibility(View.GONE);
        binding.responseMetaText.setText(R.string.postman_cancelled);
        binding.responseErrorTitle.setText(R.string.postman_cancelled);
        binding.responseErrorMessage.setText(R.string.postman_cancelled_detail);
        binding.responseErrorIcon.setImageResource(R.drawable.ic_close);
        break;
      case ERROR:
        binding.responseStatusChip.setVisibility(View.VISIBLE);
        binding.responseStatusChip.setText(R.string.postman_status_error);
        binding.responseErrorTitle.setText(R.string.postman_error_title);
        binding.responseErrorIcon.setImageResource(R.drawable.ic_plugin_state_error);
        break;
      case SUCCESS:
        binding.responseStatusChip.setVisibility(View.VISIBLE);
        break;
      case IDLE:
      default:
        binding.responseStatusChip.setVisibility(View.GONE);
        binding.responseMetaText.setText("");
        break;
    }
    applyScreenColors();
  }

  private void showResponse(HttpEngine.HttpResult result, int serial) {
    lastResult = result;
    if (!result.success) {
      binding.responseErrorMessage.setText(errorMessageFor(result));
      binding.responseMetaText.setText(
          result.timeMs > 0 ? TimeUtils.formatDuration(result.timeMs) : "");
      showResponseState(ResponseState.ERROR);
      sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
      return;
    }

    binding.responseStatusChip.setText(
        UiUtils.statusLabel(result.statusCode, result.statusMessage));
    binding.responseMetaText.setText(
        TimeUtils.formatDuration(result.timeMs) + " · " + TimeUtils.formatSize(result.sizeBytes));
    binding.responseBodyText.setText("");
    binding.responseHeadersText.setText("");
    binding.responseNoticeText.setVisibility(View.GONE);
    showResponseState(ResponseState.SUCCESS);
    sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

    int keyColor = fallback(M3Theme.primary(), 0);
    int stringColor = fallback(M3Theme.tertiary(), 0);
    int numberColor = fallback(M3Theme.secondary(), 0);
    PrecomputedTextCompat.Params bodyParams =
        TextViewCompat.getTextMetricsParams(binding.responseBodyText);
    PrecomputedTextCompat.Params headerParams =
        TextViewCompat.getTextMetricsParams(binding.responseHeadersText);
    io.execute(
        () -> {
          ResponseFormatter.Formatted formatted =
              ResponseFormatter.format(
                  result.body, result.headers, keyColor, stringColor, numberColor);
          PrecomputedTextCompat body =
              formatted.body.length() == 0
                  ? null
                  : PrecomputedTextCompat.create(formatted.body, bodyParams);
          PrecomputedTextCompat headers =
              formatted.headers.length() == 0
                  ? null
                  : PrecomputedTextCompat.create(formatted.headers, headerParams);
          runOnUiThread(
              () -> {
                if (isDestroyed() || serial != requestSerial) {
                  return;
                }
                applyFormatted(result, formatted, body, headers);
              });
        });
  }

  private void applyFormatted(
      HttpEngine.HttpResult result,
      ResponseFormatter.Formatted formatted,
      @Nullable PrecomputedTextCompat body,
      @Nullable PrecomputedTextCompat headers) {
    setPrecomputed(binding.responseBodyText, body, getString(R.string.postman_empty_body));
    setPrecomputed(binding.responseHeadersText, headers, getString(R.string.postman_no_headers));

    String notice = null;
    if (result.errorKind == HttpEngine.ErrorKind.BODY_UNREADABLE) {
      notice = getString(R.string.postman_error_body_unreadable);
    } else if (result.bodyTruncated) {
      notice =
          getString(
              R.string.postman_body_truncated,
              TimeUtils.formatSize(HttpEngine.MAX_DISPLAY_BYTES),
              TimeUtils.formatSize(result.sizeBytes));
    } else if (formatted.clipped) {
      notice = getString(R.string.postman_body_clipped, TimeUtils.formatSize(result.sizeBytes));
    }
    binding.responseNoticeText.setText(notice);
    binding.responseNoticeText.setVisibility(notice == null ? View.GONE : View.VISIBLE);
    binding.responseBodyScroll.scrollTo(0, 0);
    binding.responseHeadersScroll.scrollTo(0, 0);
  }

  /**
   * Shows precomputed text; if the text was measured with different paint settings than the view
   * now has, it falls back to letting the view lay the text out itself.
   */
  private static void setPrecomputed(
      TextView view, @Nullable PrecomputedTextCompat text, String emptyText) {
    if (text == null) {
      view.setText(emptyText);
      return;
    }
    try {
      TextViewCompat.setPrecomputedText(view, text);
    } catch (IllegalArgumentException paramsChanged) {
      view.setText(text.toString());
    }
  }

  private String errorMessageFor(HttpEngine.HttpResult result) {
    String host = result.host == null || result.host.isEmpty() ? "?" : result.host;
    switch (result.errorKind) {
      case INVALID_URL:
        return getString(R.string.msg_invalid_url);
      case TIMEOUT:
        return getString(R.string.postman_error_timeout, prefs.getTimeoutSeconds());
      case UNKNOWN_HOST:
        return UiUtils.isOnline(this)
            ? getString(R.string.postman_error_unknown_host, host)
            : getString(R.string.postman_error_offline);
      case CONNECTION_REFUSED:
        return getString(R.string.postman_error_refused, host);
      case TLS:
        return getString(R.string.postman_error_tls, host);
      default:
        String detail = result.errorMessage == null ? "" : result.errorMessage;
        return getString(R.string.postman_error_generic, detail);
    }
  }

  private void saveToHistory(
      String method, String url, String requestJson, HttpEngine.HttpResult result) {
    HistoryItem item = new HistoryItem();
    item.method = method;
    item.url = url;
    item.statusCode = result.success ? result.statusCode : 0;
    item.timeMs = result.timeMs;
    item.timestamp = System.currentTimeMillis();
    item.requestJson = requestJson;
    io.execute(
        () -> {
          try {
            repository.insertHistory(item);
          } catch (android.database.SQLException e) {
            android.util.Log.w("PostManActivity", "History insert failed", e);
          }
        });
  }

  private void copyBody() {
    HttpEngine.HttpResult result = lastResult;
    if (result == null || !result.success) return;
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (clipboard == null) return;
    String body = result.body == null ? "" : result.body;
    boolean partial = body.length() > COPY_CHAR_LIMIT;
    String text = partial ? body.substring(0, COPY_CHAR_LIMIT) : body;
    try {
      clipboard.setPrimaryClip(ClipData.newPlainText("response body", text));
    } catch (RuntimeException clipboardRejected) {
      snack(getString(R.string.postman_copy_failed));
      return;
    }
    snack(
        partial
            ? getString(R.string.postman_copied_partial, COPY_CHAR_LIMIT)
            : getString(R.string.msg_copied));
  }

  /**
   * Shares the body as text when it is small; a larger body is written to the cache directory on
   * the background executor and shared through the app's FileProvider, so the binder limit for
   * intent extras is never hit.
   */
  private void shareBody() {
    HttpEngine.HttpResult result = lastResult;
    if (result == null || !result.success) return;
    String body = result.body == null ? "" : result.body;
    if (body.length() <= SHARE_TEXT_CHAR_LIMIT) {
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("text/plain");
      send.putExtra(Intent.EXTRA_TEXT, body);
      startShare(send);
      return;
    }
    File dir = new File(getCacheDir(), SHARE_DIR);
    int serial = requestSerial;
    io.execute(
        () -> {
          boolean json = JsonUtils.looksLikeJson(body);
          File file = new File(dir, json ? "response.json" : "response.txt");
          boolean written = writeText(dir, file, body);
          runOnUiThread(
              () -> {
                if (isDestroyed() || serial != requestSerial) return;
                if (!written) {
                  snack(getString(R.string.postman_share_failed));
                  return;
                }
                Uri uri;
                try {
                  uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                } catch (IllegalArgumentException notShareable) {
                  snack(getString(R.string.postman_share_failed));
                  return;
                }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(json ? "application/json" : "text/plain");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.setClipData(ClipData.newRawUri(file.getName(), uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startShare(send);
              });
        });
  }

  private static boolean writeText(File dir, File file, String text) {
    if (!dir.isDirectory() && !dir.mkdirs()) {
      return false;
    }
    try (Writer out =
        new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
      out.write(text);
      return true;
    } catch (IOException e) {
      android.util.Log.w("PostManActivity", "Could not write the shared response", e);
      return false;
    }
  }

  private void startShare(Intent send) {
    try {
      startActivity(Intent.createChooser(send, getString(R.string.postman_share_title)));
    } catch (ActivityNotFoundException noTarget) {
      snack(getString(R.string.postman_share_failed));
    }
  }

  private void snack(CharSequence message) {
    Snackbar bar = Snackbar.make(binding.rootCoordinator, message, Snackbar.LENGTH_SHORT);
    bar.setAnchorView(binding.responseSheet);
    bar.show();
  }

  private void snack(@StringRes int message) {
    snack(getString(message));
  }

  private void hideKeyboard() {
    View focused = getCurrentFocus();
    if (focused == null) return;
    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
  }

  private void handlePickedSnapshotResult(ActivityResult result) {
    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
    String json = result.getData().getStringExtra(EXTRA_SNAPSHOT_JSON);
    applySnapshot(json);
  }

  private void applySnapshot(@Nullable String json) {
    if (json == null || json.isEmpty()) return;
    RequestSnapshot snapshot;
    try {
      snapshot = gson.fromJson(json, RequestSnapshot.class);
    } catch (JsonParseException e) {
      snack(getString(R.string.msg_request_failed, e.getMessage()));
      return;
    }
    if (snapshot == null) return;

    binding.methodDropdown.setText(
        snapshot.method == null ? DEFAULT_METHOD : snapshot.method, false);
    tintMethod();
    binding.urlInput.setText(snapshot.url);

    replaceRows(paramItems, snapshot.params, paramsAdapter);
    replaceRows(headerItems, snapshot.headers, headersAdapter);
    replaceRows(formItems, snapshot.formFields, formAdapter);

    binding.bodyRawInput.setText(snapshot.rawBody);
    binding.contentTypeDropdown.setText(
        snapshot.rawContentType == null ? DEFAULT_CONTENT_TYPE : snapshot.rawContentType, false);

    int checkedButtonId;
    if (snapshot.bodyType == 1) {
      checkedButtonId = binding.bodyTypeRaw.getId();
    } else if (snapshot.bodyType == 2) {
      checkedButtonId = binding.bodyTypeForm.getId();
    } else {
      checkedButtonId = binding.bodyTypeNone.getId();
    }
    binding.bodyTypeToggleGroup.check(checkedButtonId);
    loadAuthFromHeaders();

    snack(R.string.msg_request_loaded);
  }

  private static void replaceRows(
      List<KeyValueItem> target, @Nullable List<KeyValueItem> source, KeyValueAdapter adapter) {
    target.clear();
    if (source != null) {
      for (KeyValueItem item : source) {
        if (item != null) {
          target.add(new KeyValueItem(item.getKey(), item.getValue(), item.isEnabled()));
        }
      }
    }
    if (target.isEmpty()) target.add(new KeyValueItem());
    adapter.notifyDataSetChanged();
  }

  /** Reflects a loaded request's {@code Authorization} header in the Auth tab. */
  private void loadAuthFromHeaders() {
    int index = AuthHeaders.findEnabled(headerItems);
    AuthHeaders.Parsed parsed =
        AuthHeaders.parse(index >= 0 ? headerItems.get(index).getValue() : null);
    authType = AuthHeaders.TYPE_NONE;
    authHeaderWritten =
        parsed.type == AuthHeaders.TYPE_NONE ? null : headerItems.get(index).getValue();
    binding.authTokenInput.setText(parsed.token);
    binding.authUserInput.setText(parsed.user);
    binding.authPasswordInput.setText(parsed.password);
    int buttonId;
    if (parsed.type == AuthHeaders.TYPE_BEARER) {
      buttonId = binding.authTypeBearer.getId();
    } else if (parsed.type == AuthHeaders.TYPE_BASIC) {
      buttonId = binding.authTypeBasic.getId();
    } else {
      buttonId = binding.authTypeNone.getId();
    }
    if (binding.authTypeToggleGroup.getCheckedButtonId() == buttonId) {
      authType = parsed.type;
      showAuthFields();
    } else {
      binding.authTypeToggleGroup.check(buttonId);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    int tint = fallback(M3Theme.onSurface(), 0);
    for (int i = 0; i < menu.size(); i++) {
      MenuItem item = menu.getItem(i);
      if (item.getIcon() != null) {
        item.getIcon().mutate().setTint(tint);
      }
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_history) {
      historyLauncher.launch(new Intent(this, HistoryActivity.class));
      return true;
    } else if (id == R.id.action_collections) {
      collectionsLauncher.launch(new Intent(this, CollectionsActivity.class));
      return true;
    } else if (id == R.id.action_save) {
      syncAuthHeader();
      RequestSnapshot snapshot = buildSnapshot();
      if (snapshot.url.trim().isEmpty()) {
        snack(R.string.msg_invalid_url);
        return true;
      }
      String json = gson.toJson(snapshot);
      SaveRequestBottomSheet sheet =
          SaveRequestBottomSheet.newInstance(snapshot.method, snapshot.url, json);
      sheet.setListener(() -> snack(R.string.msg_request_saved));
      sheet.show(getSupportFragmentManager(), "save_request");
      return true;
    } else if (id == R.id.action_settings) {
      startActivity(new Intent(this, SettingActivity.class));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private static int fallback(Integer value, int def) {
    return value != null ? value : def;
  }
}
