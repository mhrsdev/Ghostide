package ir.hanzodev1375.ghostide.postman;

import android.content.Context;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.postman.data.AppRepository;
import ir.hanzodev1375.ghostide.postman.model.RequestCollection;
import ir.hanzodev1375.ghostide.postman.model.SavedRequest;
import ir.hanzodev1375.ghostide.postman.util.UiUtils;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.components.sheet.BaseBlurBottomSheet;
import ir.theme.M3Theme;

/**
 * Names the current request and stores it, optionally inside a collection that is created on the
 * fly. Database work runs on a single background thread; results are only applied while the
 * sheet's view still exists, and a save that was started is always allowed to finish.
 */
public class SaveRequestBottomSheet extends BaseBlurBottomSheet {

  private static final String TAG = "SaveRequestBottomSheet";
  private static final String ARG_METHOD = "arg_method";
  private static final String ARG_URL = "arg_url";
  private static final String ARG_SNAPSHOT_JSON = "arg_snapshot_json";

  public interface Listener {
    void onSaved();
  }

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private ExecutorService io;
  private Listener listener;
  private View rootView;

  public static SaveRequestBottomSheet newInstance(String method, String url, String snapshotJson) {
    SaveRequestBottomSheet fragment = new SaveRequestBottomSheet();
    Bundle args = new Bundle();
    args.putString(ARG_METHOD, method);
    args.putString(ARG_URL, url);
    args.putString(ARG_SNAPSHOT_JSON, snapshotJson);
    fragment.setArguments(args);
    return fragment;
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  protected void onContentReady(ViewGroup contentContainer) {
    rootView =
        LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_save_request, contentContainer, false);
    contentContainer.addView(rootView);
    io = Executors.newSingleThreadExecutor();

    Bundle args = requireArguments();
    String method = args.getString(ARG_METHOD, "GET");
    String url = args.getString(ARG_URL, "");
    String snapshotJson = args.getString(ARG_SNAPSHOT_JSON, "");

    TextView badge = rootView.findViewById(R.id.requestMethodBadge);
    TextView urlText = rootView.findViewById(R.id.requestUrlText);
    badge.setText(method);
    urlText.setText(url);
    alignForReadingDirection();
    M3Theme.apply(rootView);
    UiUtils.tonalPill(badge, UiUtils.methodColor(requireContext(), method));

    Context appContext = requireContext().getApplicationContext();
    AppRepository repository = new AppRepository(appContext);

    io.execute(
        () -> {
          List<String> names = new ArrayList<>();
          try {
            for (RequestCollection c : repository.getCollections()) names.add(c.name);
          } catch (SQLException e) {
            Log.w(TAG, "Could not load collections", e);
          }
          mainHandler.post(() -> showCollectionNames(names));
        });

    rootView
        .findViewById(R.id.confirmSaveButton)
        .setOnClickListener(v -> save(repository, method, url, snapshotJson));
  }

  private void alignForReadingDirection() {
    if (!LocaleDirection.isRtl(requireContext())) {
      return;
    }
    int start = LocaleDirection.startGravity(requireContext());
    int[] labels = {
      R.id.saveTitle, R.id.requestNameLabel, R.id.collectionNameLabel, R.id.collectionHelpText
    };
    for (int id : labels) {
      ((TextView) rootView.findViewById(id)).setGravity(start);
    }
    LocaleDirection.mirrorRow((LinearLayout) rootView.findViewById(R.id.requestPreviewRow));
    ((TextView) rootView.findViewById(R.id.requestUrlText)).setGravity(start);
  }

  private void showCollectionNames(List<String> names) {
    if (!isAdded() || rootView == null) return;
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
    AutoCompleteTextView auto = rootView.findViewById(R.id.collectionNameInput);
    auto.setAdapter(adapter);
  }

  private void save(AppRepository repository, String method, String url, String snapshotJson) {
    TextInputEditText nameInput = rootView.findViewById(R.id.requestNameInput);
    TextInputLayout nameLayout = rootView.findViewById(R.id.requestNameInputLayout);
    String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
    if (name.isEmpty()) {
      nameLayout.setError(getString(R.string.msg_name_required));
      return;
    }
    nameLayout.setError(null);
    AutoCompleteTextView collectionInput = rootView.findViewById(R.id.collectionNameInput);
    String collectionName =
        collectionInput.getText() == null ? "" : collectionInput.getText().toString().trim();

    View confirm = rootView.findViewById(R.id.confirmSaveButton);
    confirm.setEnabled(false);
    Listener savedListener = listener;
    io.execute(
        () -> {
          boolean saved;
          try {
            long collectionId = 0;
            if (!collectionName.isEmpty()) {
              collectionId = repository.getOrCreateCollection(collectionName);
            }
            SavedRequest request = new SavedRequest();
            request.collectionId = collectionId;
            request.name = name;
            request.method = method;
            request.url = url;
            request.requestJson = snapshotJson;
            saved = repository.insertSavedRequest(request) != -1;
          } catch (SQLException e) {
            Log.w(TAG, "Could not save the request", e);
            saved = false;
          }
          boolean success = saved;
          mainHandler.post(() -> onSaveFinished(success, savedListener));
        });
  }

  private void onSaveFinished(boolean success, Listener savedListener) {
    if (!isAdded() || rootView == null) return;
    if (!success) {
      rootView.findViewById(R.id.confirmSaveButton).setEnabled(true);
      ((TextInputLayout) rootView.findViewById(R.id.requestNameInputLayout))
          .setError(getString(R.string.postman_save_failed));
      return;
    }
    if (savedListener != null) savedListener.onSaved();
    dismiss();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    rootView = null;
    if (io != null) {
      io.shutdown();
      io = null;
    }
  }
}
