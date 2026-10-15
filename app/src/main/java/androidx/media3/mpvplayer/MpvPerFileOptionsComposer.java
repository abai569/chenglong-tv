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

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.mpvplayer.audio.MpvAudioFocusManager;
import androidx.media3.mpvplayer.core.MpvEffectController;
import androidx.media3.mpvplayer.core.MpvPlayerInfo;
import androidx.media3.mpvplayer.media.MpvMediaLoader;
import androidx.media3.mpvplayer.media.MpvSubtitleController;
import androidx.media3.mpvplayer.nativebridge.MpvPlaybackProperties;
import androidx.media3.mpvplayer.options.MpvOptions;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;
import androidx.media3.mpvplayer.video.MpvSurfaceController;

final class MpvPerFileOptionsComposer implements MpvMediaLoader.PerFileOptionsProvider {

  private final MpvOptions options;
  private final MpvPlaybackProperties playbackProperties;
  private final MpvPlayerInfo playerInfo;
  private final MpvAudioFocusManager audioFocusManager;
  private final MpvSubtitleController subtitleController;
  private final MpvEffectController effectController;
  private final MpvSurfaceController surfaceController;

  MpvPerFileOptionsComposer(
      MpvOptions options,
      MpvPlaybackProperties playbackProperties,
      MpvPlayerInfo playerInfo,
      MpvAudioFocusManager audioFocusManager,
      MpvSubtitleController subtitleController,
      MpvEffectController effectController,
      MpvSurfaceController surfaceController) {
    this.options = options;
    this.playbackProperties = playbackProperties;
    this.playerInfo = playerInfo;
    this.audioFocusManager = audioFocusManager;
    this.subtitleController = subtitleController;
    this.effectController = effectController;
    this.surfaceController = surfaceController;
  }

  @Override
  public void add(Uri uri, @Nullable String mimeType, MpvPerFileOptions perFileOptions) {
    options.addPerFileOptions(uri, mimeType, perFileOptions);
    playbackProperties.addPerFileOptions(
        perFileOptions,
        playerInfo.shouldPlay(),
        playerInfo.getPlaybackParameters(),
        playerInfo.getAudioOffsetMs(),
        playerInfo.getTextOffsetMs(),
        playerInfo.getVolume(),
        audioFocusManager.getVolumeMultiplier());
    subtitleController.addPerFileOptions(perFileOptions);
    effectController.addPerFileOptions(perFileOptions);
    surfaceController.addPerFileOptions(perFileOptions);
  }
}
