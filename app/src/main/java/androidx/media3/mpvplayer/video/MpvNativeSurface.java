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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.OPT_FORCE_WINDOW;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_ANDROID_SURFACE_SIZE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_YES;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Size;
import androidx.media3.mpvplayer.nativebridge.MpvClient;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;

final class MpvNativeSurface {

  private static final SurfaceChange UNCHANGED = new SurfaceChange(false, false);

  private final MpvClient client;
  private final MpvSurfaceController.Host host;

  @Nullable private Surface surface;
  private Size surfaceSize;

  MpvNativeSurface(MpvClient client, MpvSurfaceController.Host host) {
    this.client = client;
    this.host = host;
    this.surfaceSize = Size.UNKNOWN;
  }

  static boolean isSurfaceUsable(@Nullable Surface surface) {
    return surface != null && surface.isValid();
  }

  Size getSurfaceSize() {
    return surfaceSize;
  }

  void addPerFileOptions(MpvPerFileOptions options) {
    if (surface != null) {
      options.add(OPT_FORCE_WINDOW, VALUE_YES);
    }
    if (isSurfaceSizeKnown()) {
      options.add(PROP_ANDROID_SURFACE_SIZE, surfaceSize.toString());
    }
  }

  void onNativeSessionEnded() {
    surface = null;
  }

  SurfaceChange setSurface(@Nullable Surface surface) {
    Surface nextSurface = isSurfaceUsable(surface) ? surface : null;
    Surface previousSurface = this.surface;
    if (previousSurface == nextSurface) {
      return UNCHANGED;
    }
    boolean replacingSurface = previousSurface != null && nextSurface != null;
    boolean detachingSurface = previousSurface != null && nextSurface == null;
    if (detachingSurface && !detachSurfaceFromMpv()) {
      return UNCHANGED;
    }
    if (nextSurface != null && !applySurfaceToMpv(nextSurface, replacingSurface)) {
      return UNCHANGED;
    }
    this.surface = nextSurface;
    return new SurfaceChange(true, replacingSurface || detachingSurface);
  }

  boolean setSurfaceSize(Size surfaceSize) {
    if (this.surfaceSize.equals(surfaceSize)) {
      return false;
    }
    this.surfaceSize = surfaceSize;
    applySurfaceSizeToMpv();
    return true;
  }

  private boolean detachSurfaceFromMpv() {
    if (!host.isInitialized()) {
      return true;
    }
    return client.detachSurface();
  }

  private boolean applySurfaceToMpv(Surface nextSurface, boolean replaceExistingSurface) {
    if (!host.isInitialized()) {
      return true;
    }
    boolean surfaceApplied =
        replaceExistingSurface
            ? client.replaceSurface(nextSurface)
            : client.attachSurface(nextSurface);
    if (!surfaceApplied) {
      return false;
    }
    applySurfaceSizeToMpv();
    client.setPropertyString(OPT_FORCE_WINDOW, VALUE_YES);
    return true;
  }

  private void applySurfaceSizeToMpv() {
    if (!host.isInitialized() || !isSurfaceSizeKnown()) {
      return;
    }
    client.setPropertyString(PROP_ANDROID_SURFACE_SIZE, surfaceSize.toString());
  }

  private boolean isSurfaceSizeKnown() {
    return surfaceSize.getWidth() > 0 && surfaceSize.getHeight() > 0;
  }

  static final class SurfaceChange {

    private final boolean changed;
    private final boolean resetRenderedFirstFrame;

    private SurfaceChange(boolean changed, boolean resetRenderedFirstFrame) {
      this.changed = changed;
      this.resetRenderedFirstFrame = resetRenderedFirstFrame;
    }

    boolean changed() {
      return changed;
    }

    boolean resetsFirstFrame() {
      return resetRenderedFirstFrame;
    }
  }
}
