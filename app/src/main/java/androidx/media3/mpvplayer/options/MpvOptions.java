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
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.OPT_USER_AGENT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.OPT_VIDEO_OUTPUT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_ANDROID_DOLBY_VISION_OUTPUT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_HWDEC;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_NO;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Display;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.DolbyVisionOutputPolicy;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.mpvplayer.audio.MpvAudioCapabilities;
import com.google.common.annotations.VisibleForTesting;

@RestrictTo(LIBRARY_GROUP)
public final class MpvOptions {

  private static final String OPT_AUDIO_SPDIF = "audio-spdif";
  private static final String DOLBY_VISION_OUTPUT_CONFIGURED = "configured";
  private static final String DOLBY_VISION_OUTPUT_DIRECT = "direct";
  private static final String HARDWARE_DECODER_MEDIACODEC = "mediacodec";
  private static final String VIDEO_OUTPUT_NULL = "null";

  private final MpvPlayerConfig config;
  @Nullable private final String supportedPassthroughCodecs;
  private final boolean autoDolbyVisionDisplayDetection;
  @Nullable private Boolean nativeDolbyVisionOutputAllowed;
  private String hardwareDecode;
  @Nullable private String deferredVideoOutput;
  private boolean hardwareDecodeEnabled;
  private boolean directVideoOutputConfigured;
  private boolean directOsdOutputConfigured;

  public MpvOptions(Context context, MpvPlayerConfig config) {
    this(
        config,
        MpvAudioCapabilities.getSupportedPassthroughCodecs(
            context, config.getRequestedPassthroughCodecs()),
        resolveNativeDolbyVisionOutputAllowed(context, config),
        config.isDolbyVisionOutputPolicySet()
            && config.getDolbyVisionOutputPolicy() == DolbyVisionOutputPolicy.AUTO);
  }

  @VisibleForTesting
  MpvOptions(MpvPlayerConfig config, @Nullable String supportedPassthroughCodecs) {
    this(config, supportedPassthroughCodecs, /* nativeDolbyVisionOutputAllowed= */ null);
  }

  @VisibleForTesting
  MpvOptions(
      MpvPlayerConfig config,
      @Nullable String supportedPassthroughCodecs,
      @Nullable Boolean nativeDolbyVisionOutputAllowed) {
    this(
        config,
        supportedPassthroughCodecs,
        nativeDolbyVisionOutputAllowed,
        /* autoDolbyVisionDisplayDetection= */ false);
  }

  @VisibleForTesting
  MpvOptions(
      MpvPlayerConfig config,
      @Nullable String supportedPassthroughCodecs,
      @Nullable Boolean nativeDolbyVisionOutputAllowed,
      boolean autoDolbyVisionDisplayDetection) {
    this.config = config;
    this.supportedPassthroughCodecs = supportedPassthroughCodecs;
    this.nativeDolbyVisionOutputAllowed = nativeDolbyVisionOutputAllowed;
    this.autoDolbyVisionDisplayDetection = autoDolbyVisionDisplayDetection;
    this.hardwareDecode = config.getDefaultHardwareDecode();
  }

  private static @Nullable Boolean resolveNativeDolbyVisionOutputAllowed(
      Context context, MpvPlayerConfig config) {
    return !config.isDolbyVisionOutputPolicySet()
        ? null
        : DolbyVisionOutputPolicy.isNativeOutputAllowed(
            context, config.getDolbyVisionOutputPolicy());
  }

  private static boolean isHardwareDecodeValue(@Nullable String value) {
    return !TextUtils.isEmpty(value) && !VALUE_NO.equals(value);
  }

  private @Nullable String getHardwareDecodeValue() {
    if (!hardwareDecodeEnabled) {
      return VALUE_NO;
    }
    return TextUtils.isEmpty(hardwareDecode) ? null : hardwareDecode;
  }

  private boolean shouldUseDirectDolbyVisionOutput() {
    @Nullable String hardwareDecode = getHardwareDecodeValue();
    if (!Boolean.TRUE.equals(nativeDolbyVisionOutputAllowed)
        || hardwareDecode == null
        || !directVideoOutputConfigured
        || !directOsdOutputConfigured) {
      return false;
    }
    for (String decoder : hardwareDecode.split(",")) {
      if (HARDWARE_DECODER_MEDIACODEC.equals(decoder)) {
        return true;
      }
    }
    return false;
  }

  private @Nullable String getDolbyVisionOutputMode() {
    if (nativeDolbyVisionOutputAllowed == null) {
      return null;
    }
    return shouldUseDirectDolbyVisionOutput()
        ? DOLBY_VISION_OUTPUT_DIRECT
        : DOLBY_VISION_OUTPUT_CONFIGURED;
  }

  public void applyPreInit(StringOptionWriter strings) {
    config.applyPreInit(strings);
    applyAudioPassthrough(strings);
    String defaultUserAgent = config.getDefaultUserAgent();
    if (!TextUtils.isEmpty(defaultUserAgent)) {
      strings.set(OPT_USER_AGENT, defaultUserAgent);
    }
  }

  public void applyAppOwned(StringOptionWriter strings) {
    config.applyAppOwned(strings);
    applyAudioPassthrough(strings);
  }

  /** Defers Android VO creation from player initialization to the next loadfile. */
  public void deferVideoOutputUntilLoad(
      @Nullable String configuredVideoOutput, StringOptionWriter strings) {
    if (configuredVideoOutput == null
        || configuredVideoOutput.isEmpty()
        || VIDEO_OUTPUT_NULL.equals(configuredVideoOutput)) {
      deferredVideoOutput = null;
      return;
    }
    deferredVideoOutput = configuredVideoOutput;
    strings.set(OPT_VIDEO_OUTPUT, VIDEO_OUTPUT_NULL);
  }

  public boolean isAudioPassthroughEnabled() {
    return !TextUtils.isEmpty(supportedPassthroughCodecs);
  }

  public boolean isVideoSharpnessSupported() {
    return config.isVideoSharpnessSupported();
  }

  public @Nullable String onInitialized(
      boolean hardwareDecodeEnabled, @Nullable String hardwareDecode) {
    if (hardwareDecode != null && isHardwareDecodeValue(hardwareDecode)) {
      this.hardwareDecode = hardwareDecode;
    }
    this.hardwareDecodeEnabled = hardwareDecodeEnabled;
    return getHardwareDecodeValue();
  }

  public @Nullable String setHardwareDecodeEnabled(
      boolean hardwareDecodeEnabled, @Nullable String currentHardwareDecode) {
    if (!hardwareDecodeEnabled
        && this.hardwareDecodeEnabled
        && currentHardwareDecode != null
        && isHardwareDecodeValue(currentHardwareDecode)) {
      hardwareDecode = currentHardwareDecode;
    }
    this.hardwareDecodeEnabled = hardwareDecodeEnabled;
    return getHardwareDecodeValue();
  }

  public boolean setDirectVideoOutputConfigured(boolean directVideoOutputConfigured) {
    if (this.directVideoOutputConfigured == directVideoOutputConfigured) {
      return false;
    }
    @Nullable String previousOutputMode = getDolbyVisionOutputMode();
    this.directVideoOutputConfigured = directVideoOutputConfigured;
    return !TextUtils.equals(previousOutputMode, getDolbyVisionOutputMode());
  }

  public boolean setDirectVideoDisplay(@Nullable Display display) {
    if (!autoDolbyVisionDisplayDetection || display == null) {
      return false;
    }
    boolean nativeDolbyVisionOutputAllowed =
        DolbyVisionOutputPolicy.isNativeOutputAllowedOnDisplay(
            display, DolbyVisionOutputPolicy.AUTO);
    if (this.nativeDolbyVisionOutputAllowed != null
        && this.nativeDolbyVisionOutputAllowed == nativeDolbyVisionOutputAllowed) {
      return false;
    }
    @Nullable String previousOutputMode = getDolbyVisionOutputMode();
    this.nativeDolbyVisionOutputAllowed = nativeDolbyVisionOutputAllowed;
    return !TextUtils.equals(previousOutputMode, getDolbyVisionOutputMode());
  }

  public boolean setDirectOsdOutputConfigured(boolean directOsdOutputConfigured) {
    if (this.directOsdOutputConfigured == directOsdOutputConfigured) {
      return false;
    }
    @Nullable String previousOutputMode = getDolbyVisionOutputMode();
    this.directOsdOutputConfigured = directOsdOutputConfigured;
    return !TextUtils.equals(previousOutputMode, getDolbyVisionOutputMode());
  }

  public void onNativeSessionEnded() {
    hardwareDecode = config.getDefaultHardwareDecode();
    deferredVideoOutput = null;
    hardwareDecodeEnabled = false;
  }

  public void addPerFileOptions(Uri uri, @Nullable String mimeType, MpvPerFileOptions options) {
    applyAppOwned(options::add);
    if (deferredVideoOutput != null) {
      options.set(OPT_VIDEO_OUTPUT, deferredVideoOutput);
    }
    config.applyNetworkOptions(uri, mimeType, options::add);
    @Nullable String hardwareDecode = getHardwareDecodeValue();
    if (hardwareDecode != null) {
      options.add(PROP_HWDEC, hardwareDecode);
    }
    @Nullable String dolbyVisionOutputMode = getDolbyVisionOutputMode();
    if (dolbyVisionOutputMode != null) {
      options.add(PROP_ANDROID_DOLBY_VISION_OUTPUT, dolbyVisionOutputMode);
    }
  }

  public void applyRuntimeDolbyVisionOutputMode(StringOptionWriter writer) {
    @Nullable String dolbyVisionOutputMode = getDolbyVisionOutputMode();
    if (dolbyVisionOutputMode != null) {
      writer.set(PROP_ANDROID_DOLBY_VISION_OUTPUT, dolbyVisionOutputMode);
    }
  }

  private void applyAudioPassthrough(StringOptionWriter strings) {
    if (supportedPassthroughCodecs != null) {
      strings.set(OPT_AUDIO_SPDIF, supportedPassthroughCodecs);
    }
  }

  public interface StringOptionWriter {

    void set(String name, String value);
  }

  public interface DoubleOptionWriter {

    void set(String name, double value);
  }
}
