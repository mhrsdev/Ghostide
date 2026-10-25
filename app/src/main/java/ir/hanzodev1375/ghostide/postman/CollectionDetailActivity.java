package ir.hanzodev1375.ghostide.postman;

import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.ghostide.activity.BaseCompat;
import ir.hanzodev1375.ghostide.postman.util.UiUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.postman.adapter.SavedRequestAdapter;
import ir.hanzodev1375.ghostide.postman.data.AppRepository;
import ir.hanzodev1375.ghostide.databinding.ActivityCollectionDetailBinding;
import ir.hanzodev1375.ghostide.postman.model.SavedRequest;
import ir.theme.M3Theme;

/** The saved requests of one collection; picking one returns it to the caller. */
public class CollectionDetailActivity extends BaseCompat {

  private static final String TAG = "CollectionDetail";

  private ActivityCollectionDetailBinding binding;
  private AppRepository repository;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private long collectionId;
  private final List<SavedRequest> items = new ArrayList<>();
  private SavedRequestAdapter adapter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityCollectionDetailBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    setupBackgroundBlur(
        binding.backgroundIconCollectionDetail, binding.appBarLayout, binding.contentFrame);
    UiUtils.tintContentForBackground(this, binding.appBarLayout, binding.contentFrame);
    UiUtils.applyListInsets(
        binding.appBarLayout,
        binding.contentFrame,
        binding.requestsRecyclerView,
        binding.emptyState,
        null);
    collectionId = getIntent().getLongExtra(CollectionsActivity.EXTRA_COLLECTION_ID, 0);
    String collectionName = getIntent().getStringExtra(CollectionsActivity.EXTRA_COLLECTION_NAME);
    binding.toolbar.setTitle(
        collectionName != null ? collectionName : getString(R.string.title_collection_detail));
    binding.toolbar.setNavigationOnClickListener(v -> finish());

    repository = new AppRepository(this);

    adapter =
        new SavedRequestAdapter(
            items,
            new SavedRequestAdapter.Listener() {
              @Override
              public void onItemClick(SavedRequest request) {
                Intent result = new Intent();
                result.putExtra(PostManActivity.EXTRA_SNAPSHOT_JSON, request.requestJson);
                setResult(RESULT_OK, result);
                finish();
              }

              @Override
              public void onDeleteClick(SavedRequest request) {
                new DialogCompat(CollectionDetailActivity.this)
                    .setTitle(R.string.dialog_confirm_title)
                    .setMessage(R.string.msg_confirm_delete_saved_request)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(
                        R.string.action_delete,
                        (dialog, which) ->
                            runThenReload(() -> repository.deleteSavedRequest(request.id)))
                    .show();
              }
            });

    binding.requestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.requestsRecyclerView.setAdapter(adapter);

    M3Theme.apply(binding.getRoot());
    applyColors();

    loadRequests();
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

  private void runThenReload(Runnable write) {
    io.execute(
        () -> {
          try {
            write.run();
          } catch (SQLException e) {
            Log.w(TAG, "Saved request update failed", e);
          }
        });
    loadRequests();
  }

  private void loadRequests() {
    io.execute(
        () -> {
          List<SavedRequest> loaded;
          try {
            loaded = repository.getSavedRequests(collectionId);
          } catch (SQLException e) {
            Log.w(TAG, "Saved requests could not be read", e);
            loaded = new ArrayList<>();
          }
          List<SavedRequest> result = loaded;
          runOnUiThread(() -> showRequests(result));
        });
  }

  private void showRequests(List<SavedRequest> loaded) {
    if (isDestroyed()) {
      return;
    }
    items.clear();
    items.addAll(loaded);
    adapter.notifyDataSetChanged();
    binding.emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    binding.requestsRecyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
  }
}
