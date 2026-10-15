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
package androidx.media3.mpvplayer.trackselection;

import androidx.media3.common.C;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;

public final class MpvTrackController {

  private final Host host;
  private final MpvTrackReader trackReader;
  private final MpvTrackSelectionApplier trackSelectionApplier;
  private TrackSelectionParameters parameters;
  private boolean parametersPending;
  private boolean currentFileLoaded;
  private Tracks tracks;

  public MpvTrackController(Host host, MpvPropertyAccessor properties) {
    this.host = host;
    this.trackReader = new MpvTrackReader(properties);
    this.trackSelectionApplier = new MpvTrackSelectionApplier(properties);
    this.parameters = TrackSelectionParameters.DEFAULT;
    this.parametersPending = false;
    this.currentFileLoaded = false;
    this.tracks = Tracks.EMPTY;
  }

  public TrackSelectionParameters getParameters() {
    return parameters;
  }

  public void setParameters(TrackSelectionParameters parameters) {
    TrackSelectionParameters previousParameters = this.parameters;
    this.parameters = parameters;
    if (host.isInitialized() && !tracks.getGroups().isEmpty()) {
      parametersPending = false;
      trackSelectionApplier.applyChanged(previousParameters, parameters, tracks);
    } else {
      parametersPending = trackSelectionApplier.requiresNewFileApplication(parameters);
    }
    maybeResetFirstFrame();
  }

  public Tracks getTracks() {
    return tracks;
  }

  public Tracks readTracks() {
    return host.isInitialized() ? trackReader.read() : Tracks.EMPTY;
  }

  public boolean updateTracks(Tracks tracks) {
    if (this.tracks.equals(tracks)) {
      return false;
    }
    this.tracks = tracks;
    if (currentFileLoaded) {
      applyPendingParameters();
    }
    maybeResetFirstFrame();
    return true;
  }

  public void onFileLoaded() {
    currentFileLoaded = true;
    applyPendingParameters();
    maybeResetFirstFrame();
  }

  public void clear() {
    currentFileLoaded = false;
    tracks = Tracks.EMPTY;
    parametersPending = trackSelectionApplier.requiresNewFileApplication(parameters);
  }

  public void onNativeSessionEnded() {
    trackSelectionApplier.reset();
    clear();
  }

  public boolean haveTrack(int type) {
    return tracks.containsType(type);
  }

  public boolean isTrackTypeSelected(int type) {
    return tracks.isTypeSelected(type);
  }

  private void applyPendingParameters() {
    if (!parametersPending || !host.isInitialized() || tracks.getGroups().isEmpty()) {
      return;
    }
    trackSelectionApplier.applyForNewFile(parameters, tracks);
    parametersPending = false;
  }

  private boolean hasUnselectedVideoTracks() {
    return tracks.containsType(C.TRACK_TYPE_VIDEO) && !isTrackTypeSelected(C.TRACK_TYPE_VIDEO);
  }

  private void maybeResetFirstFrame() {
    if (hasUnselectedVideoTracks()) {
      host.resetRenderedFirstFrame();
    }
  }

  public interface Host {

    boolean isInitialized();

    void resetRenderedFirstFrame();
  }
}
