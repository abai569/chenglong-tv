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
package androidx.media3.mpvplayer.media;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.mpvplayer.audio.MpvAudioFocusManager;
import androidx.media3.mpvplayer.core.MpvPlaybackState;
import androidx.media3.mpvplayer.nativebridge.MpvClient;
import androidx.media3.mpvplayer.nativebridge.MpvCommandDispatcher;
import androidx.media3.mpvplayer.nativebridge.MpvLoadGeneration;
import androidx.media3.mpvplayer.nativebridge.MpvPlaybackErrorFactory;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;

public final class MpvMediaLoader {

  private final MpvClient client;
  private final MpvCommandDispatcher commandDispatcher;
  private final MpvPlaybackState playbackState;
  private final MpvSubtitleController subtitleController;
  private final MpvAudioFocusManager audioFocusManager;
  private final MpvPlaybackErrorFactory errorFactory;
  private final MpvLoadGeneration loadGeneration;
  private final MpvLoadOptionsFactory loadOptionsFactory;
  private final Host host;

  @RestrictTo(LIBRARY_GROUP)
  public MpvMediaLoader(
      MpvClient client,
      MpvCommandDispatcher commandDispatcher,
      MpvPlaybackState playbackState,
      MpvSubtitleController subtitleController,
      MpvAudioFocusManager audioFocusManager,
      MpvPlaybackErrorFactory errorFactory,
      MpvLoadGeneration loadGeneration,
      PerFileOptionsProvider perFileOptionsProvider,
      Host host) {
    this.client = client;
    this.commandDispatcher = commandDispatcher;
    this.playbackState = playbackState;
    this.subtitleController = subtitleController;
    this.audioFocusManager = audioFocusManager;
    this.errorFactory = errorFactory;
    this.loadGeneration = loadGeneration;
    this.loadOptionsFactory = new MpvLoadOptionsFactory(perFileOptionsProvider);
    this.host = host;
  }

  public void load(MediaItem item, long startPositionMs) {
    Uri uri = MpvMediaItems.getUri(item);
    if (uri == null) {
      host.fail(
          errorFactory.create("No media uri", null, PlaybackException.ERROR_CODE_BAD_VALUE));
      return;
    }
    if (host.hasActiveMpvFile()) {
      host.expectProgrammaticEndFile();
    }
    loadGeneration.onLoadRequested();
    client.clearLastResult();
    host.clearPlayerError();
    host.resetSeekState();
    subtitleController.resetFor(item);
    host.resetCurrentMediaState();
    if (host.isPlayWhenReady()) {
      audioFocusManager.requestForPlayback();
    }
    MpvLoadOptions loadOptions = loadOptionsFactory.create(item, uri, startPositionMs);
    playbackState.startLoading(
        startPositionMs, loadOptions.sourceStartPositionMs(), loadOptions.sourceEndPositionMs());
    if (!commandDispatcher.loadFile(uri, loadOptions.perFileOptions())) {
      host.fail(errorFactory.createLoadFailure(uri));
      return;
    }
    if (startPositionMs > 0) {
      playbackState.clearPendingSeek();
    }
    host.invalidatePlayerState();
  }

  public interface Host {

    boolean hasActiveMpvFile();

    void expectProgrammaticEndFile();

    boolean isPlayWhenReady();

    void clearPlayerError();

    void resetSeekState();

    void resetCurrentMediaState();

    void invalidatePlayerState();

    void fail(PlaybackException error);
  }

  @RestrictTo(LIBRARY_GROUP)
  public interface PerFileOptionsProvider {

    void add(Uri uri, @Nullable String mimeType, MpvPerFileOptions options);
  }
}
