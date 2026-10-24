package ir.hanzodev1375.ghostide.mvvm.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import ir.hanzodev1375.ghostide.jgit.GitHubClient;
import ir.hanzodev1375.ghostide.models.Contributor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Loads the repository star count and contributor list for the About screen. */
public class AboutViewModel extends AndroidViewModel {

  private static final String OWNER = "HanzoDev30";
  private static final String REPOSITORY = "Ghostide";

  /** Public web address of the repository whose stars and contributors this model loads. */
  public static final String REPOSITORY_URL = "https://github.com/" + OWNER + "/" + REPOSITORY;

  private final MutableLiveData<Integer> stars = new MutableLiveData<>();
  private final MutableLiveData<List<Contributor>> contributors = new MutableLiveData<>();
  private final GitHubClient gitHubClient;
  private boolean started;

  public AboutViewModel(Application application) {
    super(application);
    gitHubClient = new GitHubClient(application);
  }

  public LiveData<Integer> getStars() {
    return stars;
  }

  public LiveData<List<Contributor>> getContributors() {
    return contributors;
  }

  public void load() {
    if (started) return;
    started = true;
    loadStars();
    loadContributors();
  }

  private void loadStars() {
    gitHubClient.getRepositoryInfo(
        OWNER,
        REPOSITORY,
        new GitHubClient.GitHubRequestCallback() {
          @Override
          public void onSuccess(JSONObject response) {
            stars.postValue(response.optInt("stargazers_count", -1));
          }

          @Override
          public void onFailure(String errorMessage) {
            stars.postValue(-1);
          }
        });
  }

  private void loadContributors() {
    gitHubClient.getContributors(
        OWNER,
        REPOSITORY,
        new GitHubClient.GitHubArrayCallback() {
          @Override
          public void onSuccess(JSONArray response) {
            List<Contributor> result = new ArrayList<>();
            for (int i = 0; i < response.length(); i++) {
              JSONObject item = response.optJSONObject(i);
              if (item == null) continue;
              String username = item.optString("login");
              String avatarUrl = item.optString("avatar_url");
              if (username.isEmpty() || avatarUrl.isEmpty()) continue;
              result.add(new Contributor(username, avatarUrl));
            }
            contributors.postValue(result);
          }

          @Override
          public void onFailure(String errorMessage) {
            contributors.postValue(Collections.emptyList());
          }
        });
  }
}
