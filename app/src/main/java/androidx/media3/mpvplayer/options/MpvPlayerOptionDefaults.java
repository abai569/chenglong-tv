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
package androidx.media3.mpvplayer.options;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.OPT_FORCE_WINDOW;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.OPT_VIDEO_OUTPUT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_HWDEC;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_NO;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_YES;
import static com.google.common.base.Preconditions.checkArgument;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.RestrictTo;
import androidx.media3.mpvplayer.MpvAndroidOptions;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.mpvplayer.MpvSubtitleOptions;
import java.io.File;

@RestrictTo(LIBRARY_GROUP)
public final class MpvPlayerOptionDefaults {

  private static final String VALUE_AUDIO_OUTPUT = "audiotrack,opensles";
  private static final String VALUE_AUDIO_SPDIF = "ac3,dts-hd,eac3,truehd";
  private static final String VALUE_ANDROID_VK = "androidvk";
  private static final String VALUE_ANDROID = "android";
  private static final String VALUE_FAST = "fast";
  private static final String VALUE_HWDEC = "mediacodec,mediacodec-copy";
  private static final String VALUE_HWDEC_CODECS = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1,vc1";
  private static final String VALUE_VIDEO_OUTPUT_GPU = "gpu";
  private static final String VALUE_VIDEO_OUTPUT_GPU_NEXT = "gpu-next";
  private static final String VALUE_OPENGL = "opengl";
  private static final String VALUE_VULKAN = "vulkan";
  private static final String OPT_AUDIO_OUTPUT = "ao";
  private static final String OPT_CACHE = "cache";
  private static final String OPT_CACHE_ON_DISK = "cache-on-disk";
  private static final String OPT_CACHE_SECS = "cache-secs";
  private static final String OPT_CONFIG = "config";
  private static final String OPT_CONFIG_DIR = "config-dir";
  private static final String OPT_DEMUXER_CACHE_DIR = "demuxer-cache-dir";
  private static final String OPT_GPU_SHADER_CACHE_DIR = "gpu-shader-cache-dir";
  private static final String OPT_GPU_API = "gpu-api";
  private static final String OPT_GPU_CONTEXT = "gpu-context";
  private static final String OPT_HWDEC_CODECS = "hwdec-codecs";
  private static final String OPT_KEEPASPECT = "keepaspect";
  private static final String OPT_PROFILE = "profile";
  private static final String OPT_TLS_CA_FILE = "tls-ca-file";
  private static final String OPT_YTDL = "ytdl";

  public static MpvPlayerConfig.Builder addAndroidDefaults(
      MpvPlayerConfig.Builder builder, MpvAndroidOptions options) {
    addVideoOutputOptions(builder, options);
    addAudioOutputOptions(builder, options);
    addHardwareDecodeOptions(builder);
    addDolbyVisionOptions(builder, options);
    addAndroidNetworkOptions(builder);
    addProfileOption(builder);
    addVulkanOptions(builder, options);
    return builder;
  }

  public static MpvPlayerConfig.Builder addConfigOptions(
      MpvPlayerConfig.Builder builder, File configDirectory) {
    builder
        .addPreInitStringOption(OPT_CONFIG, VALUE_YES)
        .addPreInitStringOption(OPT_CONFIG_DIR, configDirectory.getAbsolutePath());
    return builder;
  }

  public static MpvPlayerConfig.Builder addAndroidFontConfig(
      MpvPlayerConfig.Builder builder, File configDirectory, File cacheDirectory) {
    MpvAndroidFontConfig.writeIfNeeded(configDirectory, cacheDirectory);
    return builder;
  }

  public static MpvPlayerConfig.Builder addTlsCaFileFromAsset(
      MpvPlayerConfig.Builder builder, Context context, String assetName, File outputFile) {
    if (TextUtils.isEmpty(assetName)) {
      return builder;
    }
    MpvAssetFile.copyIfNeeded(context, assetName, outputFile);
    builder.addPreInitStringOption(OPT_TLS_CA_FILE, outputFile.getAbsolutePath());
    return builder;
  }

  public static MpvPlayerConfig.Builder addDiskCacheOptions(
      MpvPlayerConfig.Builder builder, File cacheDirectory, int cacheSeconds) {
    checkArgument(cacheSeconds >= 0);
    builder
        .addPreInitStringOption(OPT_CACHE, VALUE_YES)
        .addPreInitStringOption(OPT_CACHE_ON_DISK, VALUE_YES)
        .addPreInitStringOption(OPT_DEMUXER_CACHE_DIR, cacheDirectory.getAbsolutePath())
        .addPreInitStringOption(OPT_CACHE_SECS, String.valueOf(cacheSeconds));
    builder
        .addAppOwnedStringOption(OPT_CACHE, VALUE_YES)
        .addAppOwnedStringOption(OPT_CACHE_ON_DISK, VALUE_YES)
        .addAppOwnedStringOption(OPT_DEMUXER_CACHE_DIR, cacheDirectory.getAbsolutePath())
        .addAppOwnedStringOption(OPT_CACHE_SECS, String.valueOf(cacheSeconds));
    return builder;
  }

  public static MpvPlayerConfig.Builder addSubtitleOptions(
      MpvPlayerConfig.Builder builder, Context context, MpvSubtitleOptions options) {
    return MpvSubtitleOptionDefaults.addSubtitleOptions(builder, context, options);
  }

  private static void addVideoOutputOptions(
      MpvPlayerConfig.Builder builder, MpvAndroidOptions options) {
    boolean gpuNextEnabled = options.isGpuNextEnabled();
    String driver = gpuNextEnabled ? VALUE_VIDEO_OUTPUT_GPU_NEXT : VALUE_VIDEO_OUTPUT_GPU;
    builder
        .setVideoSharpnessSupported(!gpuNextEnabled)
        .addPreInitStringOption(OPT_VIDEO_OUTPUT, driver)
        .addPreInitStringOption(OPT_FORCE_WINDOW, VALUE_NO)
        .addPreInitStringOption(OPT_KEEPASPECT, VALUE_NO);
    if (options.isGpuNextEnabledSet()) {
      builder.addAppOwnedStringOption(OPT_VIDEO_OUTPUT, driver);
    }
    File shaderCacheDirectory = options.getShaderCacheDirectory();
    if (shaderCacheDirectory != null) {
      builder.addPreInitStringOption(
          OPT_GPU_SHADER_CACHE_DIR, shaderCacheDirectory.getAbsolutePath());
    }
  }

  private static void addAudioOutputOptions(
      MpvPlayerConfig.Builder builder, MpvAndroidOptions options) {
    builder.addPreInitStringOption(OPT_AUDIO_OUTPUT, VALUE_AUDIO_OUTPUT);
    if (options.isAudioPassthroughEnabledSet()) {
      String codecs = options.isAudioPassthroughEnabled() ? VALUE_AUDIO_SPDIF : "";
      builder.setRequestedPassthroughCodecs(codecs);
    }
  }

  private static void addHardwareDecodeOptions(MpvPlayerConfig.Builder builder) {
    builder
        .setDefaultHardwareDecode(VALUE_HWDEC)
        .addPreInitStringOption(PROP_HWDEC, VALUE_HWDEC)
        .addPreInitStringOption(OPT_HWDEC_CODECS, VALUE_HWDEC_CODECS);
  }

  private static void addDolbyVisionOptions(
      MpvPlayerConfig.Builder builder, MpvAndroidOptions options) {
    if (options.isDolbyVisionOutputPolicySet()) {
      builder.setDolbyVisionOutputPolicy(options.getDolbyVisionOutputPolicy());
    }
  }

  private static void addAndroidNetworkOptions(MpvPlayerConfig.Builder builder) {
    builder.addPreInitStringOption(OPT_YTDL, VALUE_NO);
    MpvNetworkOptions.addProxyUrlOption(builder);
  }

  private static void addProfileOption(MpvPlayerConfig.Builder builder) {
    builder.addPreInitStringOption(OPT_PROFILE, VALUE_FAST);
  }

  private static void addVulkanOptions(MpvPlayerConfig.Builder builder, MpvAndroidOptions options) {
    boolean vulkanEnabled = options.isVulkanEnabled();
    if (vulkanEnabled) {
      builder
          .addPreInitStringOption(OPT_GPU_API, VALUE_VULKAN)
          .addPreInitStringOption(OPT_GPU_CONTEXT, VALUE_ANDROID_VK);
    }
    if (options.isVulkanEnabledSet()) {
      builder
          .addAppOwnedStringOption(OPT_GPU_API, vulkanEnabled ? VALUE_VULKAN : VALUE_OPENGL)
          .addAppOwnedStringOption(
              OPT_GPU_CONTEXT, vulkanEnabled ? VALUE_ANDROID_VK : VALUE_ANDROID);
    }
  }
}
