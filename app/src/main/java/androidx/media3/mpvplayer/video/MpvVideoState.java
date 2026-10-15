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

import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Util;

public final class MpvVideoState {

  private final Host host;
  private VideoSize videoSize;
  private boolean renderedFirstFrame;
  private boolean newlyRenderedFirstFrame;
  private int width;
  private int height;
  private int rotation;
  private double aspect;
  private boolean albumArt;

  public MpvVideoState(Host host) {
    this.host = host;
    videoSize = VideoSize.UNKNOWN;
  }

  private static int constrainDimension(int value) {
    return Util.constrainValue(value, 0, Integer.MAX_VALUE);
  }

  public VideoSize getVideoSize() {
    return videoSize;
  }

  public boolean hasKnownVideoSize() {
    return !VideoSize.UNKNOWN.equals(videoSize);
  }

  public boolean consumeFirstFrameEvent() {
    boolean result = newlyRenderedFirstFrame;
    newlyRenderedFirstFrame = false;
    return result;
  }

  public void reset() {
    width = 0;
    height = 0;
    rotation = 0;
    aspect = 0;
    albumArt = false;
    videoSize = VideoSize.UNKNOWN;
    resetRenderedFirstFrame();
  }

  public void updateWidth(int width) {
    this.width = constrainDimension(width);
    updateVideoSize();
  }

  public void updateHeight(int height) {
    this.height = constrainDimension(height);
    updateVideoSize();
  }

  public void updateAspect(double aspect) {
    this.aspect = Double.isFinite(aspect) && aspect > 0 ? aspect : 0;
    updateVideoSize();
  }

  public void updateRotation(int rotation) {
    this.rotation = Math.floorMod(rotation, 360);
    updateVideoSize();
  }

  public void updateProperties(int width, int height, double aspect, int rotation) {
    this.width = constrainDimension(width);
    this.height = constrainDimension(height);
    this.aspect = Double.isFinite(aspect) && aspect > 0 ? aspect : 0;
    this.rotation = Math.floorMod(rotation, 360);
    updateVideoSize();
  }

  public void clearProperties() {
    updateProperties(0, 0, 0, 0);
  }

  public void updateAlbumArt(boolean albumArt) {
    if (this.albumArt == albumArt) {
      return;
    }
    this.albumArt = albumArt;
    if (albumArt) {
      resetRenderedFirstFrame();
    }
    updateVideoSize();
  }

  public boolean isAlbumArt() {
    return albumArt;
  }

  public void markRenderedFirstFrame() {
    if (renderedFirstFrame || !canReportRenderedFirstFrame()) {
      return;
    }
    renderedFirstFrame = true;
    newlyRenderedFirstFrame = true;
  }

  public void resetRenderedFirstFrame() {
    renderedFirstFrame = false;
    newlyRenderedFirstFrame = false;
  }

  private void updateVideoSize() {
    VideoSize oldVideoSize = videoSize;
    if (albumArt || width <= 0 || height <= 0) {
      videoSize = VideoSize.UNKNOWN;
    } else {
      boolean rotated = rotation == 90 || rotation == 270;
      int displayWidth = rotated ? height : width;
      int displayHeight = rotated ? width : height;
      double displayAspect = aspect > 0 ? aspect : (double) width / height;
      if (rotated) {
        displayAspect = 1.0 / displayAspect;
      }
      float pixelRatio = (float) (displayAspect * displayHeight / displayWidth);
      videoSize = new VideoSize(displayWidth, displayHeight, pixelRatio);
    }
    if (oldVideoSize.equals(videoSize)) {
      return;
    }
    host.invalidateState();
  }

  private boolean canReportRenderedFirstFrame() {
    if (albumArt) {
      return false;
    }
    if (host.hasVideoTrack()) {
      return host.isVideoTrackSelected();
    }
    return !VideoSize.UNKNOWN.equals(videoSize);
  }

  public interface Host {

    boolean hasVideoTrack();

    boolean isVideoTrackSelected();

    void invalidateState();
  }
}
