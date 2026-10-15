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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_ANDROID_OSD_SURFACE_SIZE;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Size;
import androidx.media3.mpvplayer.nativebridge.MpvClient;

final class MpvNativeOsdSurface {

  private final MpvClient client;
  private final MpvSurfaceController.Host host;

  @Nullable private Surface surface;
  private Size surfaceSize;
  private Size appliedSurfaceSize;

  MpvNativeOsdSurface(MpvClient client, MpvSurfaceController.Host host) {
    this.client = client;
    this.host = host;
    this.surfaceSize = Size.UNKNOWN;
    this.appliedSurfaceSize = Size.UNKNOWN;
  }

  void onNativeSessionEnded() {
    surface = null;
    appliedSurfaceSize = Size.UNKNOWN;
  }

  boolean setSurface(@Nullable Surface surface) {
    Surface nextSurface = MpvNativeSurface.isSurfaceUsable(surface) ? surface : null;
    Surface previousSurface = this.surface;
    if (previousSurface == nextSurface) {
      return false;
    }
    if (host.isInitialized()) {
      boolean applied;
      if (nextSurface == null) {
        applied = client.detachOsdSurface();
      } else if (previousSurface == null) {
        applied = client.attachOsdSurface(nextSurface);
      } else {
        applied = client.replaceOsdSurface(nextSurface);
      }
      if (!applied) {
        return false;
      }
      if (nextSurface != null) {
        applySurfaceSizeToMpv();
      }
    }
    this.surface = nextSurface;
    return true;
  }

  void setSurfaceSize(Size surfaceSize) {
    if (this.surfaceSize.equals(surfaceSize)) {
      return;
    }
    this.surfaceSize = surfaceSize;
    applySurfaceSizeToMpv();
  }

  private void applySurfaceSizeToMpv() {
    if (!host.isInitialized()
        || surfaceSize.getWidth() <= 0
        || surfaceSize.getHeight() <= 0
        || surfaceSize.equals(appliedSurfaceSize)) {
      return;
    }
    client.setPropertyString(PROP_ANDROID_OSD_SURFACE_SIZE, surfaceSize.toString());
    appliedSurfaceSize = surfaceSize;
  }
}
