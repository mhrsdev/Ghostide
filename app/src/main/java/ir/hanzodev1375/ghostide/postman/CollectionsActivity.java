package ir.hanzodev1375.ghostide.postman;

import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.ghostide.activity.BaseCompat;
import ir.hanzodev1375.ghostide.postman.util.UiUtils;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.postman.adapter.CollectionAdapter;
import ir.hanzodev1375.ghostide.postman.data.AppRepository;
import ir.hanzodev1375.ghostide.databinding.ActivityCollectionsBinding;
import ir.hanzodev1375.ghostide.postman.model.RequestCollection;
import ir.theme.M3Theme;

/** Lists request collections; a request picked inside one is passed back to the API client. */
public class CollectionsActivity extends BaseCompat {

  public static final String EXTRA_COLLECTION_ID = "extra_collection_id";
  public static final String EXTRA_COLLECTION_NAME = "extra_collection_name";

  private static final String TAG = "CollectionsActivity";
  private static final int DIALOG_FIELD_MARGIN_DP = 20;

  private ActivityCollectionsBinding binding;
  private AppRepository repository;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final List<RequestCollection> items = new ArrayList<>();
  private CollectionAdapter adapter;

  private final ActivityResultLauncher<Intent> collectionDetailLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
              setResult(RESULT_OK, result.getData());
              finish();
            }
          });

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityCollectionsBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    setupBackgroundBlur(
        binding.backgroundIconCollections, binding.appBarLayout, binding.contentFrame);
    UiUtils.tintContentForBackground(this, binding.appBarLayout, binding.contentFrame);
    UiUtils.applyListInsets(
        binding.appBarLayout,
        binding.contentFrame,
        binding.collectionsRecyclerView,
        binding.emptyState,
        binding.newCollectionFab);
    repository = new AppRepository(this);
    binding.toolbar.setNavigationOnClickListener(v -> finish());

    adapter =
        new CollectionAdapter(
            items,
            new CollectionAdapter.Listener() {
              @Override
              public void onItemClick(RequestCollection collection) {
                Intent intent =
                    new Intent(CollectionsActivity.this, CollectionDetailActivity.class);
                intent.putExtra(EXTRA_COLLECTION_ID, collection.id);
                intent.putExtra(EXTRA_COLLECTION_NAME, collection.name);
                collectionDetailLauncher.launch(intent);
              }

              @Override
              public void onDeleteClick(RequestCollection collection) {
                new DialogCompat(CollectionsActivity.this)
                    .setTitle(R.string.dialog_confirm_title)
                    .setMessage(R.string.msg_confirm_delete_collection)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(
                        R.string.action_delete,
                        (dialog, which) ->
                            runThenReload(() -> repository.deleteCollection(collection.id)))
                    .show();
              }
            });

    binding.collectionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.collectionsRecyclerView.setAdapter(adapter);

    binding.newCollectionFab.setOnClickListener(v -> showNewCollectionDialog());
    if (LocaleDirection.isRtl(this)) {
      CoordinatorLayout.LayoutParams fabParams =
          (CoordinatorLayout.LayoutParams) binding.newCollectionFab.getLayoutParams();
      fabParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
      binding.newCollectionFab.setLayoutParams(fabParams);
    }

    M3Theme.apply(binding.getRoot());
    applyColors();
  }

  @Override
  protected void onResume() {
    super.onResume();
    loadCollections();
  }

  @Override
  protected void onThemeReapplied() {
    super.onThemeReapplied();
    if (binding != null) {
      applyColors();
      adapter.notifyDataSetChanged();
    }
  }

  private void applyColors() {
    binding.emptyIcon.setImageTintList(UiUtils.mutedTint());
    UiUtils.tintToolbar(binding.toolbar);
  }

  @Override
  protected void onDestroy() {
    io.shutdown();
    super.onDestroy();
  }

  private void showNewCollectionDialog() {
    TextInputLayout field =
        new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    field.setHintEnabled(false);
    TextInputEditText input = new TextInputEditText(field.getContext());
    input.setHint(R.string.hint_collection_name);
    input.setSingleLine(true);
    field.addView(input);
    FrameLayout container = new FrameLayout(this);
    int margin = Math.round(DIALOG_FIELD_MARGIN_DP * getResources().getDisplayMetrics().density);
    FrameLayout.LayoutParams params =
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
    params.setMargins(margin, margin / 2, margin, 0);
    container.addView(field, params);
    M3Theme.apply(container);

    new DialogCompat(this)
        .setTitle(R.string.dialog_new_collection_title)
        .setView(container)
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(
            R.string.action_create,
            (dialog, which) -> {
              String name = input.getText() == null ? "" : input.getText().toString().trim();
              if (name.isEmpty()) return;
              runThenReload(() -> repository.getOrCreateCollection(name));
            })
        .show();
  }

  private void runThenReload(Runnable write) {
    io.execute(
        () -> {
          try {
            write.run();
          } catch (SQLException e) {
            Log.w(TAG, "Collection update failed", e);
          }
        });
    loadCollections();
  }

  private void loadCollections() {
    io.execute(
        () -> {
          List<RequestCollection> loaded;
          try {
            loaded = repository.getCollections();
          } catch (SQLException e) {
            Log.w(TAG, "Collections could not be read", e);
            loaded = new ArrayList<>();
          }
          List<RequestCollection> result = loaded;
          runOnUiThread(() -> showCollections(result));
        });
  }

  private void showCollections(List<RequestCollection> loaded) {
    if (isDestroyed()) {
      return;
    }
    items.clear();
    items.addAll(loaded);
    adapter.notifyDataSetChanged();
    binding.emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    binding.collectionsRecyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
  }
}
