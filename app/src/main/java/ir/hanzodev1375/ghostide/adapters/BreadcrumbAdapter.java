package ir.hanzodev1375.ghostide.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.ghostide.codeeditors.langs.lsp.model.BreadcrumbItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ir.hanzodev1375.ghostide.R;
import ir.theme.M3Theme;

public class BreadcrumbAdapter extends RecyclerView.Adapter<BreadcrumbAdapter.ViewHolder> {

  public interface OnItemClickListener {
    void onBreadcrumbClick(BreadcrumbItem item);
  }

  private final List<BreadcrumbItem> items = new ArrayList<>();
  private OnItemClickListener listener;

  public void setOnItemClickListener(OnItemClickListener listener) {
    this.listener = listener;
  }

  /**
   * Replaces the shown path. The editor re-publishes the path on every LSP status poll, so an
   * identical path is ignored instead of rebinding (and re-theming) the whole bar each time.
   */
  public void setItems(List<BreadcrumbItem> newItems) {
    if (sameItems(newItems)) return;
    items.clear();
    if (newItems != null) items.addAll(newItems);
    notifyDataSetChanged();
  }

  private boolean sameItems(List<BreadcrumbItem> newItems) {
    int newSize = newItems != null ? newItems.size() : 0;
    if (newSize != items.size()) return false;
    for (int i = 0; i < newSize; i++) {
      BreadcrumbItem a = items.get(i);
      BreadcrumbItem b = newItems.get(i);
      if (a == b) continue;
      if (a == null
          || b == null
          || a.getLine() != b.getLine()
          || a.getColumn() != b.getColumn()
          || a.getKind() != b.getKind()
          || !Objects.equals(a.getName(), b.getName())) {
        return false;
      }
    }
    return true;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_breadcrumb, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    var item = items.get(position);
    holder.name.setText(item.getName());
    holder.separator.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
    holder.itemView.setOnClickListener(
        v -> {
          if (listener != null) listener.onBreadcrumbClick(item);
        });
    M3Theme.listCard(holder.itemView);
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    final TextView name;
    final TextView separator;

    ViewHolder(@NonNull View itemView) {
      super(itemView);
      name = itemView.findViewById(R.id.tvBreadcrumbName);
      separator = itemView.findViewById(R.id.tvBreadcrumbSeparator);
    }
  }
}
