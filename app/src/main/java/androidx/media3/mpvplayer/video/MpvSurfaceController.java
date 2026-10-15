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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.Size;
import androidx.media3.mpvplayer.nativebridge.MpvClient;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;

public final class MpvSurfaceController {

  private final MpvNativeSurface nativeSurface;
  private final MpvSurfaceTarget surfaceTarget;
  private final MpvNativeOsdSurface nativeOsdSurface;
  private final MpvSurfaceTarget osdSurfaceTarget;
  private final Host host;

  @Nullable private Object videoOutput;
  @Nullable private SurfaceView osdOutput;

  public MpvSurfaceController(MpvClient client, Host host) {
    this.nativeSurface = new MpvNativeSurface(client, host);
    this.surfaceTarget = new MpvSurfaceTarget(new TargetHost());
    this.nativeOsdSurface = new MpvNativeOsdSurface(client, host);
    this.osdSurfaceTarget = new MpvSurfaceTarget(new OsdTargetHost());
    this.host = host;
  }

  public Size getSurfaceSize() {
    return nativeSurface.getSurfaceSize();
  }

  @RestrictTo(LIBRARY_GROUP)
  public void addPerFileOptions(MpvPerFileOptions options) {
    nativeSurface.addPerFileOptions(options);
  }

  public void setVideoOutput(Object videoOutput) {
    this.videoOutput = videoOutput;
    host.setDirectVideoOutputConfigured(videoOutput instanceof SurfaceView);
    updateDirectVideoDisplay();
    if (host.isInitialized()) {
      applyVideoOutput(videoOutput);
    }
  }

  private void applyVideoOutput(Object videoOutput) {
    if (videoOutput instanceof SurfaceView) {
      surfaceTarget.setSurfaceView((SurfaceView) videoOutput);
    } else if (videoOutput instanceof TextureView) {
      surfaceTarget.setTextureView((TextureView) videoOutput);
    } else if (videoOutput instanceof SurfaceHolder) {
      surfaceTarget.setSurfaceHolder((SurfaceHolder) videoOutput);
    } else if (videoOutput instanceof Surface) {
      surfaceTarget.setSurface((Surface) videoOutput);
    } else {
      surfaceTarget.clear();
    }
  }

  public boolean clearVideoOutput(@Nullable Object videoOutput) {
    if (videoOutput != null && videoOutput != this.videoOutput) {
      return false;
    }
    host.setDirectVideoOutputConfigured(false);
    surfaceTarget.clear();
    this.videoOutput = null;
    return true;
  }

  public void setOsdOutput(SurfaceView osdOutput) {
    this.osdOutput = osdOutput;
    host.setDirectOsdOutputConfigured(true);
    if (host.isInitialized()) {
      osdSurfaceTarget.setSurfaceView(osdOutput);
    }
  }

  public void clearOsdOutput(@Nullable SurfaceView osdOutput) {
    if (osdOutput != null && osdOutput != this.osdOutput) {
      return;
    }
    host.setDirectOsdOutputConfigured(false);
    osdSurfaceTarget.clear();
    this.osdOutput = null;
  }

  public void onInitialized() {
    if (videoOutput != null) {
      setVideoOutput(videoOutput);
    }
    if (osdOutput != null) {
      host.setDirectOsdOutputConfigured(true);
      osdSurfaceTarget.setSurfaceView(osdOutput);
    }
  }

  @RestrictTo(LIBRARY_GROUP)
  public void onNativeSessionEnded() {
    host.setDirectVideoOutputConfigured(false);
    host.setDirectOsdOutputConfigured(false);
    nativeSurface.onNativeSessionEnded();
    nativeOsdSurface.onNativeSessionEnded();
    osdSurfaceTarget.onNativeSessionEnded();
    surfaceTarget.onNativeSessionEnded();
  }

  public void release() {
    if (!host.isInitialized()) {
      onNativeSessionEnded();
      return;
    }
    osdSurfaceTarget.prepareForRelease();
    surfaceTarget.prepareForRelease();
  }

  private boolean setSurfaceInternal(@Nullable Surface surface) {
    MpvNativeSurface.SurfaceChange change = nativeSurface.setSurface(surface);
    if (!change.changed()) {
      return false;
    }
    if (change.resetsFirstFrame()) {
      host.resetRenderedFirstFrame();
    }
    return true;
  }

  private void updateSurfaceSize(Size size) {
    if (nativeSurface.setSurfaceSize(size)) {
      host.invalidateState();
    }
  }

  private void updateDirectVideoDisplay() {
    @Nullable
    Display display =
        videoOutput instanceof SurfaceView ? ((SurfaceView) videoOutput).getDisplay() : null;
    host.setDirectVideoDisplay(display);
  }

  public interface Host {

    void runOnPlayerLooper(Runnable runnable);

    void runOnPlayerLooperAndWait(Runnable runnable);

    boolean isInitialized();

    void resetRenderedFirstFrame();

    void setDirectVideoDisplay(@Nullable Display display);

    void setDirectVideoOutputConfigured(boolean configured);

    void setDirectOsdOutputConfigured(boolean configured);

    void invalidateState();
  }

  private final class TargetHost implements MpvSurfaceTarget.Host {

    @Override
    public void runOnPlayerLooper(Runnable runnable) {
      host.runOnPlayerLooper(runnable);
    }

    @Override
    public void runOnPlayerLooperAndWait(Runnable runnable) {
      host.runOnPlayerLooperAndWait(runnable);
    }

    @Override
    public boolean onSurfaceChanged(@Nullable Surface surface) {
      updateDirectVideoDisplay();
      return setSurfaceInternal(surface);
    }

    @Override
    public void onSurfaceSizeChanged(Size size) {
      updateDirectVideoDisplay();
      updateSurfaceSize(size);
    }
  }

  private final class OsdTargetHost implements MpvSurfaceTarget.Host {

    @Override
    public void runOnPlayerLooper(Runnable runnable) {
      host.runOnPlayerLooper(runnable);
    }

    @Override
    public void runOnPlayerLooperAndWait(Runnable runnable) {
      host.runOnPlayerLooperAndWait(runnable);
    }

    @Override
    public boolean onSurfaceChanged(@Nullable Surface surface) {
      return nativeOsdSurface.setSurface(surface);
    }

    @Override
    public void onSurfaceSizeChanged(Size size) {
      nativeOsdSurface.setSurfaceSize(size);
    }
  }
}
