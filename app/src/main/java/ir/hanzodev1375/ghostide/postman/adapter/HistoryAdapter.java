package ir.hanzodev1375.ghostide.postman.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.listitem.ListItemViewHolder;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.databinding.ItemHistoryPostmanBinding;
import java.util.List;
import ir.hanzodev1375.ghostide.postman.model.HistoryItem;
import ir.hanzodev1375.ghostide.postman.util.TimeUtils;
import ir.hanzodev1375.ghostide.postman.util.UiUtils;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

  public interface Listener {
    void onItemClick(HistoryItem item);

    void onDeleteClick(HistoryItem item);
  }

  private final List<HistoryItem> items;
  private final Listener listener;

  public HistoryAdapter(List<HistoryItem> items, Listener listener) {
    this.items = items;
    this.listener = listener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ItemHistoryPostmanBinding binding =
        ItemHistoryPostmanBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
    if (LocaleDirection.isRtl(parent.getContext())) {
      LocaleDirection.mirrorRow(binding.rowContent);
      LocaleDirection.mirrorRow(binding.metaRow);
      binding.urlText.setGravity(Gravity.RIGHT);
      binding.metaRow.setLayoutParams(rightAligned(binding.metaRow.getLayoutParams()));
    }
    return new ViewHolder(binding);
  }

  static ViewGroup.LayoutParams rightAligned(ViewGroup.LayoutParams params) {
    if (params instanceof android.widget.LinearLayout.LayoutParams) {
      ((android.widget.LinearLayout.LayoutParams) params).gravity = Gravity.RIGHT;
    }
    return params;
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    M3Theme.listCard(holder.itemView);
    holder.bind(position, getItemCount());
    holder.bind(items.get(position));
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  class ViewHolder extends ListItemViewHolder {
    private final ItemHistoryPostmanBinding binding;

    ViewHolder(ItemHistoryPostmanBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
      binding.rowCard.setOnClickListener(
          v -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onItemClick(items.get(pos));
          });
      binding.deleteButton.setOnClickListener(
          v -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onDeleteClick(items.get(pos));
          });
    }

    void bind(HistoryItem item) {
      android.content.Context context = binding.getRoot().getContext();
      binding.methodBadge.setText(item.method);
      UiUtils.tonalPill(binding.methodBadge, UiUtils.methodColor(context, item.method));

      binding.urlText.setText(item.url);

      if (item.statusCode > 0) {
        binding.statusChip.setText(String.valueOf(item.statusCode));
        UiUtils.tonalPill(binding.statusChip, UiUtils.statusColor(context, item.statusCode));
      } else {
        binding.statusChip.setText(R.string.postman_status_failed);
        UiUtils.tonalPill(binding.statusChip, UiUtils.errorColor(context));
      }

      String meta =
          TimeUtils.formatDuration(item.timeMs)
              + " · "
              + TimeUtils.relativeTime(context, item.timestamp);
      binding.metaText.setText(meta);
      binding.deleteButton.setImageTintList(UiUtils.mutedTint());
    }
  }
}
