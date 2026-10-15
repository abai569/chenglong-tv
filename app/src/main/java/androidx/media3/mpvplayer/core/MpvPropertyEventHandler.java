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
import androidx.media3.common.MediaChapter;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.Tracks;
import androidx.media3.mpvplayer.audio.MpvAudioFocusManager;
import androidx.media3.mpvplayer.media.MpvArtworkLoader;
import androidx.media3.mpvplayer.media.MpvChapterController;
import androidx.media3.mpvplayer.media.MpvEditionController;
import androidx.media3.mpvplayer.media.MpvPlaylist;
import androidx.media3.mpvplayer.nativebridge.MpvEventAdapter;
import androidx.media3.mpvplayer.seek.MpvSeekController;
import androidx.media3.mpvplayer.trackselection.MpvTrackController;
import androidx.media3.mpvplayer.video.MpvVideoState;
import androidx.media3.mpvplayer.video.MpvVideoTrackEnableGate;
import com.google.common.collect.ImmutableList;

final class MpvPropertyEventHandler implements MpvEventAdapter.PropertyEventHost {

  private final MpvPlaybackEventState eventState;
  private final MpvPlaylist playlist;
  private final MpvPlaybackState playbackState;
  private final MpvChapterController chapterController;
  private final MpvEditionController editionController;
  private final MpvSeekController seekController;
  private final MpvTrackController trackController;
  private final MpvVideoState videoState;
  private final MpvVideoTrackEnableGate videoTrackEnableGate;
  private final MpvPlaybackPropertyUpdater propertyUpdater;
  private final MpvAudioFocusManager audioFocusManager;
  private final MpvArtworkLoader artworkLoader;
  private final MpvPlaybackEventHandler.Host host;

  MpvPropertyEventHandler(
      MpvPlaybackEventState eventState,
      MpvPlaylist playlist,
      MpvPlaybackState playbackState,
      MpvChapterController chapterController,
      MpvEditionController editionController,
      MpvSeekController seekController,
      MpvTrackController trackController,
      MpvVideoState videoState,
      MpvVideoTrackEnableGate videoTrackEnableGate,
      MpvPlaybackPropertyUpdater propertyUpdater,
      MpvAudioFocusManager audioFocusManager,
      MpvArtworkLoader artworkLoader,
      MpvPlaybackEventHandler.Host host) {
    this.eventState = eventState;
    this.playlist = playlist;
    this.playbackState = playbackState;
    this.chapterController = chapterController;
    this.editionController = editionController;
    this.seekController = seekController;
    this.trackController = trackController;
    this.videoState = videoState;
    this.videoTrackEnableGate = videoTrackEnableGate;
    this.propertyUpdater = propertyUpdater;
    this.audioFocusManager = audioFocusManager;
    this.artworkLoader = artworkLoader;
    this.host = host;
  }

  @Override
  public Tracks readTracks() {
    return trackController.readTracks();
  }

  @Override
  public ImmutableList<MediaChapter> readChapters() {
    return chapterController.readChapters();
  }

  @Override
  public boolean onChapterListPropertyChanged(ImmutableList<MediaChapter> chapters) {
    return chapterController.updateChapters(chapters);
  }

  @Override
  public ImmutableList<MediaEdition> readEditions() {
    return editionController.readEditions();
  }

  @Override
  public boolean onEditionListPropertyChanged(ImmutableList<MediaEdition> editions) {
    return editionController.updateEditions(editions);
  }

  @Override
  public boolean onTrackPropertyChanged(Tracks tracks) {
    if (!playbackState.isCurrentFileLoaded()) {
      return false;
    }
    boolean tracksChanged = trackController.updateTracks(tracks);
    clearVideoStateIfDisabled();
    videoTrackEnableGate.maybeCompletePendingEnable();
    return tracksChanged;
  }

  @Override
  public @Nullable byte[] readArtworkData() {
    return artworkLoader.readAttachedPicture();
  }

  @Override
  public void onArtworkData(byte[] artworkData) {
    artworkLoader.load(playlist.current(), artworkData);
  }

  @Override
  public @Nullable Double readPosition() {
    return propertyUpdater.readPosition();
  }

  @Override
  public boolean onChapterPropertyInvalidated() {
    return chapterController.updateChapterSelection(C.INDEX_UNSET);
  }

  @Override
  public boolean onChapterProperty(long chapter) {
    return chapterController.updateChapterSelection(chapter);
  }

  @Override
  public boolean onEditionPropertyInvalidated() {
    return editionController.updateEditionSelection(C.INDEX_UNSET);
  }

  @Override
  public boolean onEditionProperty(long edition) {
    return editionController.updateEditionSelection(edition);
  }

  @Override
  public void onCacheTimeInvalidated() {
    playbackState.onCacheTimeInvalidated();
  }

  @Override
  public void onCacheTimeProperty(double cacheTimeSeconds) {
    playbackState.onCacheTimeProperty(cacheTimeSeconds);
  }

  @Override
  public void onCacheDurationInvalidated() {
    playbackState.onCacheDurationInvalidated();
  }

  @Override
  public void onCacheDurationProperty(double cacheDurationSeconds) {
    playbackState.onCacheDurationProperty(cacheDurationSeconds);
  }

  @Override
  public void onVideoWidthProperty(long width) {
    propertyUpdater.onVideoWidthProperty(width);
  }

  @Override
  public void onVideoHeightProperty(long height) {
    propertyUpdater.onVideoHeightProperty(height);
  }

  @Override
  public void onVideoAspectProperty(double aspect) {
    propertyUpdater.onVideoAspectProperty(aspect);
  }

  @Override
  public void onVideoRotationProperty(long rotation) {
    propertyUpdater.onVideoRotationProperty(rotation);
  }

  @Override
  public void onAlbumArtProperty(boolean albumArt) {
    propertyUpdater.onAlbumArtProperty(albumArt);
  }

  @Override
  public void onPauseProperty(boolean paused) {
    if (!playbackState.isCurrentFileLoaded()
        || playbackState.isSeeking()
        || audioFocusManager.isResumeOnAudioFocusGain()) {
      return;
    }
    host.setPlayWhenReadyFromNative(!paused);
    if (paused && !audioFocusManager.isResumeOnAudioFocusGain()) {
      host.releaseAudioFocus();
    }
  }

  @Override
  public boolean onPausedForCacheProperty(boolean pausedForCache) {
    eventState.setPausedForCache(pausedForCache);
    if (consumeIgnoredRepeatOneBuffering(pausedForCache)) {
      return false;
    }
    if (seekController.onPausedForCache(pausedForCache)) {
      return true;
    }
    return updatePausedForCacheState(pausedForCache);
  }

  @Override
  public boolean onPositionProperty(double positionSeconds) {
    if (seekController.shouldIgnorePosition(positionSeconds)) {
      return false;
    }
    boolean seekReady = seekController.maybeCompleteReadyOnPosition(positionSeconds);
    seekController.clearPositionMaskIfActive();
    return playbackState.onPositionProperty(positionSeconds) || seekReady;
  }

  @Override
  public void onDurationProperty(double durationSeconds) {
    playbackState.onDurationProperty(durationSeconds);
  }

  @Override
  public boolean onLivePropertyInvalidated() {
    return playbackState.setLiveState(MpvPlaybackState.LiveState.UNKNOWN);
  }

  @Override
  public boolean onLiveProperty(boolean live) {
    return playbackState.setLiveState(
        live ? MpvPlaybackState.LiveState.LIVE : MpvPlaybackState.LiveState.NOT_LIVE);
  }

  @Override
  public boolean onSeekablePropertyInvalidated() {
    return playbackState.setSeekableState(MpvPlaybackState.SeekableState.UNKNOWN);
  }

  @Override
  public boolean onSeekableProperty(boolean seekable) {
    return playbackState.setSeekableState(
        seekable
            ? MpvPlaybackState.SeekableState.SEEKABLE
            : MpvPlaybackState.SeekableState.NOT_SEEKABLE);
  }

  void refreshForFileLoaded(Tracks tracks) {
    trackController.onFileLoaded();
    trackController.updateTracks(tracks);
    clearVideoStateIfDisabled();
  }

  void updatePosition(@Nullable Double positionSeconds) {
    propertyUpdater.updatePosition(positionSeconds);
  }

  void onPlaybackRestart() {
    clearVideoStateIfDisabled();
    videoTrackEnableGate.maybeCompletePendingEnable();
  }

  private void clearVideoStateIfDisabled() {
    if (!trackController.isTrackTypeSelected(C.TRACK_TYPE_VIDEO)) {
      videoState.clearProperties();
    }
  }

  private boolean consumeIgnoredRepeatOneBuffering(boolean pausedForCache) {
    if (!eventState.ignoreRepeatOneBuffering()) {
      return false;
    }
    if (!pausedForCache) {
      eventState.setIgnoreRepeatOneBuffering(false);
    }
    return true;
  }

  private boolean updatePausedForCacheState(boolean pausedForCache) {
    if (playbackState.isSeeking() && !pausedForCache) {
      return false;
    }
    playbackState.onPausedForCache(pausedForCache);
    return true;
  }
}
