package ir.hanzodev1375.ghostide.postman.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.listitem.ListItemViewHolder;
import java.util.List;

import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.databinding.ItemCollectionBinding;
import ir.hanzodev1375.ghostide.postman.model.RequestCollection;
import ir.hanzodev1375.ghostide.postman.util.UiUtils;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {

  public interface Listener {
    void onItemClick(RequestCollection collection);

    void onDeleteClick(RequestCollection collection);
  }

  private final List<RequestCollection> items;
  private final Listener listener;

  public CollectionAdapter(List<RequestCollection> items, Listener listener) {
    this.items = items;
    this.listener = listener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ItemCollectionBinding binding =
        ItemCollectionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
    if (LocaleDirection.isRtl(parent.getContext())) {
      LocaleDirection.mirrorRow(binding.rowContent);
      binding.nameText.setGravity(Gravity.RIGHT);
      binding.countText.setGravity(Gravity.RIGHT);
    }
    return new ViewHolder(binding);
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
    private final ItemCollectionBinding binding;

    ViewHolder(ItemCollectionBinding binding) {
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

    void bind(RequestCollection collection) {
      binding.nameText.setText(collection.name);
      int count = collection.requestCount;
      binding.countText.setText(
          binding
              .getRoot()
              .getResources()
              .getQuantityString(R.plurals.postman_request_count, count, count));
      UiUtils.tonalIcon(binding.folderIcon);
      binding.deleteButton.setImageTintList(UiUtils.mutedTint());
    }
  }
}
