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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.SEEK_INCREMENT_MS;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.mpvplayer.media.MpvChapterController;
import androidx.media3.mpvplayer.media.MpvEditionController;
import androidx.media3.mpvplayer.media.MpvMediaMetadata;
import androidx.media3.mpvplayer.media.MpvPlaylist;
import androidx.media3.mpvplayer.trackselection.MpvTrackController;
import androidx.media3.mpvplayer.video.MpvSurfaceController;
import androidx.media3.mpvplayer.video.MpvVideoState;
import java.util.List;

public final class MpvStateBuilder {

  private final MpvPlaylist playlist;
  private final MpvPlaybackState playbackState;
  private final MpvChapterController chapterController;
  private final MpvEditionController editionController;
  private final MpvTrackController trackController;
  private final MpvVideoState videoState;
  private final MpvSurfaceController surfaceController;
  private final MpvMediaMetadata mediaMetadata;

  public MpvStateBuilder(
      MpvPlaylist playlist,
      MpvPlaybackState playbackState,
      MpvChapterController chapterController,
      MpvEditionController editionController,
      MpvTrackController trackController,
      MpvVideoState videoState,
      MpvSurfaceController surfaceController,
      MpvMediaMetadata mediaMetadata) {
    this.playlist = playlist;
    this.playbackState = playbackState;
    this.chapterController = chapterController;
    this.editionController = editionController;
    this.trackController = trackController;
    this.videoState = videoState;
    this.surfaceController = surfaceController;
    this.mediaMetadata = mediaMetadata;
  }

  public SimpleBasePlayer.State.Builder buildBaseState(Snapshot snapshot) {
    boolean playlistEmpty = playlist.isEmpty();
    @Player.State int state = getVisiblePlaybackState(playlistEmpty, snapshot.playerError);
    boolean idleOrEnded = state == Player.STATE_IDLE || state == Player.STATE_ENDED;
    long positionMs = playbackState.getPositionMs();
    boolean currentMediaLive = !playlistEmpty && playbackState.isCurrentMediaLive();
    return new SimpleBasePlayer.State.Builder()
        .setAvailableCommands(
            MpvAvailableCommands.build(
                playlistEmpty,
                !playlistEmpty && playbackState.isCurrentMediaSeekable(),
                currentMediaLive,
                playlist.hasPreviousMediaItem(snapshot.repeatMode),
                playlist.hasNextMediaItem(snapshot.repeatMode)))
        .setPlaybackState(state)
        .setPlayerError(snapshot.playerError)
        .setPlayWhenReady(snapshot.playWhenReady, snapshot.playWhenReadyChangeReason)
        .setPlaybackSuppressionReason(snapshot.playbackSuppressionReason)
        .setRepeatMode(snapshot.repeatMode)
        .setIsLoading(!idleOrEnded && playbackState.isLoading())
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .setMaxSeekToPreviousPositionMs(C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS)
        .setPlaybackParameters(snapshot.playbackParameters)
        .setAudioAttributes(snapshot.audioAttributes)
        .setTrackSelectionParameters(snapshot.trackSelectionParameters)
        .setCurrentMediaChapters(chapterController.getChapters())
        .setCurrentMediaEditions(editionController.getEditions())
        .setPlaylist(buildPlaylist())
        .setPlaylistMetadata(MediaMetadata.EMPTY)
        .setContentPositionMs(getPositionSupplier(state, snapshot, positionMs))
        .setContentBufferedPositionMs(
            SimpleBasePlayer.PositionSupplier.getConstant(
                getBufferedPositionMs(idleOrEnded, positionMs)))
        .setTotalBufferedDurationMs(
            SimpleBasePlayer.PositionSupplier.getConstant(getBufferedDurationMs(idleOrEnded)))
        .setVideoSize(videoState.getVideoSize())
        .setSurfaceSize(surfaceController.getSurfaceSize())
        .setNewlyRenderedFirstFrame(snapshot.newlyRenderedFirstFrame)
        .setVolume(snapshot.volume)
        .setAudioOffsetMs(snapshot.audioOffsetMs)
        .setTextOffsetMs(snapshot.textOffsetMs);
  }

  private @Player.State int getVisiblePlaybackState(
      boolean playlistEmpty, @Nullable PlaybackException playerError) {
    if (playerError != null) {
      return Player.STATE_IDLE;
    }
    @Player.State int state = playbackState.getState();
    if (!playlistEmpty) {
      return state;
    }
    return state == Player.STATE_ENDED ? Player.STATE_ENDED : Player.STATE_IDLE;
  }

  private SimpleBasePlayer.PositionSupplier getPositionSupplier(
      @Player.State int state, Snapshot snapshot, long positionMs) {
    if (state == Player.STATE_READY) {
      return playbackState.getPositionSupplier(
          snapshot.playbackParameters.speed, snapshot.shouldPlay());
    }
    return SimpleBasePlayer.PositionSupplier.getConstant(positionMs);
  }

  private long getBufferedPositionMs(boolean idleOrEnded, long positionMs) {
    return idleOrEnded ? positionMs : playbackState.getBufferedPositionMs();
  }

  private long getBufferedDurationMs(boolean idleOrEnded) {
    return idleOrEnded ? 0 : playbackState.getBufferedDurationMs();
  }

  private List<SimpleBasePlayer.MediaItemData> buildPlaylist() {
    return playlist.build(
        currentTracks(),
        currentMediaMetadata(),
        playbackState.getTimelineDurationMs(),
        playbackState.getDefaultPositionMs(),
        playbackState.isCurrentMediaSeekable(),
        playbackState.isCurrentMediaLive());
  }

  private Tracks currentTracks() {
    if (!playbackState.isCurrentFileLoaded()) {
      return Tracks.EMPTY;
    }
    return trackController.getTracks();
  }

  private MediaMetadata currentMediaMetadata() {
    MediaItem current = playlist.current();
    return mediaMetadata.build(current);
  }

  public static final class Snapshot {

    @Nullable private final PlaybackException playerError;
    private final boolean playWhenReady;
    private final @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason;
    private final @Player.PlaybackSuppressionReason int playbackSuppressionReason;
    private final @Player.RepeatMode int repeatMode;
    private final PlaybackParameters playbackParameters;
    private final AudioAttributes audioAttributes;
    private final TrackSelectionParameters trackSelectionParameters;
    private final boolean newlyRenderedFirstFrame;
    private final float volume;
    private final long audioOffsetMs;
    private final long textOffsetMs;

    public Snapshot(
        @Nullable PlaybackException playerError,
        boolean playWhenReady,
        @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason,
        @Player.PlaybackSuppressionReason int playbackSuppressionReason,
        @Player.RepeatMode int repeatMode,
        PlaybackParameters playbackParameters,
        AudioAttributes audioAttributes,
        TrackSelectionParameters trackSelectionParameters,
        boolean newlyRenderedFirstFrame,
        float volume,
        long audioOffsetMs,
        long textOffsetMs) {
      this.playerError = playerError;
      this.playWhenReady = playWhenReady;
      this.playWhenReadyChangeReason = playWhenReadyChangeReason;
      this.playbackSuppressionReason = playbackSuppressionReason;
      this.repeatMode = repeatMode;
      this.playbackParameters = playbackParameters;
      this.audioAttributes = audioAttributes;
      this.trackSelectionParameters = trackSelectionParameters;
      this.newlyRenderedFirstFrame = newlyRenderedFirstFrame;
      this.volume = volume;
      this.audioOffsetMs = audioOffsetMs;
      this.textOffsetMs = textOffsetMs;
    }

    private boolean shouldPlay() {
      return playWhenReady && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    }
  }
}
