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
package androidx.media3.mpvplayer.seek;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.mpvplayer.core.MpvPlaybackState;

final class MpvEndedSeekHandler {

  private final MpvPlaybackState playbackState;
  private final MpvSeekController.Host host;
  private boolean skipNextDefaultPositionSeek;

  MpvEndedSeekHandler(MpvPlaybackState playbackState, MpvSeekController.Host host) {
    this.playbackState = playbackState;
    this.host = host;
  }

  void endLoadedFileFromSeek() {
    if (!host.hasActiveMpvFile() || host.stopActiveFile()) {
      completeEndedSeek();
    }
  }

  private void completeEndedSeek() {
    playbackState.onEndFile();
    playbackState.clearPendingSeek();
    if (host.maybeLoadNextAfterEnd()) {
      return;
    }
    skipNextDefaultPositionSeek();
    host.releaseAudioFocus();
  }

  void seekWithoutLoadedFile(
      long positionMs, @Player.Command int seekCommand, boolean isEndPosition) {
    if (isEndPosition) {
      playbackState.setPositionMs(playbackState.getDurationMs());
      playbackState.clearPendingSeek();
      playbackState.setEnded();
      skipNextDefaultPositionSeek();
      return;
    }
    if (maybeRestartEndedMedia(positionMs, seekCommand)) {
      return;
    }
    playbackState.setPositionMs(positionMs);
    playbackState.setPendingSeekMs(positionMs);
  }

  void clearTransitionFlags() {
    skipNextDefaultPositionSeek = false;
  }

  private boolean maybeRestartEndedMedia(long positionMs, @Player.Command int seekCommand) {
    @Nullable MediaItem current = host.currentMediaItem();
    if (!host.isInitialized()
        || playbackState.getState() != Player.STATE_ENDED
        || current == null) {
      return false;
    }
    if (consumeDefaultPositionSeek(seekCommand)) {
      return true;
    }
    clearTransitionFlags();
    host.loadCurrent(current, positionMs);
    return true;
  }

  private void skipNextDefaultPositionSeek() {
    skipNextDefaultPositionSeek = true;
  }

  private boolean consumeDefaultPositionSeek(@Player.Command int seekCommand) {
    if (!skipNextDefaultPositionSeek || seekCommand != Player.COMMAND_SEEK_TO_DEFAULT_POSITION) {
      return false;
    }
    // MediaSession maps play() in STATE_ENDED to seekToDefaultPosition() + play().
    // A seek-to-duration EOF should still let the app consume ENDED and advance.
    skipNextDefaultPositionSeek = false;
    return true;
  }
}
