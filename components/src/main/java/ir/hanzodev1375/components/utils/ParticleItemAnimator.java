package ir.hanzodev1375.components.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.components.effect.ParticleSmasher;
import ir.hanzodev1375.components.effect.SmashAnimator;
import ir.hanzodev1375.components.effect.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link DefaultItemAnimator} that shatters removed rows into particles.
 *
 * <p>The particle effect shrinks and fades the row's own view to nothing. Because RecyclerView
 * recycles that view, every finished or interrupted particle removal restores the view's alpha,
 * scale and translation before it is handed back; otherwise a later bind would show an invisible
 * row. Removals can be switched to the plain fade with {@link #setParticleRemovals(boolean)}, e.g.
 * while rows only disappear because a search filter hides them.
 */
public class ParticleItemAnimator extends DefaultItemAnimator {

  private final Activity activity;
  private final Map<RecyclerView.ViewHolder, SmashAnimator> runningAnimations = new HashMap<>();
  private boolean particleRemovals = true;

  public ParticleItemAnimator(Activity activity) {
    this.activity = activity;
  }

  /**
   * Chooses how removals that are scheduled from now on are animated.
   *
   * @param enabled true for the particle effect, false for the default fade-out
   */
  public void setParticleRemovals(boolean enabled) {
    particleRemovals = enabled;
  }

  @Override
  public boolean animateRemove(RecyclerView.ViewHolder holder) {
    if (!particleRemovals
        || runningAnimations.containsKey(holder)
        || holder.itemView.getWidth() <= 0
        || holder.itemView.getHeight() <= 0) {
      return super.animateRemove(holder);
    }

    View itemView = holder.itemView;
    ParticleSmasher smasher = new ParticleSmasher(activity);

    SmashAnimator animator =
        smasher
            .with(itemView)
            .setStyle(SmashAnimator.STYLE_EXPLOSION)
            .setDuration(800)
            .setStartDelay(100)
            .setParticleRadius(Utils.dp2Px(3))
            .addAnimatorListener(
                new SmashAnimator.OnAnimatorListener() {
                  @Override
                  public void onAnimatorEnd() {
                    finishSmash(holder);
                    if (smasher.getParent() != null) {
                      ((ViewGroup) smasher.getParent()).removeView(smasher);
                    }
                  }
                });

    runningAnimations.put(holder, animator);
    animator.start();
    return true;
  }

  @Override
  public boolean animateAdd(RecyclerView.ViewHolder holder) {
    restore(holder.itemView);
    dispatchAddStarting(holder);
    dispatchAddFinished(holder);
    return true;
  }

  @Override
  public void endAnimation(RecyclerView.ViewHolder item) {
    finishSmash(item);
    super.endAnimation(item);
  }

  @Override
  public void endAnimations() {
    List<RecyclerView.ViewHolder> running = new ArrayList<>(runningAnimations.keySet());
    for (RecyclerView.ViewHolder holder : running) {
      finishSmash(holder);
    }
    super.endAnimations();
  }

  @Override
  public boolean isRunning() {
    return !runningAnimations.isEmpty() || super.isRunning();
  }

  /**
   * Ends the particle removal of {@code holder} once: restores its view and reports the removal as
   * finished. Later calls for the same holder, e.g. the effect's own end callback after {@link
   * #endAnimation}, do nothing.
   */
  private void finishSmash(RecyclerView.ViewHolder holder) {
    if (runningAnimations.remove(holder) == null) {
      return;
    }
    restore(holder.itemView);
    dispatchRemoveFinished(holder);
    if (!isRunning()) {
      dispatchAnimationsFinished();
    }
  }

  private static void restore(View view) {
    view.animate().cancel();
    view.setAlpha(1f);
    view.setScaleX(1f);
    view.setScaleY(1f);
    view.setTranslationX(0f);
    view.setTranslationY(0f);
  }
}
