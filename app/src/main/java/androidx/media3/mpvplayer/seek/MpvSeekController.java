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
package androidx.media3.mpvplayer.seek;

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_TIME_POS;
import static androidx.media3.mpvplayer.util.MpvUtil.secondsToMsOrUnset;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.mpvplayer.core.MpvPlaybackState;
import androidx.media3.mpvplayer.nativebridge.MpvCommandDispatcher;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public final class MpvSeekController {

  private static final ListenableFuture<?> COMPLETED = Futures.immediateVoidFuture();

  private final MpvPlaybackState playbackState;
  private final MpvCommandDispatcher commandDispatcher;
  private final MpvPropertyAccessor properties;
  private final Host host;
  private final MpvCommandedSeekState commandedSeekState;
  private final MpvSeekPositionResolver positionResolver;
  private final MpvEndedSeekHandler endedSeekHandler;
  private long seekRequestGeneration;

  public MpvSeekController(
      MpvPlaybackState playbackState,
      MpvCommandDispatcher commandDispatcher,
      MpvPropertyAccessor properties,
      Host host) {
    this.playbackState = playbackState;
    this.commandDispatcher = commandDispatcher;
    this.properties = properties;
    this.host = host;
    this.commandedSeekState = new MpvCommandedSeekState();
    this.positionResolver = new MpvSeekPositionResolver(playbackState);
    this.endedSeekHandler = new MpvEndedSeekHandler(playbackState, host);
  }

  public long resolvePositionMs(int mediaItemIndex, int currentIndex, long positionMs) {
    return positionResolver.resolvePositionMs(mediaItemIndex, currentIndex, positionMs);
  }

  public ListenableFuture<?> seekToPositionMs(long positionMs, @Player.Command int seekCommand) {
    boolean isEndPosition = positionResolver.isEndPosition(positionMs);
    boolean canSeekLoadedFile =
        host.isInitialized() && host.hasActiveMpvFile() && playbackState.isCurrentFileLoaded();
    if (!canSeekLoadedFile) {
      endedSeekHandler.seekWithoutLoadedFile(positionMs, seekCommand, isEndPosition);
      return COMPLETED;
    }
    if (isEndPosition) {
      reset();
      endedSeekHandler.endLoadedFileFromSeek();
      return COMPLETED;
    }
    if (isNoOpSeek(positionMs)) {
      playbackState.setPositionMs(positionMs);
      return COMPLETED;
    }
    return dispatchSeek(positionMs);
  }

  public boolean isCommandedSeekPending() {
    return commandedSeekState.isPending();
  }

  public boolean isEndingPlayback() {
    return commandedSeekState.isEndingPlayback();
  }

  public PlaybackRestartAction onPlaybackRestart() {
    if (!commandedSeekState.isPending()) {
      return PlaybackRestartAction.NOT_COMMANDED;
    }
    commandedSeekState.clearPending();
    commandedSeekState.completePendingFuture();
    if (dispatchQueuedCommandedSeek()) {
      return PlaybackRestartAction.QUEUED_SEEK_DISPATCHED;
    }
    playbackState.onPlaybackRestartBuffering();
    awaitCommandedSeekReady();
    return PlaybackRestartAction.WAITING_FOR_READY;
  }

  public boolean onPausedForCache(boolean pausedForCache) {
    if (commandedSeekState.isAwaitingReady()) {
      playbackState.onPausedForCache(pausedForCache);
      if (!pausedForCache) {
        completeCommandedSeekReady();
      }
      return true;
    }
    return false;
  }

  public boolean maybeCompleteReadyOnPosition(double positionSeconds) {
    if (commandedSeekState.shouldIgnorePosition(
        playbackState.getMappedMediaPositionMs(secondsToMsOrUnset(positionSeconds)))) {
      return false;
    }
    if (host.isPausedForCache()) {
      return false;
    }
    return completeCommandedSeekReady();
  }

  public boolean shouldIgnorePosition(double positionSeconds) {
    return commandedSeekState.shouldIgnorePosition(
        playbackState.getMappedMediaPositionMs(secondsToMsOrUnset(positionSeconds)));
  }

  public void clearPositionMaskIfActive() {
    if (commandedSeekState.isMaskingPosition()) {
      commandedSeekState.clearPositionMask();
    }
  }

  public void reset() {
    seekRequestGeneration++;
    commandedSeekState.reset();
  }

  public void clearTransitionFlags() {
    endedSeekHandler.clearTransitionFlags();
  }

  private boolean isNoOpSeek(long positionMs) {
    return positionMs == playbackState.getPositionMs()
        && (playbackState.getState() == Player.STATE_READY
            || playbackState.getState() == Player.STATE_BUFFERING);
  }

  private ListenableFuture<?> dispatchSeek(long positionMs) {
    playbackState.setPositionMs(positionMs);
    playbackState.clearPendingSeek();
    playbackState.onSeek();
    host.invalidatePlayerState();
    if (commandedSeekState.isPending()) {
      return commandedSeekState.queue(positionMs);
    }
    ListenableFuture<?> seekFuture = commandedSeekState.start(positionMs);
    dispatchCommandedSeekToMpv(positionMs);
    return seekFuture;
  }

  private void onCommandedSeekFailed() {
    host.updatePosition();
    playbackState.setReady();
    host.invalidatePlayerState();
  }

  private boolean dispatchQueuedCommandedSeek() {
    MpvCommandedSeekState.QueuedSeek seek = commandedSeekState.consumeQueued();
    if (seek == null) {
      return false;
    }
    long positionMs = seek.positionMs();
    commandedSeekState.start(seek);
    dispatchCommandedSeekToMpv(positionMs);
    return true;
  }

  private void dispatchCommandedSeekToMpv(long positionMs) {
    long requestGeneration = ++seekRequestGeneration;
    if (!commandDispatcher.seekTo(
        playbackState.getMpvPositionMs(positionMs),
        success -> onSeekRequestCompleted(requestGeneration, success))) {
      reset();
      onCommandedSeekFailed();
      return;
    }
    if (host.shouldPlay()) {
      host.setPauseProperty();
    }
  }

  private void onSeekRequestCompleted(long requestGeneration, boolean success) {
    if (success
        || requestGeneration != seekRequestGeneration
        || !commandedSeekState.isPending()) {
      return;
    }
    reset();
    onCommandedSeekFailed();
  }

  private void awaitCommandedSeekReady() {
    commandedSeekState.startWaitingForReady();
    if (!host.shouldPlay()) {
      completeCommandedSeekReady();
    }
  }

  private boolean completeCommandedSeekReady() {
    if (commandedSeekState.isAwaitingReady() && !host.isReleased()) {
      commandedSeekState.clearAwaitingReady();
      maybeClearPositionMask();
      playbackState.onPlaybackRestart();
      host.invalidatePlayerState();
      return true;
    }
    return false;
  }

  private void maybeClearPositionMask() {
    Double positionSeconds = properties.getDouble(PROP_TIME_POS);
    long positionMs =
        positionSeconds == null
            ? C.TIME_UNSET
            : playbackState.getMappedMediaPositionMs(secondsToMsOrUnset(positionSeconds));
    commandedSeekState.maybeClearPositionMask(positionMs);
  }

  public enum PlaybackRestartAction {
    NOT_COMMANDED,
    QUEUED_SEEK_DISPATCHED,
    WAITING_FOR_READY
  }

  public interface Host {

    boolean isInitialized();

    boolean hasActiveMpvFile();

    boolean isReleased();

    boolean isPausedForCache();

    boolean shouldPlay();

    @Nullable
    MediaItem currentMediaItem();

    void loadCurrent(MediaItem item, long startPositionMs);

    boolean stopActiveFile();

    boolean maybeLoadNextAfterEnd();

    void releaseAudioFocus();

    void updatePosition();

    void setPauseProperty();

    void invalidatePlayerState();
  }
}
