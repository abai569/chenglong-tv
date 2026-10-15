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

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Size;

final class MpvSurfaceTarget implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {

  private static final Size ZERO_SIZE = new Size(0, 0);

  private final Host host;

  @Nullable private Surface ownedSurface;
  @Nullable private SurfaceHolder surfaceHolder;
  @Nullable private TextureView textureView;
  private boolean holderSurfaceIsOutput;

  MpvSurfaceTarget(Host host) {
    this.host = host;
  }

  private static Size getSurfaceHolderSize(SurfaceHolder surfaceHolder) {
    Rect frame = surfaceHolder.getSurfaceFrame();
    return new Size(frame.width(), frame.height());
  }

  private static boolean isSurfaceUsable(@Nullable Surface surface) {
    return surface != null && surface.isValid();
  }

  void setSurface(@Nullable Surface surface) {
    removeCallbacks();
    setOutputSurface(surface, false);
    host.onSurfaceSizeChanged(Size.UNKNOWN);
  }

  void setSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    if (surfaceHolder == null) {
      clear();
      return;
    }
    removeCallbacks();
    holderSurfaceIsOutput = true;
    this.surfaceHolder = surfaceHolder;
    surfaceHolder.addCallback(this);
    Surface surface = surfaceHolder.getSurface();
    if (isSurfaceUsable(surface)) {
      host.onSurfaceSizeChanged(getSurfaceHolderSize(surfaceHolder));
      setOutputSurface(surface, false);
    } else {
      setOutputSurface(null, false);
      host.onSurfaceSizeChanged(ZERO_SIZE);
    }
  }

  void setSurfaceView(@Nullable SurfaceView surfaceView) {
    setSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  void setTextureView(@Nullable TextureView textureView) {
    if (textureView == null) {
      clear();
      return;
    }
    removeCallbacks();
    this.textureView = textureView;
    textureView.setSurfaceTextureListener(this);
    SurfaceTexture surfaceTexture =
        textureView.isAvailable() ? textureView.getSurfaceTexture() : null;
    if (surfaceTexture == null) {
      setOutputSurface(null, false);
      host.onSurfaceSizeChanged(ZERO_SIZE);
    } else {
      host.onSurfaceSizeChanged(new Size(textureView.getWidth(), textureView.getHeight()));
      setSurfaceTexture(surfaceTexture);
    }
  }

  void clear() {
    removeCallbacks();
    setOutputSurface(null, false);
    host.onSurfaceSizeChanged(ZERO_SIZE);
  }

  void prepareForRelease() {
    removeCallbacks();
  }

  void onNativeSessionEnded() {
    removeCallbacks();
    releaseOwnedSurface();
  }

  @Override
  public void surfaceCreated(@NonNull SurfaceHolder holder) {
    runIfCurrentSurfaceHolder(
        holder,
        () -> {
          host.onSurfaceSizeChanged(getSurfaceHolderSize(holder));
          if (holderSurfaceIsOutput) {
            setOutputSurface(holder.getSurface(), false);
          }
        });
  }

  @Override
  public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    runIfCurrentSurfaceHolder(holder, () -> host.onSurfaceSizeChanged(new Size(width, height)));
  }

  @Override
  public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
    if (!isCurrentSurfaceHolder(holder)) {
      return;
    }
    host.runOnPlayerLooperAndWait(
        () -> {
          if (isCurrentSurfaceHolder(holder)) {
            if (holderSurfaceIsOutput) {
              setOutputSurface(null, false);
            }
            host.onSurfaceSizeChanged(ZERO_SIZE);
          }
        });
  }

  @Override
  public void onSurfaceTextureAvailable(
      @NonNull SurfaceTexture surfaceTexture, int width, int height) {
    runIfCurrentSurfaceTexture(
        surfaceTexture,
        () -> {
          host.onSurfaceSizeChanged(new Size(width, height));
          setSurfaceTexture(surfaceTexture);
        });
  }

  @Override
  public void onSurfaceTextureSizeChanged(
      @NonNull SurfaceTexture surfaceTexture, int width, int height) {
    runIfCurrentSurfaceTexture(
        surfaceTexture, () -> host.onSurfaceSizeChanged(new Size(width, height)));
  }

  @Override
  public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
    if (!isCurrentSurfaceTexture(surfaceTexture)) {
      return true;
    }
    host.runOnPlayerLooperAndWait(
        () -> {
          if (isCurrentSurfaceTexture(surfaceTexture)) {
            setOutputSurface(null, false);
            host.onSurfaceSizeChanged(ZERO_SIZE);
          }
        });
    return true;
  }

  @Override
  public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}

  private void setSurfaceTexture(SurfaceTexture surfaceTexture) {
    setOutputSurface(new Surface(surfaceTexture), true);
  }

  private void setOutputSurface(@Nullable Surface surface, boolean ownsSurface) {
    Surface previousOwnedSurface = ownedSurface;
    Surface nextOwnedSurface = ownsSurface ? surface : null;
    if (!host.onSurfaceChanged(surface)) {
      if (nextOwnedSurface != null && nextOwnedSurface != previousOwnedSurface) {
        nextOwnedSurface.release();
      }
      return;
    }
    if (previousOwnedSurface != null && previousOwnedSurface != nextOwnedSurface) {
      previousOwnedSurface.release();
    }
    ownedSurface = nextOwnedSurface;
  }

  private void releaseOwnedSurface() {
    if (ownedSurface == null) {
      return;
    }
    ownedSurface.release();
    ownedSurface = null;
  }

  private void removeCallbacks() {
    if (textureView != null) {
      if (textureView.getSurfaceTextureListener() == this) {
        textureView.setSurfaceTextureListener(null);
      }
      textureView = null;
    }
    if (surfaceHolder != null) {
      surfaceHolder.removeCallback(this);
      surfaceHolder = null;
    }
    holderSurfaceIsOutput = false;
  }

  private boolean isCurrentSurfaceHolder(SurfaceHolder holder) {
    return holder == surfaceHolder;
  }

  private boolean isCurrentSurfaceTexture(SurfaceTexture surfaceTexture) {
    return textureView != null && textureView.getSurfaceTexture() == surfaceTexture;
  }

  private void runIfCurrentSurfaceHolder(SurfaceHolder holder, Runnable action) {
    if (!isCurrentSurfaceHolder(holder)) {
      return;
    }
    host.runOnPlayerLooper(
        () -> {
          if (isCurrentSurfaceHolder(holder)) {
            action.run();
          }
        });
  }

  private void runIfCurrentSurfaceTexture(SurfaceTexture surfaceTexture, Runnable action) {
    if (!isCurrentSurfaceTexture(surfaceTexture)) {
      return;
    }
    host.runOnPlayerLooper(
        () -> {
          if (isCurrentSurfaceTexture(surfaceTexture)) {
            action.run();
          }
        });
  }

  interface Host {

    void runOnPlayerLooper(Runnable runnable);

    void runOnPlayerLooperAndWait(Runnable runnable);

    boolean onSurfaceChanged(@Nullable Surface surface);

    void onSurfaceSizeChanged(Size size);
  }
}
