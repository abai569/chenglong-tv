/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.ui;

import static androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_GET_TIMELINE;
import static androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.EVENT_AVAILABLE_COMMANDS_CHANGED;
import static androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED;
import static androidx.media3.common.Player.EVENT_MEDIA_CHAPTERS_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY;
import static androidx.media3.common.Player.EVENT_TIMELINE_CHANGED;
import static androidx.media3.common.util.Util.msToUs;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

/** A compact seek view that binds a {@link Player} to a {@link DefaultTimeBar}. */
@UnstableApi
public class PlayerSeekView extends FrameLayout
    implements Player.Listener, TimeBar.OnScrubListener {

  private static final int MAX_UPDATE_INTERVAL_MS = 1000;
  private static final int MIN_UPDATE_INTERVAL_MS = 200;

  private final StringBuilder timeBuilder;
  private final Formatter timeFormatter;
  private final TextView positionView;
  private final TextView durationView;
  @Nullable private final TextView chapterView;
  @Nullable private final TextView chapterSeparatorView;
  private final DefaultTimeBar timeBar;
  private final Runnable updateProgressAction;
  private final Timeline.Period period;
  private final Timeline.Window window;
  private final Rect windowVisibleFrame;
  private final Rect seekVisibleFrame;
  private final int[] timeBarLocation;
  private final int[] rootLocation;
  private final int chapterBubbleMargin;
  private final int chapterBubbleScreenInset;
  private final int chapterBubbleMaxWidth;

  @Nullable private Player player;
  @Nullable private TextView chapterBubbleView;
  @Nullable private ViewGroup chapterBubbleRoot;
  @Nullable private String chapterBubbleText;
  private int chapterBubbleMeasuredMaxWidth;
  private int chapterBubbleWidth;
  private int chapterBubbleHeight;
  private long[] adGroupTimesMs;
  private boolean[] playedAdGroups;
  private long currentDuration;
  private long currentWindowOffset;
  @Nullable private long[] chapterTimesMs;
  @Nullable private String[] chapterLabels;
  private int chapterCount;
  private boolean attached;
  private boolean listening;
  private boolean scrubbing;

  public PlayerSeekView(Context context) {
    this(context, /* attrs= */ null);
  }

  public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, /* defStyleAttr= */ 0);
  }

  public PlayerSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    this(
        context,
        attrs,
        defStyleAttr,
        R.layout.exo_player_seek_view,
        R.id.exo_position,
        R.id.exo_progress,
        R.id.exo_duration);
  }

  protected PlayerSeekView(
      Context context,
      @Nullable AttributeSet attrs,
      int defStyleAttr,
      @LayoutRes int layoutResourceId,
      @IdRes int positionViewId,
      @IdRes int timeBarId,
      @IdRes int durationViewId) {
    super(context, attrs, defStyleAttr);
    LayoutInflater.from(context).inflate(layoutResourceId, this);
    positionView = checkNotNull(findViewById(positionViewId));
    durationView = checkNotNull(findViewById(durationViewId));
    View timeBarView = checkNotNull(findViewById(timeBarId));
    checkArgument(timeBarView instanceof DefaultTimeBar);
    timeBar = (DefaultTimeBar) timeBarView;
    chapterView = findViewById(R.id.exo_chapter);
    chapterSeparatorView = findViewById(R.id.exo_chapter_separator);
    timeBuilder = new StringBuilder();
    timeFormatter = new Formatter(timeBuilder, Locale.getDefault());
    updateProgressAction = this::updateProgress;
    period = new Timeline.Period();
    window = new Timeline.Window();
    windowVisibleFrame = new Rect();
    seekVisibleFrame = new Rect();
    timeBarLocation = new int[2];
    rootLocation = new int[2];
    chapterBubbleMargin = getResources().getDimensionPixelSize(R.dimen.exo_chapter_bubble_margin);
    chapterBubbleScreenInset =
        getResources().getDimensionPixelSize(R.dimen.exo_chapter_bubble_screen_inset);
    chapterBubbleMaxWidth =
        getResources().getDimensionPixelSize(R.dimen.exo_chapter_bubble_max_width);
    adGroupTimesMs = new long[0];
    playedAdGroups = new boolean[0];
    timeBar.addListener(this);
    resetView();
  }

  /** Sets the player whose progress should be shown. */
  public void setPlayer(@Nullable Player player) {
    checkState(Looper.myLooper() == Looper.getMainLooper());
    checkArgument(player == null || player.getApplicationLooper() == Looper.getMainLooper());
    if (this.player == player) {
      return;
    }
    removeCallbacks(updateProgressAction);
    removePlayerListener();
    this.player = player;
    if (player == null) {
      resetView();
    }
    if (attached) {
      addPlayerListener();
      updateTimeline();
    }
  }

  /** Returns the bound {@link TimeBar}. */
  public TimeBar getTimeBar() {
    return timeBar;
  }

  private String getTimeString(long timeMs) {
    return Util.getStringForTime(timeBuilder, timeFormatter, timeMs);
  }

  private void updateTimeline() {
    @Nullable Player player = this.player;
    if (!attached || player == null) {
      return;
    }
    currentWindowOffset = 0;
    long durationUs = 0;
    int adGroupCount = 0;
    Timeline timeline =
        player.isCommandAvailable(COMMAND_GET_TIMELINE)
            ? player.getCurrentTimeline()
            : Timeline.EMPTY;
    if (!timeline.isEmpty()) {
      int currentWindowIndex = player.getCurrentMediaItemIndex();
      timeline.getWindow(currentWindowIndex, window);
      if (window.durationUs != C.TIME_UNSET) {
        adGroupCount = collectAdGroupMarkers(timeline);
        durationUs = window.durationUs;
      }
    } else if (player.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      long durationMs = player.getContentDuration();
      if (durationMs != C.TIME_UNSET) {
        durationUs = msToUs(durationMs);
      }
    }
    updateDuration(Util.usToMs(durationUs), adGroupCount);
    updateProgress();
  }

  private void updateProgress() {
    removeCallbacks(updateProgressAction);
    if (!attached || !isVisible() || player == null) {
      return;
    }
    long position = 0;
    long bufferedPosition = 0;
    if (player.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      position = currentWindowOffset + player.getContentPosition();
      bufferedPosition = currentWindowOffset + player.getContentBufferedPosition();
    }
    if (!scrubbing) {
      positionView.setText(getTimeString(position));
      updateChapterLabel(position);
    }
    timeBar.setPosition(position);
    timeBar.setBufferedPosition(bufferedPosition);

    int playbackState = player.getPlaybackState();
    if (player.isPlaying()) {
      postDelayed(updateProgressAction, getProgressUpdateDelayMs(player, position));
    } else if (playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
      postDelayed(updateProgressAction, MAX_UPDATE_INTERVAL_MS);
    }
  }

  private void resetView() {
    positionView.setText(getTimeString(0));
    durationView.setText(getTimeString(0));
    currentWindowOffset = 0;
    timeBar.setPosition(0);
    timeBar.setDuration(currentDuration = 0);
    timeBar.setBufferedPosition(0);
    timeBar.setAdGroupTimesMs(null, null, 0);
    clearChapters();
  }

  private void updateChapters() {
    if (player == null) {
      clearChapters();
    } else {
      setMediaChapters(player.getCurrentMediaChapters());
    }
  }

  private void clearChapters() {
    chapterCount = 0;
    chapterTimesMs = null;
    chapterLabels = null;
    timeBar.setChapterTimesMs(null, 0);
    hideChapterBubble();
    updateChapterLabel(C.TIME_UNSET);
  }

  private void setMediaChapters(List<MediaChapter> chapters) {
    if (chapters.isEmpty() || currentDuration <= 0) {
      clearChapters();
      return;
    }
    long[] chapterTimesMs = new long[chapters.size()];
    String[] chapterLabels = new String[chapters.size()];
    boolean hasChapterLabels = false;
    int chapterCount = 0;
    for (int i = 0; i < chapters.size(); i++) {
      MediaChapter chapter = chapters.get(i);
      if (chapter.timeUs == C.TIME_UNSET) {
        continue;
      }
      long chapterTimeMs = currentWindowOffset + Util.usToMs(chapter.timeUs);
      if (chapterTimeMs < 0 || chapterTimeMs >= currentDuration) {
        continue;
      }
      chapterTimesMs[chapterCount] = chapterTimeMs;
      String chapterLabel = chapter.label.trim();
      chapterLabels[chapterCount] = chapterLabel;
      hasChapterLabels |= !chapterLabel.isEmpty();
      chapterCount++;
    }
    this.chapterCount = chapterCount;
    this.chapterTimesMs = chapterCount == 0 ? null : Arrays.copyOf(chapterTimesMs, chapterCount);
    this.chapterLabels =
        chapterCount == 0 || !hasChapterLabels ? null : Arrays.copyOf(chapterLabels, chapterCount);
    timeBar.setChapterTimesMs(this.chapterTimesMs, chapterCount);
  }

  private void updateChapterLabel(long positionMs) {
    updateChapterLabel(getChapterLabel(positionMs));
  }

  private void updateChapterLabel(@Nullable String chapterLabel) {
    if (chapterView == null) {
      setChapterSeparatorVisible(false);
      return;
    }
    if (chapterLabel == null) {
      chapterView.setText(null);
      chapterView.setVisibility(GONE);
      setChapterSeparatorVisible(false);
    } else {
      chapterView.setText(chapterLabel);
      chapterView.setVisibility(VISIBLE);
      setChapterSeparatorVisible(true);
    }
  }

  private void setChapterSeparatorVisible(boolean visible) {
    if (chapterSeparatorView != null) {
      chapterSeparatorView.setVisibility(visible ? VISIBLE : GONE);
    }
  }

  private @Nullable String getChapterLabel(long positionMs) {
    if (positionMs == C.TIME_UNSET || chapterCount == 0 || chapterTimesMs == null) {
      return null;
    }
    int chapterIndex = C.INDEX_UNSET;
    long chapterTimeMs = Long.MIN_VALUE;
    for (int i = 0; i < chapterCount; i++) {
      long timeMs = chapterTimesMs[i];
      if (timeMs != C.TIME_UNSET && timeMs <= positionMs && timeMs >= chapterTimeMs) {
        chapterIndex = i;
        chapterTimeMs = timeMs;
      }
    }
    if (chapterIndex == C.INDEX_UNSET || chapterLabels == null) {
      return null;
    }
    String chapterLabel = chapterLabels[chapterIndex];
    return chapterLabel.isEmpty() ? null : chapterLabel;
  }

  private void updateScrubViews(long positionMs) {
    @Nullable String chapterLabel = getChapterLabel(positionMs);
    positionView.setText(getTimeString(positionMs));
    updateChapterLabel(chapterLabel);
    if (chapterLabel == null) {
      hideChapterBubble();
    } else {
      showChapterBubble(chapterLabel, positionMs);
    }
  }

  private void showChapterBubble(String chapterLabel, long positionMs) {
    if (!attached || !isShown()) {
      hideChapterBubble();
      return;
    }
    ViewGroup rootView = getChapterBubbleRoot();
    TextView bubbleView = getChapterBubbleView();
    Rect horizontalFrame = getChapterBubbleHorizontalFrame();
    updateChapterBubbleSizeIfNeeded(bubbleView, chapterLabel, horizontalFrame);
    timeBar.getLocationOnScreen(timeBarLocation);
    rootView.getLocationOnScreen(rootLocation);
    int left =
        getChapterBubbleLeft(horizontalFrame, positionMs, chapterBubbleWidth) - rootLocation[0];
    int top = getChapterBubbleTop(rootView, chapterBubbleHeight) - rootLocation[1];
    showOrMoveChapterBubble(rootView, bubbleView, left, top);
  }

  private void updateChapterBubbleSizeIfNeeded(
      TextView bubbleView, String chapterLabel, Rect horizontalFrame) {
    int maxWidth =
        Math.min(
            chapterBubbleMaxWidth,
            Math.max(1, horizontalFrame.width() - chapterBubbleScreenInset * 2));
    if (chapterLabel.equals(chapterBubbleText) && chapterBubbleMeasuredMaxWidth == maxWidth) {
      return;
    }
    bubbleView.setMaxWidth(maxWidth);
    bubbleView.setText(chapterLabel);
    bubbleView.measure(
        MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    chapterBubbleText = chapterLabel;
    chapterBubbleMeasuredMaxWidth = maxWidth;
    chapterBubbleWidth = bubbleView.getMeasuredWidth();
    chapterBubbleHeight = bubbleView.getMeasuredHeight();
  }

  private int getChapterBubbleLeft(Rect horizontalFrame, long positionMs, int bubbleWidth) {
    int anchorX = timeBarLocation[0] + timeBar.getPositionXInView(positionMs);
    int minX = horizontalFrame.left + chapterBubbleScreenInset;
    int maxX = horizontalFrame.right - chapterBubbleScreenInset - bubbleWidth;
    return Util.constrainValue(anchorX - bubbleWidth / 2, minX, Math.max(minX, maxX));
  }

  private int getChapterBubbleTop(View rootView, int bubbleHeight) {
    rootView.getGlobalVisibleRect(windowVisibleFrame);
    return Math.max(
        windowVisibleFrame.top + chapterBubbleScreenInset,
        timeBarLocation[1]
            + timeBar.getProgressBarTopInView()
            - bubbleHeight
            - chapterBubbleMargin);
  }

  private void showOrMoveChapterBubble(ViewGroup rootView, TextView bubbleView, int left, int top) {
    if (chapterBubbleRoot != rootView) {
      hideChapterBubble();
      chapterBubbleRoot = rootView;
      rootView.getOverlay().add(bubbleView);
    }
    bubbleView.layout(left, top, left + chapterBubbleWidth, top + chapterBubbleHeight);
  }

  private Rect getChapterBubbleHorizontalFrame() {
    getGlobalVisibleRect(seekVisibleFrame);
    return seekVisibleFrame;
  }

  private ViewGroup getChapterBubbleRoot() {
    View rootView = getRootView();
    return rootView instanceof ViewGroup ? (ViewGroup) rootView : this;
  }

  private TextView getChapterBubbleView() {
    if (chapterBubbleView == null) {
      chapterBubbleView =
          (TextView)
              LayoutInflater.from(getContext()).inflate(R.layout.exo_chapter_bubble, this, false);
    }
    return chapterBubbleView;
  }

  private void hideChapterBubble() {
    if (chapterBubbleRoot != null) {
      chapterBubbleRoot.getOverlay().remove(checkNotNull(chapterBubbleView));
      chapterBubbleRoot = null;
    }
  }

  private void updateDuration(long durationMs, int adGroupCount) {
    currentDuration = durationMs;
    timeBar.setDuration(durationMs);
    timeBar.setAdGroupTimesMs(adGroupTimesMs, playedAdGroups, adGroupCount);
    durationView.setText(getTimeString(durationMs));
    updateChapters();
  }

  private void seekToTimeBarPosition(long positionMs) {
    if (player == null) {
      return;
    }
    if (player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
      player.seekTo(positionMs);
    }
    updateProgress();
  }

  private void addPlayerListener() {
    if (player == null || listening) {
      return;
    }
    player.addListener(this);
    listening = true;
  }

  private void removePlayerListener() {
    if (player == null || !listening) {
      return;
    }
    player.removeListener(this);
    listening = false;
  }

  private boolean isVisible() {
    return getVisibility() == VISIBLE;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    attached = true;
    addPlayerListener();
    updateTimeline();
  }

  @Override
  protected void onDetachedFromWindow() {
    attached = false;
    scrubbing = false;
    hideChapterBubble();
    removePlayerListener();
    removeCallbacks(updateProgressAction);
    super.onDetachedFromWindow();
  }

  @Override
  protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (visibility != VISIBLE) {
      hideChapterBubble();
    }
  }

  @Override
  public void onEvents(Player player, Events events) {
    boolean timelineChanged =
        events.containsAny(
            EVENT_POSITION_DISCONTINUITY,
            EVENT_TIMELINE_CHANGED,
            EVENT_MEDIA_CHAPTERS_CHANGED,
            EVENT_AVAILABLE_COMMANDS_CHANGED);
    if (timelineChanged) {
      updateTimeline();
    } else if (events.containsAny(
        EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_IS_PLAYING_CHANGED)) {
      updateProgress();
    }
  }

  @Override
  public void onScrubStart(TimeBar timeBar, long position) {
    scrubbing = true;
    updateScrubViews(position);
  }

  @Override
  public void onScrubMove(TimeBar timeBar, long position) {
    updateScrubViews(position);
  }

  @Override
  public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
    scrubbing = false;
    hideChapterBubble();
    if (!canceled) {
      seekToTimeBarPosition(position);
    } else {
      updateProgress();
    }
  }

  private long getProgressUpdateDelayMs(Player player, long positionMs) {
    float speed = player.getPlaybackParameters().speed;
    long mediaTimeDelayMs = Math.min(timeBar.getPreferredUpdateDelay(), 1000 - positionMs % 1000);
    return Util.constrainValue(
        speed > 0 ? (long) (mediaTimeDelayMs / speed) : MAX_UPDATE_INTERVAL_MS,
        MIN_UPDATE_INTERVAL_MS,
        MAX_UPDATE_INTERVAL_MS);
  }

  private int collectAdGroupMarkers(Timeline timeline) {
    int adGroupCount = 0;
    for (int i = window.firstPeriodIndex; i <= window.lastPeriodIndex; i++) {
      timeline.getPeriod(i, period);
      int removedGroups = period.getRemovedAdGroupCount();
      int totalGroups = period.getAdGroupCount();
      for (int adGroupIndex = removedGroups; adGroupIndex < totalGroups; adGroupIndex++) {
        long adGroupTimeInPeriodUs = period.getAdGroupTimeUs(adGroupIndex);
        if (adGroupTimeInPeriodUs == C.TIME_END_OF_SOURCE) {
          if (period.durationUs == C.TIME_UNSET) {
            continue;
          }
          adGroupTimeInPeriodUs = period.durationUs;
        }
        long adGroupTimeInWindowUs = adGroupTimeInPeriodUs + period.getPositionInWindowUs();
        if (adGroupTimeInWindowUs < 0) {
          continue;
        }
        if (adGroupCount == adGroupTimesMs.length) {
          int newLength = adGroupTimesMs.length == 0 ? 1 : adGroupTimesMs.length * 2;
          adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, newLength);
          playedAdGroups = Arrays.copyOf(playedAdGroups, newLength);
        }
        adGroupTimesMs[adGroupCount] = Util.usToMs(adGroupTimeInWindowUs);
        playedAdGroups[adGroupCount] = period.hasPlayedAdGroup(adGroupIndex);
        adGroupCount++;
      }
    }
    return adGroupCount;
  }
}
