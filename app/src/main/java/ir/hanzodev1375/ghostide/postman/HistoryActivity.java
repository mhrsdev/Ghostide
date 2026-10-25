package ir.hanzodev1375.ghostide.postman;

import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import ir.hanzodev1375.ghostide.postman.adapter.HistoryAdapter;
import ir.hanzodev1375.ghostide.postman.data.AppRepository;
import ir.hanzodev1375.ghostide.databinding.ActivityHistoryBinding;
import ir.hanzodev1375.ghostide.postman.model.HistoryItem;
import ir.theme.M3Theme;

/** Request history, newest first; picking a row returns its request to the API client. */
public class HistoryActivity extends BaseCompat {

  private static final String TAG = "HistoryActivity";

  private ActivityHistoryBinding binding;
  private AppRepository repository;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final List<HistoryItem> items = new ArrayList<>();
  private HistoryAdapter adapter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityHistoryBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    setupBackgroundBlur(binding.backgroundIconHistory, binding.appBarLayout, binding.contentFrame);
    UiUtils.tintContentForBackground(this, binding.appBarLayout, binding.contentFrame);
    UiUtils.applyListInsets(
        binding.appBarLayout,
        binding.contentFrame,
        binding.historyRecyclerView,
        binding.emptyState,
        null);
    setSupportActionBar(binding.toolbar);
    binding.toolbar.setNavigationOnClickListener(v -> finish());

    repository = new AppRepository(this);

    adapter =
        new HistoryAdapter(
            items,
            new HistoryAdapter.Listener() {
              @Override
              public void onItemClick(HistoryItem item) {
                Intent result = new Intent();
                result.putExtra(PostManActivity.EXTRA_SNAPSHOT_JSON, item.requestJson);
                setResult(RESULT_OK, result);
                finish();
              }

              @Override
              public void onDeleteClick(HistoryItem item) {
                new DialogCompat(HistoryActivity.this)
                    .setTitle(R.string.dialog_confirm_title)
                    .setMessage(R.string.msg_confirm_delete_history_item)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(
                        R.string.action_delete,
                        (dialog, which) -> runThenReload(() -> repository.deleteHistory(item.id)))
                    .show();
              }
            });

    binding.historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.historyRecyclerView.setAdapter(adapter);

    M3Theme.apply(binding.getRoot());
    applyColors();

    loadHistory();
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

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_history, menu);
    UiUtils.tintToolbar(binding.toolbar);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem clear = menu.findItem(R.id.action_clear_all);
    if (clear != null) {
      clear.setEnabled(!items.isEmpty());
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_clear_all) {
      new DialogCompat(this)
          .setTitle(R.string.dialog_confirm_title)
          .setMessage(R.string.msg_confirm_clear_history)
          .setNegativeButton(R.string.action_cancel, null)
          .setPositiveButton(
              R.string.action_clear_all, (dialog, which) -> runThenReload(repository::clearHistory))
          .show();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void runThenReload(Runnable write) {
    io.execute(
        () -> {
          try {
            write.run();
          } catch (SQLException e) {
            Log.w(TAG, "History update failed", e);
          }
        });
    loadHistory();
  }

  private void loadHistory() {
    io.execute(
        () -> {
          List<HistoryItem> loaded;
          try {
            loaded = repository.getHistory();
          } catch (SQLException e) {
            Log.w(TAG, "History could not be read", e);
            loaded = new ArrayList<>();
          }
          List<HistoryItem> result = loaded;
          runOnUiThread(() -> showHistory(result));
        });
  }

  private void showHistory(List<HistoryItem> loaded) {
    if (isDestroyed()) {
      return;
    }
    items.clear();
    items.addAll(loaded);
    adapter.notifyDataSetChanged();
    binding.emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    binding.historyRecyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    invalidateOptionsMenu();
  }
}
