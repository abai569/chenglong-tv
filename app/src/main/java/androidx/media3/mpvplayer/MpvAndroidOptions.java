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

import static com.google.common.base.Preconditions.checkState;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.DolbyVisionOutputPolicy;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;

/** Android-specific playback options understood by {@link MpvPlayer}. */
public final class MpvAndroidOptions {

  @Nullable private final File shaderCacheDirectory;
  private final boolean audioPassthroughEnabled;
  private final boolean audioPassthroughEnabledSet;
  private final boolean gpuNextEnabled;
  private final boolean gpuNextEnabledSet;
  private final boolean vulkanEnabled;
  private final boolean vulkanEnabledSet;
  private final @DolbyVisionOutputPolicy.Mode int dolbyVisionOutputPolicy;
  private final boolean dolbyVisionOutputPolicySet;

  private MpvAndroidOptions(Builder builder) {
    shaderCacheDirectory = builder.shaderCacheDirectory;
    audioPassthroughEnabled = builder.audioPassthroughEnabled;
    audioPassthroughEnabledSet = builder.audioPassthroughEnabledSet;
    gpuNextEnabled = builder.gpuNextEnabled;
    gpuNextEnabledSet = builder.gpuNextEnabledSet;
    vulkanEnabled = builder.vulkanEnabled;
    vulkanEnabledSet = builder.vulkanEnabledSet;
    dolbyVisionOutputPolicy = builder.dolbyVisionOutputPolicy;
    dolbyVisionOutputPolicySet = builder.dolbyVisionOutputPolicySet;
  }

  @Nullable
  public File getShaderCacheDirectory() {
    return shaderCacheDirectory;
  }

  public boolean isAudioPassthroughEnabled() {
    return audioPassthroughEnabled;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public boolean isAudioPassthroughEnabledSet() {
    return audioPassthroughEnabledSet;
  }

  public boolean isGpuNextEnabled() {
    return gpuNextEnabled;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public boolean isGpuNextEnabledSet() {
    return gpuNextEnabledSet;
  }

  public boolean isVulkanEnabled() {
    return vulkanEnabled;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public boolean isVulkanEnabledSet() {
    return vulkanEnabledSet;
  }

  public @DolbyVisionOutputPolicy.Mode int getDolbyVisionOutputPolicy() {
    return dolbyVisionOutputPolicy;
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public boolean isDolbyVisionOutputPolicySet() {
    return dolbyVisionOutputPolicySet;
  }

  /** Builder for {@link MpvAndroidOptions}. */
  public static final class Builder {

    @Nullable private File shaderCacheDirectory;
    private boolean audioPassthroughEnabled;
    private boolean audioPassthroughEnabledSet;
    private boolean gpuNextEnabled;
    private boolean gpuNextEnabledSet;
    private boolean vulkanEnabled;
    private boolean vulkanEnabledSet;
    private @DolbyVisionOutputPolicy.Mode int dolbyVisionOutputPolicy;
    private boolean dolbyVisionOutputPolicySet;
    private boolean buildCalled;

    public Builder() {
      dolbyVisionOutputPolicy = DolbyVisionOutputPolicy.AUTO;
    }

    @CanIgnoreReturnValue
    public Builder setShaderCacheDirectory(@Nullable File shaderCacheDirectory) {
      checkState(!buildCalled);
      this.shaderCacheDirectory = shaderCacheDirectory;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAudioPassthroughEnabled(boolean audioPassthroughEnabled) {
      checkState(!buildCalled);
      this.audioPassthroughEnabled = audioPassthroughEnabled;
      audioPassthroughEnabledSet = true;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setGpuNextEnabled(boolean gpuNextEnabled) {
      checkState(!buildCalled);
      this.gpuNextEnabled = gpuNextEnabled;
      gpuNextEnabledSet = true;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setVulkanEnabled(boolean vulkanEnabled) {
      checkState(!buildCalled);
      this.vulkanEnabled = vulkanEnabled;
      vulkanEnabledSet = true;
      return this;
    }

    /**
     * Sets the policy used to decide whether native Dolby Vision output may be sent to the current
     * display. When native output is allowed, direct MediaCodec hardware decoding and both
     * SurfaceView-backed video and subtitle outputs must be available. Dolby Vision tracks then
     * render directly from MediaCodec to the video surface. Subtitles and mpv OSD remain
     * mpv-rendered on the separate overlay surface, while video effects, screenshots, and GPU tone
     * mapping are unavailable for those tracks. Dolby Vision tracks that cannot use direct output
     * fall back to the configured video output. Other video tracks continue to use that output.
     */
    @CanIgnoreReturnValue
    public Builder setDolbyVisionOutputPolicy(
        @DolbyVisionOutputPolicy.Mode int dolbyVisionOutputPolicy) {
      checkState(!buildCalled);
      this.dolbyVisionOutputPolicy = dolbyVisionOutputPolicy;
      dolbyVisionOutputPolicySet = true;
      return this;
    }

    public MpvAndroidOptions build() {
      checkState(!buildCalled);
      buildCalled = true;
      return new MpvAndroidOptions(this);
    }
  }
}
