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
package androidx.media3.mpvplayer;

import android.view.Display;
import androidx.annotation.Nullable;
import androidx.media3.mpvplayer.audio.MpvAudioFocusManager;
import androidx.media3.mpvplayer.trackselection.MpvTrackController;
import androidx.media3.mpvplayer.video.MpvSurfaceController;
import androidx.media3.mpvplayer.video.MpvVideoState;

final class MpvComponentListener
    implements MpvSurfaceController.Host,
        MpvAudioFocusManager.Host,
        MpvTrackController.Host,
        MpvVideoState.Host {

  private final MpvPlayer player;

  MpvComponentListener(MpvPlayer player) {
    this.player = player;
  }

  @Override
  public void runOnPlayerLooper(Runnable runnable) {
    player.runOnPlayerLooper(runnable);
  }

  @Override
  public void runOnPlayerLooperAndWait(Runnable runnable) {
    player.runOnPlayerLooperAndWait(runnable);
  }

  @Override
  public boolean isInitialized() {
    return player.isMpvInitialized();
  }

  @Override
  public void invalidateState() {
    player.invalidatePlayerState();
  }

  @Override
  public void resetRenderedFirstFrame() {
    player.resetRenderedFirstFrame();
  }

  @Override
  public void setDirectVideoDisplay(@Nullable Display display) {
    player.setDirectVideoDisplay(display);
  }

  @Override
  public void setDirectVideoOutputConfigured(boolean configured) {
    player.setDirectVideoOutputConfigured(configured);
  }

  @Override
  public void setDirectOsdOutputConfigured(boolean configured) {
    player.setDirectOsdOutputConfigured(configured);
  }

  @Override
  public boolean isPlayWhenReady() {
    return player.isPlayWhenReadyInternal();
  }

  @Override
  public int getPlaybackSuppressionReason() {
    return player.getPlaybackSuppressionReasonInternal();
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady, int changeReason, int suppressionReason) {
    player.setPlayWhenReadyInternal(playWhenReady, changeReason, suppressionReason);
  }

  @Override
  public void setPlaybackSuppressionReason(int suppressionReason) {
    player.setPlaybackSuppressionReasonInternal(suppressionReason);
  }

  @Override
  public int getPlaybackState() {
    return player.getInternalPlaybackState();
  }

  @Override
  public void setPauseProperty() {
    player.setPauseProperty();
  }

  @Override
  public void setVolumeProperty() {
    player.setVolumeProperty();
  }

  @Override
  public boolean hasVideoTrack() {
    return player.hasInternalVideoTrack();
  }

  @Override
  public boolean isVideoTrackSelected() {
    return player.isInternalVideoTrackSelected();
  }
}
