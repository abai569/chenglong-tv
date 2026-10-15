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
package androidx.media3.mpvplayer.audio;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioBecomingNoisyManager;
import androidx.media3.common.audio.AudioFocusManager;
import androidx.media3.common.util.Clock;

public final class MpvAudioFocusManager {

  private static final float DEFAULT_VOLUME_MULTIPLIER = 1.0f;

  private final AudioBecomingNoisyManager noisyManager;
  private final AudioFocusManager focusManager;
  private final Host host;

  private boolean resumeOnAudioFocusGain;
  private float volumeMultiplier;

  public MpvAudioFocusManager(Context context, Handler handler, Host host) {
    this.host = host;
    this.volumeMultiplier = DEFAULT_VOLUME_MULTIPLIER;
    this.focusManager = new AudioFocusManager(context, handler.getLooper(), createPlayerControl());
    setAudioAttributes(AudioAttributes.DEFAULT);
    this.noisyManager =
        new AudioBecomingNoisyManager(
            context,
            handler.getLooper(),
            handler.getLooper(),
            this::onAudioBecomingNoisy,
            Clock.DEFAULT);
  }

  public void setAudioAttributes(@Nullable AudioAttributes audioAttributes) {
    focusManager.setAudioAttributes(audioAttributes);
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    if (!playWhenReady) {
      release();
      return;
    }
    requestForPlayback();
  }

  public boolean requestForPlayback() {
    if (!host.isInitialized()) {
      return true;
    }
    return applyPlayerCommand(focusManager.updateAudioFocus(true, host.getPlaybackState()), true);
  }

  public void release() {
    resumeOnAudioFocusGain = false;
    noisyManager.setEnabled(false);
    focusManager.updateAudioFocus(false, Player.STATE_IDLE);
    restoreVolumeMultiplier();
  }

  public float getVolumeMultiplier() {
    return volumeMultiplier;
  }

  public boolean isResumeOnAudioFocusGain() {
    return resumeOnAudioFocusGain;
  }

  private AudioFocusManager.PlayerControl createPlayerControl() {
    return new AudioFocusManager.PlayerControl() {
      @Override
      public void setVolumeMultiplier(float volumeMultiplier) {
        host.runOnPlayerLooper(
            () -> {
              if (MpvAudioFocusManager.this.volumeMultiplier == volumeMultiplier) {
                return;
              }
              MpvAudioFocusManager.this.volumeMultiplier = volumeMultiplier;
              host.setVolumeProperty();
            });
      }

      @Override
      public void executePlayerCommand(int playerCommand) {
        host.runOnPlayerLooper(() -> applyPlayerCommand(playerCommand, false));
      }
    };
  }

  private void onAudioBecomingNoisy() {
    host.runOnPlayerLooper(
        () ->
            pauseForInterruption(
                false,
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
                Player.PLAYBACK_SUPPRESSION_REASON_NONE));
  }

  private boolean applyPlayerCommand(int playerCommand, boolean requestedByPlayback) {
    if (playerCommand == AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY) {
      pauseForInterruption(
          false,
          Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
          Player.PLAYBACK_SUPPRESSION_REASON_NONE);
      return false;
    } else if (playerCommand == AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK) {
      pauseForInterruption(
          true,
          Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
          Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
      return false;
    } else if (playerCommand == AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY) {
      if (requestedByPlayback) {
        resumeOnAudioFocusGain = false;
        allowPlaybackAfterFocus();
      } else {
        resumeAfterFocusGain();
      }
      return true;
    }
    return requestedByPlayback;
  }

  private void pauseForInterruption(
      boolean resumeOnGain,
      @Player.PlayWhenReadyChangeReason int changeReason,
      @Player.PlaybackSuppressionReason int suppressionReason) {
    restoreVolumeMultiplier();
    resumeOnAudioFocusGain = resumeOnGain && host.isPlayWhenReady();
    boolean stateChanged = false;
    if (resumeOnGain) {
      if (resumeOnAudioFocusGain && host.getPlaybackSuppressionReason() != suppressionReason) {
        host.setPlaybackSuppressionReason(suppressionReason);
        stateChanged = true;
      }
    } else if (host.isPlayWhenReady() || host.getPlaybackSuppressionReason() != suppressionReason) {
      host.setPlayWhenReady(false, changeReason, suppressionReason);
      stateChanged = true;
    }
    if (stateChanged) {
      host.setPauseProperty();
      host.invalidateState();
    }
    noisyManager.setEnabled(false);
    if (!resumeOnGain) {
      focusManager.updateAudioFocus(false, Player.STATE_IDLE);
    }
  }

  private void resumeAfterFocusGain() {
    if (!resumeOnAudioFocusGain) {
      return;
    }
    resumeOnAudioFocusGain = false;
    allowPlaybackAfterFocus();
  }

  private void allowPlaybackAfterFocus() {
    restoreVolumeMultiplier();
    boolean stateChanged =
        host.getPlaybackSuppressionReason()
            == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    if (stateChanged) {
      host.setPlaybackSuppressionReason(Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    }
    if (host.isInitialized() && host.isPlayWhenReady()) {
      if (stateChanged) {
        host.setPauseProperty();
      }
      noisyManager.setEnabled(true);
    }
    if (stateChanged) {
      host.invalidateState();
    }
  }

  private void restoreVolumeMultiplier() {
    if (volumeMultiplier == DEFAULT_VOLUME_MULTIPLIER) {
      return;
    }
    volumeMultiplier = DEFAULT_VOLUME_MULTIPLIER;
    host.setVolumeProperty();
  }

  public interface Host {

    void runOnPlayerLooper(Runnable runnable);

    boolean isInitialized();

    boolean isPlayWhenReady();

    @Player.PlaybackSuppressionReason
    int getPlaybackSuppressionReason();

    void setPlayWhenReady(
        boolean playWhenReady,
        @Player.PlayWhenReadyChangeReason int changeReason,
        @Player.PlaybackSuppressionReason int suppressionReason);

    void setPlaybackSuppressionReason(@Player.PlaybackSuppressionReason int suppressionReason);

    @Player.State
    int getPlaybackState();

    void setPauseProperty();

    void setVolumeProperty();

    void invalidateState();
  }
}
