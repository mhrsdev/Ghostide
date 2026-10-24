package ir.hanzodev1375.ghostide.adapters;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import ir.hanzodev1375.ghostide.customui.PreferenceSwitchGroup;
import ir.hanzodev1375.ghostide.models.SettingItem;
import ir.theme.M3Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

  private final List<SettingItem> items;
  private final List<SettingItem> backupList;
  private OnItemClickListener listener;
  private String currentQuery = "";

  public interface OnItemClickListener {
    void onItemClick(int position);
  }

  public SettingsAdapter(List<SettingItem> items) {
    this.items = new ArrayList<>(items);
    this.backupList = new ArrayList<>(items);
  }

  public void setOnItemClickListener(OnItemClickListener listener) {
    this.listener = listener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    PreferenceSwitchGroup view = new PreferenceSwitchGroup(parent.getContext());
    ViewGroup.MarginLayoutParams layoutParams =
        new ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    layoutParams.bottomMargin =
        (int) (4 * parent.getResources().getDisplayMetrics().density);
    view.setLayoutParams(layoutParams);
    return new ViewHolder(view);
  }

  public void updateItem(int originalPosition, SettingItem newItem) {
    if (newItem == null) return;
    if (originalPosition < 0 || originalPosition >= backupList.size()) return;
    SettingItem oldItem = backupList.get(originalPosition);
    backupList.set(originalPosition, newItem);
    int visibleIndex = items.indexOf(oldItem);
    if (visibleIndex >= 0) {
      items.set(visibleIndex, newItem);
      notifyItemChanged(visibleIndex);
    }
  }

  public SettingItem getItemAtPosition(int originalPosition) {
    if (originalPosition < 0 || originalPosition >= backupList.size()) return null;
    return backupList.get(originalPosition);
  }

  public void notifyItemChangedByOriginalPosition(int originalPosition) {
    if (originalPosition < 0 || originalPosition >= backupList.size()) return;
    SettingItem item = backupList.get(originalPosition);
    int visibleIndex = items.indexOf(item);
    if (visibleIndex >= 0) {
      notifyItemChanged(visibleIndex);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    SettingItem item = items.get(position);
    holder.switchGroup.setListPosition(position, getItemCount());

    if (currentQuery != null && !currentQuery.isEmpty()) {
      holder.switchGroup.setTitle(highlightText(item.getTitle(), currentQuery));
      if (item.getDescription() != null && !item.getDescription().isEmpty()) {
        holder.switchGroup.setDescription(highlightText(item.getDescription(), currentQuery));
      } else {
        holder.switchGroup.setDescription("");
      }
    } else {
      holder.switchGroup.setTitle(item.getTitle());
      if (item.getDescription() != null && !item.getDescription().isEmpty()) {
        holder.switchGroup.setDescription(item.getDescription());
      } else {
        holder.switchGroup.setDescription("");
      }
    }

    if (item.getIconRes() != 0) {
      holder.switchGroup.setIcon(item.getIconRes());
    }
    holder.switchGroup.getSwitch().setOnCheckedChangeListener(null);
    holder.switchGroup.setValue(item.isChecked());
    if (item.getListener() != null) {
      holder.switchGroup.getSwitch().setVisibility(View.VISIBLE);
      holder.switchGroup.setSwitchChangedListener(
          (button, isChecked) -> {
            item.setChecked(isChecked);
            item.getListener().onCheckedChanged(isChecked);
          });
      holder.switchGroup.setOnClickListener(holder.switchGroup);
    } else {
      holder.switchGroup.getSwitch().setVisibility(View.GONE);
      holder.switchGroup.setOnClickListener(
          v -> {
            if (listener != null) {
              int originalPosition = backupList.indexOf(item);
              listener.onItemClick(originalPosition);
            }
          });
    }
  }

  private SpannableString highlightText(String text, String query) {
    if (text == null) return new SpannableString("");
    SpannableString spannableString = new SpannableString(text);
    if (query == null || query.isEmpty()) {
      return spannableString;
    }
    String lowerText = text.toLowerCase(Locale.getDefault());
    String lowerQuery = query.toLowerCase(Locale.getDefault());
    int startIndex = lowerText.indexOf(lowerQuery);

    if (startIndex == -1) {
      return spannableString;
    }

    int highlightColor = fallback(M3Theme.error(), 0);
    while (startIndex != -1) {
      int endIndex = startIndex + lowerQuery.length();
      spannableString.setSpan(
          new ForegroundColorSpan(highlightColor),
          startIndex,
          endIndex,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      startIndex = lowerText.indexOf(lowerQuery, endIndex);
    }
    return spannableString;
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  public void resetToFull() {
    filter("");
  }

  /**
   * Replaces the row list and clears any active filter.
   */
  public void setItems(List<SettingItem> newItems) {
    items.clear();
    items.addAll(newItems);
    backupList.clear();
    backupList.addAll(newItems);
    currentQuery = "";
    notifyDataSetChanged();
  }

  public void filter(String query) {
    Locale locale = Locale.getDefault();
    String newQuery = query != null ? query.toLowerCase(locale).trim() : "";
    boolean sameQuery = newQuery.equals(currentQuery);
    currentQuery = newQuery;

    List<SettingItem> next;
    if (newQuery.isEmpty()) {
      next = backupList;
    } else {
      next = new ArrayList<>();
      for (SettingItem item : backupList) {
        if (item.getTitle().toLowerCase(locale).contains(newQuery)
            || (item.getDescription() != null
                && item.getDescription().toLowerCase(locale).contains(newQuery))) {
          next.add(item);
        }
      }
    }
    if (items.equals(next) && (sameQuery || next.isEmpty())) {
      return;
    }
    items.clear();
    items.addAll(next);
    notifyDataSetChanged();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    PreferenceSwitchGroup switchGroup;

    ViewHolder(PreferenceSwitchGroup itemView) {
      super(itemView);
      switchGroup = itemView;
    }
  }

  private static int fallback(Integer value, int def) {
    return value != null ? value : def;
  }
}
