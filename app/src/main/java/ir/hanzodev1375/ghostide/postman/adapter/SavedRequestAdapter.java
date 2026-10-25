package ir.hanzodev1375.ghostide.postman.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.listitem.ListItemViewHolder;
import java.util.List;

import ir.hanzodev1375.ghostide.databinding.ItemSavedRequestBinding;
import ir.hanzodev1375.ghostide.postman.model.SavedRequest;
import ir.hanzodev1375.ghostide.postman.util.UiUtils;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.theme.M3Theme;

public class SavedRequestAdapter extends RecyclerView.Adapter<SavedRequestAdapter.ViewHolder> {

  public interface Listener {
    void onItemClick(SavedRequest request);

    void onDeleteClick(SavedRequest request);
  }

  private final List<SavedRequest> items;
  private final Listener listener;

  public SavedRequestAdapter(List<SavedRequest> items, Listener listener) {
    this.items = items;
    this.listener = listener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ItemSavedRequestBinding binding =
        ItemSavedRequestBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
    if (LocaleDirection.isRtl(parent.getContext())) {
      LocaleDirection.mirrorRow(binding.rowContent);
      binding.nameText.setGravity(Gravity.RIGHT);
      binding.urlText.setGravity(Gravity.RIGHT);
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
    private final ItemSavedRequestBinding binding;

    ViewHolder(ItemSavedRequestBinding binding) {
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

    void bind(SavedRequest request) {
      binding.methodBadge.setText(request.method);
      UiUtils.tonalPill(
          binding.methodBadge, UiUtils.methodColor(binding.getRoot().getContext(), request.method));
      binding.nameText.setText(request.name);
      binding.urlText.setText(request.url);
      binding.deleteButton.setImageTintList(UiUtils.mutedTint());
    }
  }
}
