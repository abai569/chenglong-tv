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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_TIME_POS;

import androidx.annotation.Nullable;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import androidx.media3.mpvplayer.video.MpvVideoState;
import androidx.media3.mpvplayer.video.MpvVideoTrackEnableGate;

public final class MpvPlaybackPropertyUpdater {

  private final MpvPropertyAccessor properties;
  private final MpvPlaybackState playbackState;
  private final MpvVideoState videoState;
  private final MpvVideoTrackEnableGate videoTrackEnableGate;

  public MpvPlaybackPropertyUpdater(
      MpvPropertyAccessor properties,
      MpvPlaybackState playbackState,
      MpvVideoState videoState,
      MpvVideoTrackEnableGate videoTrackEnableGate) {
    this.properties = properties;
    this.playbackState = playbackState;
    this.videoState = videoState;
    this.videoTrackEnableGate = videoTrackEnableGate;
  }

  public @Nullable Double readPosition() {
    return properties.getDouble(PROP_TIME_POS);
  }

  public void updatePosition(@Nullable Double positionSeconds) {
    if (positionSeconds != null && Double.isFinite(positionSeconds)) {
      playbackState.onPositionProperty(positionSeconds);
    }
  }

  public void onVideoWidthProperty(long width) {
    videoState.updateWidth((int) width);
    videoTrackEnableGate.maybeCompletePendingEnable();
  }

  public void onVideoHeightProperty(long height) {
    videoState.updateHeight((int) height);
    videoTrackEnableGate.maybeCompletePendingEnable();
  }

  public void onVideoAspectProperty(double aspect) {
    videoState.updateAspect(aspect);
    videoTrackEnableGate.maybeCompletePendingEnable();
  }

  public void onVideoRotationProperty(long rotation) {
    videoState.updateRotation((int) rotation);
    videoTrackEnableGate.maybeCompletePendingEnable();
  }

  public void onAlbumArtProperty(boolean albumArt) {
    videoState.updateAlbumArt(albumArt);
    videoTrackEnableGate.maybeCompletePendingEnable();
  }
}
