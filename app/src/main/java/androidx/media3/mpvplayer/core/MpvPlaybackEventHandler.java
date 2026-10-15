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

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.mpvplayer.audio.MpvAudioFocusManager;
import androidx.media3.mpvplayer.media.MpvArtworkLoader;
import androidx.media3.mpvplayer.media.MpvChapterController;
import androidx.media3.mpvplayer.media.MpvEditionController;
import androidx.media3.mpvplayer.media.MpvEndFileGuard;
import androidx.media3.mpvplayer.media.MpvPlaybackNavigator;
import androidx.media3.mpvplayer.media.MpvPlaylist;
import androidx.media3.mpvplayer.media.MpvSubtitleController;
import androidx.media3.mpvplayer.nativebridge.MpvEventAdapter;
import androidx.media3.mpvplayer.nativebridge.MpvPlaybackErrorFactory;
import androidx.media3.mpvplayer.seek.MpvSeekController;
import androidx.media3.mpvplayer.trackselection.MpvTrackController;
import androidx.media3.mpvplayer.video.MpvVideoState;
import androidx.media3.mpvplayer.video.MpvVideoTrackEnableGate;

public final class MpvPlaybackEventHandler implements MpvEventAdapter.PlayerEventHost {

  private final MpvPlaybackEventState eventState;
  private final MpvPlaylist playlist;
  private final MpvPlaybackNavigator playbackNavigator;
  private final MpvPlaybackState playbackState;
  private final MpvEndFileGuard endFileGuard;
  private final MpvSubtitleController subtitleController;
  private final MpvSeekController seekController;
  private final MpvVideoState videoState;
  private final MpvVideoTrackEnableGate videoTrackEnableGate;
  private final MpvPropertyEventHandler propertyEventHandler;
  private final MpvAudioFocusManager audioFocusManager;
  private final MpvPlaybackErrorFactory errorFactory;
  private final Host host;

  public MpvPlaybackEventHandler(
      MpvPlaybackEventState eventState,
      MpvPlaylist playlist,
      MpvPlaybackNavigator playbackNavigator,
      MpvPlaybackState playbackState,
      MpvEndFileGuard endFileGuard,
      MpvChapterController chapterController,
      MpvEditionController editionController,
      MpvSubtitleController subtitleController,
      MpvSeekController seekController,
      MpvTrackController trackController,
      MpvVideoState videoState,
      MpvVideoTrackEnableGate videoTrackEnableGate,
      MpvPlaybackPropertyUpdater propertyUpdater,
      MpvAudioFocusManager audioFocusManager,
      MpvPlaybackErrorFactory errorFactory,
      MpvArtworkLoader artworkLoader,
      Host host) {
    this.eventState = eventState;
    this.playlist = playlist;
    this.playbackNavigator = playbackNavigator;
    this.playbackState = playbackState;
    this.endFileGuard = endFileGuard;
    this.subtitleController = subtitleController;
    this.seekController = seekController;
    this.videoState = videoState;
    this.videoTrackEnableGate = videoTrackEnableGate;
    this.audioFocusManager = audioFocusManager;
    this.errorFactory = errorFactory;
    this.host = host;
    this.propertyEventHandler =
        new MpvPropertyEventHandler(
            eventState,
            playlist,
            playbackState,
            chapterController,
            editionController,
            seekController,
            trackController,
            videoState,
            videoTrackEnableGate,
            propertyUpdater,
            audioFocusManager,
            artworkLoader,
            host);
  }

  @Override
  public void runOnPlayerLooper(Runnable runnable) {
    host.runOnPlayerLooper(runnable);
  }

  @Override
  public void runOnPlayerLooperAfterRelease(Runnable runnable) {
    host.runOnPlayerLooperAfterRelease(runnable);
  }

  @Override
  public void invalidateState() {
    host.invalidatePlayerState();
  }

  public MpvEventAdapter.PropertyEventHost propertyEventHost() {
    return propertyEventHandler;
  }

  @Override
  public void onStartFile() {
    endFileGuard.clear();
    playbackState.onStartFile();
    host.invalidatePlayerState();
  }

  @Override
  public void onFileLoaded(Tracks tracks) {
    subtitleController.addPendingSubtitles();
    propertyEventHandler.refreshForFileLoaded(tracks);
    playbackState.onFileLoaded();
    long pendingSeekMs = playbackState.consumePendingSeekMs();
    if (pendingSeekMs != C.TIME_UNSET) {
      seekController.seekToPositionMs(pendingSeekMs, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    }
    host.setPauseProperty();
    host.invalidatePlayerState();
  }

  @Override
  public void onEndFile(int reason, int error, @Nullable String errorString) {
    if (consumeExpectedEndFile()) {
      return;
    }
    boolean loadFailed = finishCurrentEndFile();
    if (maybeFailEndFile(reason, error, errorString, loadFailed)) {
      return;
    }
    if (host.maybeLoadNextAfterEnd()) {
      return;
    }
    finishEndedPlayback();
  }

  @Override
  public void onSeek() {
    if (host.hasActiveMpvFile()) {
      handleActiveFileSeek();
    }
  }

  private void handleActiveFileSeek() {
    if (seekController.isCommandedSeekPending()) {
      return;
    }
    if (playbackNavigator.isRepeatOneLoopSeek(host.getRepeatMode(), playbackState)) {
      eventState.setIgnoreRepeatOneBuffering(true);
      playbackState.setPositionMs(0);
      host.setPendingDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 0);
      host.invalidatePlayerState();
      return;
    }
    playbackState.onSeek();
    host.invalidatePlayerState();
  }

  @Override
  public void onPlaybackRestart(@Nullable Double positionSeconds) {
    if (host.hasActiveMpvFile() && playbackState.isCurrentFileLoaded()) {
      handleActiveFilePlaybackRestart(positionSeconds);
    }
  }

  private void handleActiveFilePlaybackRestart(@Nullable Double positionSeconds) {
    MpvSeekController.PlaybackRestartAction seekRestartAction = seekController.onPlaybackRestart();
    videoTrackEnableGate.clear();
    eventState.setIgnoreRepeatOneBuffering(false);
    if (seekRestartAction == MpvSeekController.PlaybackRestartAction.NOT_COMMANDED) {
      propertyEventHandler.updatePosition(positionSeconds);
      if (eventState.isPausedForCache()) {
        playbackState.onPlaybackRestartBuffering();
      } else {
        playbackState.onPlaybackRestart();
      }
    }
    propertyEventHandler.onPlaybackRestart();
    if (host.isPlayWhenReady() && audioFocusManager.requestForPlayback()) {
      host.setPauseProperty();
    }
    host.setVolumeProperty();
    videoState.markRenderedFirstFrame();
    host.invalidatePlayerState();
  }

  private boolean consumeExpectedEndFile() {
    if (!seekController.isEndingPlayback() && endFileGuard.consumeExpected()) {
      return true;
    }
    endFileGuard.clear();
    return false;
  }

  private boolean finishCurrentEndFile() {
    boolean loadFailed = playbackState.onEndFile();
    seekController.reset();
    return loadFailed;
  }

  private boolean maybeFailEndFile(
      int reason, int error, @Nullable String errorString, boolean loadFailed) {
    if (!loadFailed && !MpvPlaybackErrorFactory.isEndFileError(reason, error)) {
      return false;
    }
    host.fail(
        errorFactory.createEndFileFailure(playlist.currentUri(), loadFailed, error, errorString));
    return true;
  }

  private void finishEndedPlayback() {
    host.releaseAudioFocus();
    host.invalidatePlayerState();
  }

  @Override
  public void onShutdown() {
    host.onNativeShutdown();
  }

  public interface Host {

    void runOnPlayerLooper(Runnable runnable);

    void runOnPlayerLooperAfterRelease(Runnable runnable);

    void invalidatePlayerState();

    boolean hasActiveMpvFile();

    @Player.RepeatMode
    int getRepeatMode();

    boolean isPlayWhenReady();

    void setPlayWhenReadyFromNative(boolean playWhenReady);

    boolean maybeLoadNextAfterEnd();

    void releaseAudioFocus();

    void setPauseProperty();

    void setVolumeProperty();

    void setPendingDiscontinuity(@Player.DiscontinuityReason int reason, long positionMs);

    void onNativeShutdown();

    void fail(PlaybackException error);
  }
}
