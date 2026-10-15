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
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;

public final class MpvPlayerInfo {

  @Nullable private PlaybackException playerError;
  private PlaybackParameters playbackParameters;
  private AudioAttributes audioAttributes;
  private boolean playWhenReady;
  private @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason;
  private @Player.PlaybackSuppressionReason int playbackSuppressionReason;
  private @Player.RepeatMode int repeatMode;
  private float volume;
  private long audioOffsetMs;
  private long textOffsetMs;
  private int pendingDiscontinuityReason;
  private long pendingDiscontinuityMs;
  private volatile boolean released;

  public MpvPlayerInfo() {
    playbackParameters = PlaybackParameters.DEFAULT;
    audioAttributes = AudioAttributes.DEFAULT;
    playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
    playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    volume = 1.0f;
    pendingDiscontinuityReason = C.INDEX_UNSET;
  }

  public MpvStateBuilder.Snapshot createStateSnapshot(
      TrackSelectionParameters trackSelectionParameters, boolean newlyRenderedFirstFrame) {
    return new MpvStateBuilder.Snapshot(
        playerError,
        playWhenReady,
        playWhenReadyChangeReason,
        playbackSuppressionReason,
        repeatMode,
        playbackParameters,
        audioAttributes,
        trackSelectionParameters,
        newlyRenderedFirstFrame,
        volume,
        audioOffsetMs,
        textOffsetMs);
  }

  public boolean hasPlayerError() {
    return playerError != null;
  }

  public void setPlayerError(PlaybackException playerError) {
    this.playerError = playerError;
  }

  public void clearPlayerError() {
    playerError = null;
  }

  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    this.playbackParameters = playbackParameters;
  }

  public void setAudioAttributes(AudioAttributes audioAttributes) {
    this.audioAttributes = audioAttributes;
  }

  public boolean isPlayWhenReady() {
    return playWhenReady;
  }

  public boolean shouldPlay() {
    return playWhenReady && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  public void setPlayWhenReady(
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int changeReason,
      @Player.PlaybackSuppressionReason int suppressionReason) {
    this.playWhenReady = playWhenReady;
    this.playWhenReadyChangeReason = changeReason;
    this.playbackSuppressionReason = suppressionReason;
  }

  public void setPlaybackSuppressionReason(
      @Player.PlaybackSuppressionReason int suppressionReason) {
    playbackSuppressionReason = suppressionReason;
  }

  public @Player.PlaybackSuppressionReason int getPlaybackSuppressionReason() {
    return playbackSuppressionReason;
  }

  public @Player.RepeatMode int getRepeatMode() {
    return repeatMode;
  }

  public void setRepeatMode(@Player.RepeatMode int repeatMode) {
    this.repeatMode = repeatMode;
  }

  public float getVolume() {
    return volume;
  }

  public void setVolume(float volume) {
    this.volume = volume;
  }

  public long getAudioOffsetMs() {
    return audioOffsetMs;
  }

  public void setAudioOffsetMs(long audioOffsetMs) {
    this.audioOffsetMs = audioOffsetMs;
  }

  public long getTextOffsetMs() {
    return textOffsetMs;
  }

  public void setTextOffsetMs(long textOffsetMs) {
    this.textOffsetMs = textOffsetMs;
  }

  public boolean hasPendingDiscontinuity() {
    return pendingDiscontinuityReason != C.INDEX_UNSET;
  }

  public @Player.DiscontinuityReason int pendingDiscontinuityReason() {
    return pendingDiscontinuityReason;
  }

  public long pendingDiscontinuityMs() {
    return pendingDiscontinuityMs;
  }

  public void setPendingDiscontinuity(@Player.DiscontinuityReason int reason, long positionMs) {
    pendingDiscontinuityReason = reason;
    pendingDiscontinuityMs = positionMs;
  }

  public void clearPendingDiscontinuity() {
    pendingDiscontinuityReason = C.INDEX_UNSET;
  }

  public boolean isReleased() {
    return released;
  }

  public boolean markReleased() {
    if (released) {
      return false;
    }
    released = true;
    return true;
  }
}
