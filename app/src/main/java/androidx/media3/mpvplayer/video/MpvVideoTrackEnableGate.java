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
package androidx.media3.mpvplayer.video;

import androidx.media3.common.C;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.mpvplayer.core.MpvPlaybackState;
import androidx.media3.mpvplayer.trackselection.MpvTrackController;

public final class MpvVideoTrackEnableGate {

  private final MpvPlaybackState playbackState;
  private final MpvTrackController trackController;
  private final MpvVideoState videoState;
  private final Runnable invalidateState;

  private boolean waitingForFirstFrame;

  public MpvVideoTrackEnableGate(
      MpvPlaybackState playbackState,
      MpvTrackController trackController,
      MpvVideoState videoState,
      Runnable invalidateState) {
    this.playbackState = playbackState;
    this.trackController = trackController;
    this.videoState = videoState;
    this.invalidateState = invalidateState;
  }

  private static boolean isVideoEnabled(TrackSelectionParameters parameters) {
    if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) {
      return false;
    }
    for (TrackSelectionOverride override : parameters.overrides.values()) {
      if (override.getType() == C.TRACK_TYPE_VIDEO) {
        return !override.trackIndices.isEmpty();
      }
    }
    return true;
  }

  public void onParametersChanging(TrackSelectionParameters parameters) {
    waitingForFirstFrame = shouldWaitForFirstFrame(parameters);
  }

  public void onTrackSelectionApplied() {
    if (trackController.isTrackTypeSelected(C.TRACK_TYPE_VIDEO)) {
      maybeCompletePendingEnable();
    } else {
      waitingForFirstFrame = false;
    }
  }

  public void maybeCompletePendingEnable() {
    if (!waitingForFirstFrame || !maybeMarkReadyVideoFrame()) {
      return;
    }
    waitingForFirstFrame = false;
    invalidateState.run();
  }

  public void clear() {
    waitingForFirstFrame = false;
  }

  private boolean maybeMarkReadyVideoFrame() {
    if (!playbackState.isReady()
        || !trackController.isTrackTypeSelected(C.TRACK_TYPE_VIDEO)
        || !videoState.hasKnownVideoSize()) {
      return false;
    }
    videoState.markRenderedFirstFrame();
    return true;
  }

  private boolean shouldWaitForFirstFrame(TrackSelectionParameters parameters) {
    return !trackController.isTrackTypeSelected(C.TRACK_TYPE_VIDEO)
        && trackController.haveTrack(C.TRACK_TYPE_VIDEO)
        && isVideoEnabled(parameters);
  }
}
