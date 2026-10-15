/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.source.preload;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.RenderersFactory;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinates disk preloading for the currently playing {@link MediaItem}.
 *
 * <p>The manager writes ahead into a shared {@link Cache}. It does not use Media3 memory preload
 * sources, so the active player buffer remains governed by the player's {@code LoadControl}.
 */
@UnstableApi
public final class DiskPreloadManager implements Player.Listener {

  private static final long MIN_RESTART_STEP_MS = 5_000;
  private static final long MAX_RESTART_STEP_MS = 30_000;
  private static final long MAX_BUFFER_OVERLAP_MS = 5_000;
  private static final long CHECK_INTERVAL_MS = 1_000;

  /** Preload options for a single media item. */
  public static final class Options {

    /** Disabled options. */
    public static final Options DISABLED = new Builder().setEnabled(false).build();

    /** Default enabled options. */
    public static final Options DEFAULT = new Builder().build();

    public final boolean enabled;
    public final long durationMs;
    public final int maxThreads;

    private Options(Builder builder) {
      this.enabled = builder.enabled;
      this.durationMs = builder.durationMs;
      this.maxThreads = builder.maxThreads;
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() {
      return new Builder();
    }

    /** Returns a {@link Builder} initialized with this instance's values. */
    public Builder buildUpon() {
      return new Builder().setEnabled(enabled).setDurationMs(durationMs).setMaxThreads(maxThreads);
    }

    /** A builder for {@link Options} instances. */
    public static final class Builder {

      private boolean enabled;
      private long durationMs;
      private int maxThreads;

      private Builder() {
        enabled = true;
        durationMs = 30_000;
        maxThreads = 1;
      }

      /** Sets whether disk preloading is enabled. */
      @CanIgnoreReturnValue
      public Builder setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
      }

      /** Sets how much media time should be kept preloaded ahead on disk. */
      @CanIgnoreReturnValue
      public Builder setDurationMs(long durationMs) {
        checkArgument(durationMs > 0);
        this.durationMs = durationMs;
        return this;
      }

      /** Sets the maximum number of download threads. */
      @CanIgnoreReturnValue
      public Builder setMaxThreads(int maxThreads) {
        checkArgument(maxThreads > 0);
        this.maxThreads = maxThreads;
        return this;
      }

      /** Builds the options. */
      public Options build() {
        return new Options(this);
      }
    }
  }

  /** A builder for {@link DiskPreloadManager} instances. */
  public static final class Builder {

    private final Cache cache;
    private final RenderersFactory renderersFactory;
    private final DataSource.Factory upstreamDataSourceFactory;

    private TrackSelectionParameters trackSelectionParameters;
    @Nullable private PriorityTaskManager priorityTaskManager;
    @Nullable private PreCacheHelper.Listener listener;
    @Nullable private Looper preloadLooper;

    /**
     * Creates a builder.
     *
     * @param cache The cache into which media will be preloaded.
     * @param upstreamDataSourceFactory The upstream data source factory for cache misses.
     * @param renderersFactory The renderers factory used for track selection.
     */
    public Builder(
        Cache cache,
        DataSource.Factory upstreamDataSourceFactory,
        RenderersFactory renderersFactory) {
      this.cache = cache;
      this.upstreamDataSourceFactory = upstreamDataSourceFactory;
      this.renderersFactory = renderersFactory;
      this.trackSelectionParameters = TrackSelectionParameters.DEFAULT;
    }

    /** Sets the {@link TrackSelectionParameters} used for adaptive streams. */
    @CanIgnoreReturnValue
    public Builder setTrackSelectionParameters(TrackSelectionParameters trackSelectionParameters) {
      this.trackSelectionParameters = trackSelectionParameters;
      return this;
    }

    /**
     * Sets the {@link PriorityTaskManager} used by preload downloads.
     *
     * <p>Set the same manager on the player to give playback loading priority over background disk
     * preloading.
     */
    @CanIgnoreReturnValue
    public Builder setPriorityTaskManager(PriorityTaskManager priorityTaskManager) {
      this.priorityTaskManager = priorityTaskManager;
      return this;
    }

    /** Sets the looper used to operate the preloading flow. */
    @CanIgnoreReturnValue
    public Builder setPreloadLooper(Looper preloadLooper) {
      this.preloadLooper = preloadLooper;
      return this;
    }

    /** Sets an optional listener for the underlying {@link PreCacheHelper}. */
    @CanIgnoreReturnValue
    public Builder setListener(@Nullable PreCacheHelper.Listener listener) {
      this.listener = listener;
      return this;
    }

    /** Builds the manager. */
    public DiskPreloadManager build() {
      return new DiskPreloadManager(this);
    }
  }

  private final Cache cache;
  private final Looper preloadLooper;
  private final Runnable checkRunnable;
  private final RenderersFactory renderersFactory;
  private final DataSource.Factory upstreamDataSourceFactory;
  private final TrackSelectionParameters trackSelectionParameters;

  @Nullable private final HandlerThread ownedPreloadThread;
  @Nullable private final PreCacheHelper.Listener listener;
  @Nullable private final PriorityTaskManager priorityTaskManager;

  @Nullable private Player player;
  @Nullable private MediaItem mediaItem;
  @Nullable private Handler playerHandler;
  @Nullable private PreCacheHelper preCacheHelper;
  @Nullable private ExecutorService downloadExecutor;

  private Options options;
  private long lastPreloadStartPositionMs;
  private boolean released;

  private DiskPreloadManager(Builder builder) {
    cache = builder.cache;
    upstreamDataSourceFactory = builder.upstreamDataSourceFactory;
    renderersFactory = builder.renderersFactory;
    trackSelectionParameters = builder.trackSelectionParameters;
    priorityTaskManager = builder.priorityTaskManager;
    if (builder.preloadLooper != null) {
      preloadLooper = builder.preloadLooper;
      ownedPreloadThread = null;
    } else {
      HandlerThread preloadThread = new HandlerThread("Media3:DiskPreload");
      preloadThread.start();
      preloadLooper = preloadThread.getLooper();
      ownedPreloadThread = preloadThread;
    }
    listener = builder.listener;
    checkRunnable = this::maybeStartPreload;
    options = Options.DISABLED;
    lastPreloadStartPositionMs = C.TIME_UNSET;
  }

  /**
   * Starts coordinating disk preloading for {@code mediaItem}.
   *
   * <p>To prioritize playback loading over disk preloading, set the same {@link
   * PriorityTaskManager} on the player and this manager.
   */
  public void start(Player player, MediaItem mediaItem, Options options) {
    checkState(!released);
    stop();
    this.player = player;
    this.mediaItem = mediaItem;
    this.options = options;
    lastPreloadStartPositionMs = C.TIME_UNSET;
    if (!options.enabled || !canPreload(mediaItem)) {
      return;
    }
    player.addListener(this);
    playerHandler = Util.createHandler(player.getApplicationLooper(), /* callback= */ null);
    downloadExecutor = createDownloadExecutor(options.maxThreads);
    preCacheHelper =
        new PreCacheHelper.Factory(
                cache, upstreamDataSourceFactory, renderersFactory, preloadLooper)
            .setTrackSelectionParameters(trackSelectionParameters)
            .setDownloadExecutor(downloadExecutor)
            .setUpstreamPriorityTaskManager(priorityTaskManager)
            .setProgressiveParallelDownloadCount(options.maxThreads)
            .setListener(createPreCacheListener())
            .create(mediaItem);
    scheduleCheck(/* delayMs= */ 0);
  }

  /** Stops current disk preloading. Cached bytes already written are kept. */
  public void stop() {
    @Nullable Handler playerHandler = this.playerHandler;
    if (playerHandler != null) {
      playerHandler.removeCallbacks(checkRunnable);
    }
    @Nullable Player player = this.player;
    if (player != null) {
      player.removeListener(this);
    }
    @Nullable PreCacheHelper preCacheHelper = this.preCacheHelper;
    if (preCacheHelper != null) {
      preCacheHelper.release(/* removeCachedContent= */ false);
    }
    @Nullable ExecutorService downloadExecutor = this.downloadExecutor;
    if (downloadExecutor != null) {
      downloadExecutor.shutdownNow();
    }
    this.player = null;
    this.playerHandler = null;
    mediaItem = null;
    this.preCacheHelper = null;
    this.downloadExecutor = null;
    options = Options.DISABLED;
    lastPreloadStartPositionMs = C.TIME_UNSET;
  }

  /** Releases the manager. */
  public void release() {
    if (released) {
      return;
    }
    stop();
    if (ownedPreloadThread != null) {
      ownedPreloadThread.quitSafely();
    }
    released = true;
  }

  @Override
  public void onPlaybackStateChanged(@Player.State int playbackState) {
    maybeStartPreload();
  }

  @Override
  public void onIsLoadingChanged(boolean isLoading) {
    maybeStartPreload();
  }

  @Override
  public void onAvailableCommandsChanged(Player.Commands availableCommands) {
    maybeStartPreload();
  }

  @Override
  public void onTimelineChanged(Timeline timeline, int reason) {
    maybeStartPreload();
  }

  @Override
  public void onPositionDiscontinuity(
      Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
    lastPreloadStartPositionMs = C.TIME_UNSET;
    @Nullable PreCacheHelper preCacheHelper = this.preCacheHelper;
    if (preCacheHelper != null) {
      preCacheHelper.stop();
    }
    maybeStartPreload();
  }

  @Override
  public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
    @Nullable MediaItem preloadMediaItem = this.mediaItem;
    if (mediaItem != null
        && preloadMediaItem != null
        && !hasSameLocalConfiguration(preloadMediaItem, mediaItem)) {
      stop();
    }
  }

  private void maybeStartPreload() {
    if (released) {
      return;
    }
    @Nullable Player player = this.player;
    @Nullable MediaItem mediaItem = this.mediaItem;
    @Nullable PreCacheHelper preCacheHelper = this.preCacheHelper;
    if (player == null || mediaItem == null || preCacheHelper == null || !options.enabled) {
      return;
    }
    if (!player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      scheduleCheck(CHECK_INTERVAL_MS);
      return;
    }
    @Player.State int playbackState = player.getPlaybackState();
    if (playbackState == Player.STATE_IDLE
        || playbackState == Player.STATE_ENDED
        || player.isCurrentMediaItemLive()) {
      preCacheHelper.stop();
      scheduleCheck(CHECK_INTERVAL_MS);
      return;
    }
    long durationMs = player.getDuration();
    if (durationMs <= 0) {
      scheduleCheck(CHECK_INTERVAL_MS);
      return;
    }
    long startPositionMs = getPreloadStartPositionMs(player);
    if (startPositionMs >= durationMs) {
      preCacheHelper.stop();
      scheduleCheck(CHECK_INTERVAL_MS);
      return;
    }
    if (!shouldRestartPreload(startPositionMs)) {
      scheduleCheck(CHECK_INTERVAL_MS);
      return;
    }
    long preloadDurationMs = min(options.durationMs, durationMs - startPositionMs);
    if (preloadDurationMs <= 0) {
      scheduleCheck(CHECK_INTERVAL_MS);
      return;
    }
    preCacheHelper.preCache(startPositionMs, preloadDurationMs);
    lastPreloadStartPositionMs = startPositionMs;
    scheduleCheck(CHECK_INTERVAL_MS);
  }

  private long getPreloadStartPositionMs(Player player) {
    long currentPositionMs = player.getCurrentPosition();
    long bufferedPositionMs = player.getBufferedPosition();
    long overlapMs = min(MAX_BUFFER_OVERLAP_MS, options.durationMs / 10);
    long bufferedOrCurrentPositionMs = max(currentPositionMs, bufferedPositionMs);
    return max(currentPositionMs, bufferedOrCurrentPositionMs - overlapMs);
  }

  private boolean shouldRestartPreload(long startPositionMs) {
    if (lastPreloadStartPositionMs == C.TIME_UNSET
        || startPositionMs < lastPreloadStartPositionMs) {
      return true;
    }
    return startPositionMs - lastPreloadStartPositionMs >= getRestartStepMs();
  }

  private long getRestartStepMs() {
    return constrainValue(options.durationMs / 4, MIN_RESTART_STEP_MS, MAX_RESTART_STEP_MS);
  }

  private void scheduleCheck(long delayMs) {
    @Nullable Handler playerHandler = this.playerHandler;
    if (playerHandler == null) {
      return;
    }
    playerHandler.removeCallbacks(checkRunnable);
    playerHandler.postDelayed(checkRunnable, delayMs);
  }

  private PreCacheHelper.Listener createPreCacheListener() {
    return new PreCacheHelper.Listener() {
      @Override
      public void onPrepared(MediaItem originalMediaItem, MediaItem updatedMediaItem) {
        if (listener != null) {
          listener.onPrepared(originalMediaItem, updatedMediaItem);
        }
      }

      @Override
      public void onPreCacheProgress(
          MediaItem mediaItem,
          long contentLength,
          long bytesDownloaded,
          float percentageDownloaded) {
        if (listener != null) {
          listener.onPreCacheProgress(
              mediaItem, contentLength, bytesDownloaded, percentageDownloaded);
        }
        if (isComplete(contentLength, bytesDownloaded, percentageDownloaded)) {
          scheduleCheck(/* delayMs= */ 0);
        }
      }

      @Override
      public void onPrepareError(MediaItem mediaItem, IOException error) {
        if (listener != null) {
          listener.onPrepareError(mediaItem, error);
        }
      }

      @Override
      public void onDownloadError(MediaItem mediaItem, IOException error) {
        if (listener != null) {
          listener.onDownloadError(mediaItem, error);
        }
        scheduleCheck(CHECK_INTERVAL_MS);
      }
    };
  }

  /* package */ static boolean canPreload(MediaItem mediaItem) {
    @Nullable MediaItem.LocalConfiguration localConfiguration = mediaItem.localConfiguration;
    if (localConfiguration == null) {
      return false;
    }
    Uri uri = localConfiguration.uri;
    String scheme = uri.getScheme();
    return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
  }

  private static boolean hasSameLocalConfiguration(MediaItem first, MediaItem second) {
    @Nullable MediaItem.LocalConfiguration firstLocalConfiguration = first.localConfiguration;
    @Nullable MediaItem.LocalConfiguration secondLocalConfiguration = second.localConfiguration;
    return firstLocalConfiguration != null
        && firstLocalConfiguration.equals(secondLocalConfiguration);
  }

  private static ExecutorService createDownloadExecutor(int maxThreads) {
    return Executors.newFixedThreadPool(
        maxThreads, runnable -> new Thread(runnable, "Media3:DiskPreloadDownloader"));
  }

  private static boolean isComplete(
      long contentLength, long bytesDownloaded, float percentageDownloaded) {
    return (contentLength != C.LENGTH_UNSET && bytesDownloaded >= contentLength)
        || percentageDownloaded >= 100f;
  }

  private static long constrainValue(long value, long minValue, long maxValue) {
    return max(minValue, min(maxValue, value));
  }
}
