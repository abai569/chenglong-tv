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
package androidx.media3.mpvplayer.core;

import static androidx.media3.mpvplayer.util.MpvUtil.normalizePositionMs;
import static androidx.media3.mpvplayer.util.MpvUtil.secondsToMsOrUnset;

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer.PositionSupplier;
import androidx.media3.common.util.Util;

public final class MpvPlaybackState {

  private static final long END_POSITION_TOLERANCE_MS = 1000;

  private final MpvNativePositionMapper nativePositionMapper;
  private boolean loading;
  private boolean seeking;
  private boolean currentFileStarted;
  private boolean currentFileLoaded;
  private boolean demuxerCacheTimeAvailable;
  private @Player.State int state;
  private long pendingSeekMs;
  private long bufferedPositionMs;
  private long sourceCacheTimeMs;
  private long positionMs;
  private long durationMs;
  private long sourceStartPositionMs;
  private long sourceEndPositionMs;
  private LiveState liveState;
  private SeekableState seekableState;

  public MpvPlaybackState() {
    nativePositionMapper = new MpvNativePositionMapper();
    state = Player.STATE_IDLE;
    durationMs = C.TIME_UNSET;
    sourceCacheTimeMs = C.TIME_UNSET;
    pendingSeekMs = C.TIME_UNSET;
    liveState = LiveState.UNKNOWN;
    seekableState = SeekableState.UNKNOWN;
  }

  private static long addMs(long first, long second) {
    return first == C.TIME_UNSET || second == C.TIME_UNSET
        ? C.TIME_UNSET
        : Util.addWithOverflowDefault(first, second, C.TIME_UNSET);
  }

  public @Player.State int getState() {
    return state;
  }

  public boolean isLoading() {
    return loading;
  }

  public boolean isSeeking() {
    return seeking;
  }

  public boolean isReady() {
    return state == Player.STATE_READY;
  }

  public boolean isCurrentFileLoaded() {
    return currentFileLoaded;
  }

  public long getPositionMs() {
    return positionMs;
  }

  public void setPositionMs(long positionMs) {
    this.positionMs = normalizePositionMs(positionMs);
    nativePositionMapper.anchorNextNativePosition();
    updateBufferedPosition(Math.max(bufferedPositionMs, this.positionMs));
  }

  public long getDurationMs() {
    return durationMs;
  }

  public long getTimelineDurationMs() {
    if (!isCurrentMediaLive()) {
      return durationMs;
    }
    long liveDurationMs = Math.max(positionMs, bufferedPositionMs);
    if (hasKnownDuration()) {
      liveDurationMs = Math.max(liveDurationMs, durationMs);
    }
    return liveDurationMs > 0 ? liveDurationMs : C.TIME_UNSET;
  }

  public long getBufferedPositionMs() {
    return bufferedPositionMs;
  }

  public long getBufferedDurationMs() {
    if (bufferedPositionMs == C.TIME_UNSET || positionMs == C.TIME_UNSET) {
      return 0;
    }
    return Util.constrainValue(bufferedPositionMs - positionMs, 0, Long.MAX_VALUE);
  }

  public boolean isCurrentMediaSeekable() {
    if (seekableState == SeekableState.SEEKABLE) {
      return true;
    }
    if (isCurrentMediaLive() && hasLiveCacheWindow()) {
      return true;
    }
    if (seekableState == SeekableState.NOT_SEEKABLE) {
      return false;
    }
    return hasKnownDuration();
  }

  public boolean isCurrentMediaLive() {
    return liveState == LiveState.LIVE;
  }

  public long getDefaultPositionMs() {
    long timelineDurationMs = getTimelineDurationMs();
    return isCurrentMediaLive() && timelineDurationMs != C.TIME_UNSET ? timelineDurationMs : 0;
  }

  boolean setLiveState(LiveState liveState) {
    if (this.liveState == liveState) {
      return false;
    }
    this.liveState = liveState;
    return true;
  }

  boolean setSeekableState(SeekableState seekableState) {
    if (this.seekableState == seekableState) {
      return false;
    }
    this.seekableState = seekableState;
    return true;
  }

  public boolean isNearEnd() {
    return isNearEndPosition(positionMs);
  }

  public boolean isNearEndPosition(long positionMs) {
    return !isCurrentMediaLive()
        && hasKnownDuration()
        && normalizePositionMs(positionMs) >= getNearEndPositionMs();
  }

  public PositionSupplier getPositionSupplier(float playbackSpeed, boolean playWhenReady) {
    if (state == Player.STATE_READY && playWhenReady) {
      return PositionSupplier.getExtrapolating(positionMs, playbackSpeed);
    }
    return PositionSupplier.getConstant(positionMs);
  }

  public void setIdle() {
    state = Player.STATE_IDLE;
    loading = false;
    seeking = false;
  }

  public void setEnded() {
    state = Player.STATE_ENDED;
    loading = false;
    seeking = false;
  }

  public void setReady() {
    state = Player.STATE_READY;
    loading = false;
    seeking = false;
  }

  public void resetCurrentMedia(long startPositionMs) {
    resetCurrentMedia(startPositionMs, 0, C.TIME_END_OF_SOURCE);
  }

  public void resetCurrentMedia(
      long startPositionMs, long sourceStartPositionMs, long sourceEndPositionMs) {
    this.sourceStartPositionMs = normalizePositionMs(sourceStartPositionMs);
    this.sourceEndPositionMs = sourceEndPositionMs;
    positionMs = normalizePositionMs(startPositionMs);
    bufferedPositionMs = positionMs;
    setDurationFromSource(C.TIME_UNSET);
    demuxerCacheTimeAvailable = false;
    sourceCacheTimeMs = C.TIME_UNSET;
    liveState = LiveState.UNKNOWN;
    seekableState = SeekableState.UNKNOWN;
    nativePositionMapper.reset();
  }

  public void startLoading(
      long startPositionMs, long sourceStartPositionMs, long sourceEndPositionMs) {
    loading = true;
    seeking = false;
    currentFileStarted = false;
    currentFileLoaded = false;
    state = Player.STATE_BUFFERING;
    pendingSeekMs = startPositionMs > 0 ? startPositionMs : C.TIME_UNSET;
    resetCurrentMedia(startPositionMs, sourceStartPositionMs, sourceEndPositionMs);
  }

  public void onStartFile() {
    currentFileStarted = true;
    loading = true;
    seeking = false;
    state = Player.STATE_BUFFERING;
  }

  public void onFileLoaded() {
    currentFileStarted = true;
    currentFileLoaded = true;
    loading = false;
    seeking = false;
    state = Player.STATE_READY;
  }

  public boolean onEndFile() {
    boolean loadFailed = loading && !currentFileLoaded;
    currentFileStarted = false;
    currentFileLoaded = false;
    seeking = false;
    if (!loadFailed) {
      if (!isCurrentMediaLive() && hasKnownDuration()) {
        positionMs = durationMs;
        updateBufferedPosition(durationMs);
      }
      loading = false;
      state = Player.STATE_ENDED;
    }
    return loadFailed;
  }

  public void onSeek() {
    loading = true;
    seeking = true;
    nativePositionMapper.anchorNextNativePosition();
    bufferedPositionMs = positionMs;
    demuxerCacheTimeAvailable = false;
    sourceCacheTimeMs = C.TIME_UNSET;
    state = Player.STATE_BUFFERING;
  }

  public void onPlaybackRestart() {
    setReady();
  }

  public void onPlaybackRestartBuffering() {
    loading = true;
    seeking = false;
    state = Player.STATE_BUFFERING;
  }

  public void onPausedForCache(boolean pausedForCache) {
    if (pausedForCache) {
      loading = true;
      state = Player.STATE_BUFFERING;
    } else {
      loading = false;
      seeking = false;
      if (state == Player.STATE_BUFFERING) {
        state = Player.STATE_READY;
      }
    }
  }

  public boolean onPositionProperty(double positionSeconds) {
    long sourcePositionMs = secondsToMsOrUnset(positionSeconds);
    if (sourcePositionMs == C.TIME_UNSET) {
      return false;
    }
    long updatedPositionMs =
        nativePositionMapper.map(positionMs, getMediaPositionMs(sourcePositionMs));
    boolean changed = positionMs != updatedPositionMs;
    positionMs = updatedPositionMs;
    updateBufferedPosition(
        demuxerCacheTimeAvailable
            ? nativePositionMapper.toMediaPositionMs(sourceCacheTimeMs)
            : Math.max(bufferedPositionMs, positionMs));
    return changed;
  }

  public void onDurationProperty(double durationSeconds) {
    updateDuration(secondsToMsOrUnset(durationSeconds));
  }

  private void updateDuration(long sourceDurationMs) {
    setDurationFromSource(sourceDurationMs);
    updateBufferedPosition(bufferedPositionMs);
  }

  public void onCacheTimeProperty(double cacheTimeSeconds) {
    sourceCacheTimeMs = getMediaPositionMs(secondsToMsOrUnset(cacheTimeSeconds));
    demuxerCacheTimeAvailable = sourceCacheTimeMs != C.TIME_UNSET;
    updateBufferedPosition(
        demuxerCacheTimeAvailable
            ? nativePositionMapper.toMediaPositionMs(sourceCacheTimeMs)
            : positionMs);
  }

  public void onCacheTimeInvalidated() {
    demuxerCacheTimeAvailable = false;
    sourceCacheTimeMs = C.TIME_UNSET;
    updateBufferedPosition(positionMs);
  }

  public void onCacheDurationInvalidated() {
    if (!demuxerCacheTimeAvailable) {
      updateBufferedPosition(positionMs);
    }
  }

  public void onCacheDurationProperty(double cacheDurationSeconds) {
    if (!demuxerCacheTimeAvailable) {
      updateBufferedPosition(addMs(positionMs, secondsToMsOrUnset(cacheDurationSeconds)));
    }
  }

  public boolean hasActiveFile(boolean initialized) {
    return initialized
        && (currentFileStarted
            || currentFileLoaded
            || state == Player.STATE_READY
            || state == Player.STATE_BUFFERING);
  }

  public void stopActiveFile() {
    loading = false;
    seeking = false;
    currentFileStarted = false;
    currentFileLoaded = false;
    pendingSeekMs = C.TIME_UNSET;
  }

  public void fail() {
    state = Player.STATE_IDLE;
    loading = false;
    seeking = false;
    currentFileStarted = false;
    currentFileLoaded = false;
  }

  public long consumePendingSeekMs() {
    long result = pendingSeekMs;
    pendingSeekMs = C.TIME_UNSET;
    return result;
  }

  public void clearPendingSeek() {
    pendingSeekMs = C.TIME_UNSET;
  }

  public void setPendingSeekMs(long positionMs) {
    pendingSeekMs = positionMs == C.TIME_UNSET ? C.TIME_UNSET : normalizePositionMs(positionMs);
  }

  public long getMpvPositionMs(long positionMs) {
    if (positionMs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    long mpvPositionMs =
        sourceStartPositionMs
            + nativePositionMapper.toNativePositionMs(normalizePositionMs(positionMs));
    if (sourceEndPositionMs != C.TIME_END_OF_SOURCE) {
      mpvPositionMs =
          Util.constrainValue(mpvPositionMs, sourceStartPositionMs, sourceEndPositionMs);
    }
    return mpvPositionMs;
  }

  public long getMediaPositionMs(long sourcePositionMs) {
    if (sourcePositionMs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    long positionMs = normalizePositionMs(sourcePositionMs - sourceStartPositionMs);
    if (!isCurrentMediaLive() && hasKnownDuration()) {
      positionMs = Util.constrainValue(positionMs, 0, durationMs);
    }
    return positionMs;
  }

  public long getMappedMediaPositionMs(long sourcePositionMs) {
    long mediaPositionMs = getMediaPositionMs(sourcePositionMs);
    return mediaPositionMs == C.TIME_UNSET
        ? C.TIME_UNSET
        : nativePositionMapper.toMediaPositionMs(mediaPositionMs);
  }

  private void updateBufferedPosition(long bufferedPositionMs) {
    long position = normalizePositionMs(positionMs);
    long buffered =
        bufferedPositionMs == C.TIME_UNSET ? position : Math.max(position, bufferedPositionMs);
    if (!isCurrentMediaLive() && hasKnownDuration()) {
      buffered = Util.constrainValue(buffered, 0, durationMs);
    }
    this.bufferedPositionMs = buffered;
  }

  private boolean hasKnownDuration() {
    return durationMs != C.TIME_UNSET && durationMs > 0;
  }

  private boolean hasLiveCacheWindow() {
    return demuxerCacheTimeAvailable || bufferedPositionMs > positionMs;
  }

  private void setDurationFromSource(long sourceDurationMs) {
    long effectiveSourceEndPositionMs;
    if (sourceEndPositionMs == C.TIME_END_OF_SOURCE) {
      effectiveSourceEndPositionMs = sourceDurationMs;
    } else if (sourceDurationMs == C.TIME_UNSET) {
      effectiveSourceEndPositionMs = sourceEndPositionMs;
    } else {
      effectiveSourceEndPositionMs = Math.min(sourceEndPositionMs, sourceDurationMs);
    }
    durationMs =
        effectiveSourceEndPositionMs == C.TIME_UNSET
            ? C.TIME_UNSET
            : Util.constrainValue(
                effectiveSourceEndPositionMs - sourceStartPositionMs, 0, Long.MAX_VALUE);
  }

  private long getNearEndPositionMs() {
    return Util.constrainValue(durationMs - END_POSITION_TOLERANCE_MS, 0, Long.MAX_VALUE);
  }

  enum LiveState {
    UNKNOWN,
    NOT_LIVE,
    LIVE
  }

  enum SeekableState {
    UNKNOWN,
    NOT_SEEKABLE,
    SEEKABLE
  }
}
