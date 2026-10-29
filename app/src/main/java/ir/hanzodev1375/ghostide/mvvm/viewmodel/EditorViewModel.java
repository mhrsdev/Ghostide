package ir.hanzodev1375.ghostide.mvvm.viewmodel;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import java.io.File;
import ir.hanzodev1375.ghostide.utils.FileUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;

public class EditorViewModel extends AndroidViewModel {
  private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();
  private static final Map<String, Long> LATEST_SAVE = new ConcurrentHashMap<>();
  private static final AtomicLong SAVE_SEQUENCE = new AtomicLong();
  private MutableLiveData<Content> text = new MutableLiveData<>();
  private MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
  private MutableLiveData<String> currentPath = new MutableLiveData<>();

  public EditorViewModel(Application app) {
    super(app);
  }

  public LiveData<Content> getText() {
    return text;
  }

  public LiveData<Boolean> getLoading() {
    return loading;
  }

  public LiveData<String> getCurrentPath() {
    return currentPath;
  }

  public void loadFile(String filePath) {
    loading.setValue(true);
    currentPath.setValue(filePath);

    new Thread(
            () -> {
              try {
                File file = new File(filePath);
                if (!file.exists()) {

                  Content emptyContent = new Content();
                  text.postValue(emptyContent);
                  loading.postValue(false);
                  return;
                }

                try (FileInputStream fis = new FileInputStream(file)) {
                  Content content = ContentIO.createFrom(fis);
                  text.postValue(content);
                }
              } catch (Exception e) {
                e.printStackTrace();
                text.postValue(null);
              } finally {
                loading.postValue(false);
              }
            })
        .start();
  }

  public void saveFile(String textContent) {
    String path = currentPath.getValue();
    if (path == null || path.isEmpty()) {
      Log.e("EditorViewModel", "مسیر فایل وجود ندارد");
      return;
    }
    saveFile(path, textContent, null);
  }

  public void saveFile(String filePath, String textContent) {
    saveFile(filePath, textContent, null);
  }

  public void saveFile(String filePath, String textContent, @Nullable Runnable onSaved) {
    saveFile(filePath, textContent, onSaved, null);
  }

  /**
   * Writes {@code textContent} to {@code filePath} as UTF-8 on a background thread.
   *
   * <p>The file is replaced atomically through {@link FileUtil#writeAtomically}, so a failed save
   * keeps the previous content on disk. Saves run one after another on a single thread; when a
   * newer save of the same file is already queued, an older one is skipped without calling its
   * callbacks, because only the newest content should reach the disk.
   *
   * @param onSaved runs on the main thread only after the content was written.
   * @param onFailed runs on the main thread with the cause when the content could not be written.
   */
  public void saveFile(
      String filePath,
      String textContent,
      @Nullable Runnable onSaved,
      @Nullable Consumer<IOException> onFailed) {
    if (filePath == null || filePath.isEmpty()) {
      Log.e("EditorViewModel", "مسیر فایل وجود ندارد");
      return;
    }
    if (textContent == null) {
      Log.e("EditorViewModel", "محتوای فایل نال است: " + filePath);
      return;
    }
    currentPath.setValue(filePath);
    Handler mainHandler = new Handler(Looper.getMainLooper());
    long ticket = SAVE_SEQUENCE.incrementAndGet();
    LATEST_SAVE.put(filePath, ticket);
    SAVE_EXECUTOR.execute(
        () -> {
          Long latest = LATEST_SAVE.get(filePath);
          if (latest != null && latest != ticket) {
            return;
          }
          try {
            byte[] bytes = textContent.getBytes(StandardCharsets.UTF_8);
            FileUtil.writeAtomically(new File(filePath), out -> out.write(bytes));
            Log.d("EditorViewModel", "فایل ذخیره شد: " + filePath);
            IdeEvents.post(FileEvent.saved(filePath));
            if (onSaved != null) {
              mainHandler.post(onSaved);
            }
          } catch (IOException e) {
            Log.e("EditorViewModel", "خطا در ذخیره فایل: " + filePath, e);
            if (onFailed != null) {
              mainHandler.post(() -> onFailed.accept(e));
            }
          } finally {
            LATEST_SAVE.remove(filePath, ticket);
          }
        });
  }
}
