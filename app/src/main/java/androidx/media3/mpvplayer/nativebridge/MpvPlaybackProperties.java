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
package androidx.media3.mpvplayer.nativebridge;

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_AUDIO_CHANNEL_COUNT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_AUDIO_DELAY;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_AUDIO_OUTPUT_FORMAT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_BRIGHTNESS;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CONTRAST;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CURRENT_VIDEO_OUTPUT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_GAMMA;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_HUE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_HWDEC;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_IDLE_ACTIVE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_LOOP_FILE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_PAUSE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_PITCH;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_SATURATION;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_SHARPEN;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_SPEED;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_SUB_DELAY;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_VOLUME;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_NO;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_YES;

import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.mpvplayer.options.MpvOptions.DoubleOptionWriter;
import androidx.media3.mpvplayer.options.MpvOptions.StringOptionWriter;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;
import androidx.media3.mpvplayer.video.MpvVideoEqualizer;

public final class MpvPlaybackProperties {

  private final MpvPropertyAccessor properties;

  public MpvPlaybackProperties(MpvPropertyAccessor properties) {
    this.properties = properties;
  }

  public void addPerFileOptions(
      MpvPerFileOptions options,
      boolean playWhenReady,
      PlaybackParameters playbackParameters,
      long audioOffsetMs,
      long textOffsetMs,
      float volume,
      float volumeMultiplier) {
    writePlaybackOptions(
        options::add,
        options::add,
        playWhenReady,
        playbackParameters,
        audioOffsetMs,
        textOffsetMs,
        volume,
        volumeMultiplier);
  }

  private static void writePlaybackOptions(
      StringOptionWriter strings,
      DoubleOptionWriter doubles,
      boolean playWhenReady,
      PlaybackParameters playbackParameters,
      long audioOffsetMs,
      long textOffsetMs,
      float volume,
      float volumeMultiplier) {
    strings.set(PROP_PAUSE, playWhenReady ? VALUE_NO : VALUE_YES);
    strings.set(PROP_LOOP_FILE, VALUE_NO);
    doubles.set(PROP_SPEED, playbackParameters.speed);
    doubles.set(PROP_PITCH, playbackParameters.pitch);
    doubles.set(PROP_AUDIO_DELAY, audioOffsetMs / 1000.0);
    doubles.set(PROP_SUB_DELAY, textOffsetMs / 1000.0);
    doubles.set(PROP_VOLUME, volume * 100.0 * volumeMultiplier);
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    properties.setStringProperty(PROP_PAUSE, playWhenReady ? VALUE_NO : VALUE_YES);
  }

  public void disableNativeLoop() {
    properties.setStringProperty(PROP_LOOP_FILE, VALUE_NO);
  }

  public void updatePlaybackParameters(
      PlaybackParameters previousPlaybackParameters, PlaybackParameters playbackParameters) {
    if (Float.compare(previousPlaybackParameters.speed, playbackParameters.speed) != 0) {
      properties.setDoubleProperty(PROP_SPEED, playbackParameters.speed);
    }
    if (Float.compare(previousPlaybackParameters.pitch, playbackParameters.pitch) != 0) {
      properties.setDoubleProperty(PROP_PITCH, playbackParameters.pitch);
    }
  }

  public void setAudioDelayMs(long audioOffsetMs) {
    properties.setDoubleProperty(PROP_AUDIO_DELAY, audioOffsetMs / 1000.0);
  }

  public @Nullable Integer getAudioChannelCount() {
    return properties.getInt(PROP_AUDIO_CHANNEL_COUNT);
  }

  public @Nullable String getAudioOutputFormat() {
    return properties.getString(PROP_AUDIO_OUTPUT_FORMAT);
  }

  public @Nullable String getCurrentVideoOutput() {
    return properties.getString(PROP_CURRENT_VIDEO_OUTPUT);
  }

  public boolean hasActiveFile(boolean fallbackHasActiveFile) {
    @Nullable Boolean idleActive = properties.getBoolean(PROP_IDLE_ACTIVE);
    return idleActive == null ? fallbackHasActiveFile : !idleActive;
  }

  public void setTextDelayMs(long textOffsetMs) {
    properties.setDoubleProperty(PROP_SUB_DELAY, textOffsetMs / 1000.0);
  }

  public void setVolume(float volume, float volumeMultiplier) {
    properties.setDoubleProperty(PROP_VOLUME, volume * 100.0 * volumeMultiplier);
  }

  public void setVideoEqualizer(MpvVideoEqualizer videoEqualizer) {
    writeVideoEqualizer(properties::setDoubleProperty, videoEqualizer);
  }

  public void addVideoEqualizerPerFileOptions(
      MpvPerFileOptions options, MpvVideoEqualizer videoEqualizer) {
    writeVideoEqualizer(options::add, videoEqualizer);
  }

  private static void writeVideoEqualizer(
      DoubleOptionWriter doubles, MpvVideoEqualizer videoEqualizer) {
    doubles.set(PROP_BRIGHTNESS, videoEqualizer.getBrightness());
    doubles.set(PROP_CONTRAST, videoEqualizer.getContrast());
    doubles.set(PROP_SATURATION, videoEqualizer.getSaturation());
    doubles.set(PROP_GAMMA, videoEqualizer.getGamma());
    doubles.set(PROP_HUE, videoEqualizer.getHue());
    doubles.set(PROP_SHARPEN, videoEqualizer.getSharpness());
  }

  public @Nullable String getHardwareDecode() {
    return properties.getString(PROP_HWDEC);
  }

  public void setHardwareDecode(@Nullable String hardwareDecode) {
    if (hardwareDecode != null && !hardwareDecode.isEmpty()) {
      properties.setStringOptionOrProperty(PROP_HWDEC, hardwareDecode);
    }
  }

  public void setStringOptionOrProperty(String name, String value) {
    properties.setStringOptionOrProperty(name, value);
  }
}
