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

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.mpvplayer.core.MpvPlaybackState;

public final class MpvPlaybackNavigator {

  private final MpvPlaylist playlist;

  public MpvPlaybackNavigator(MpvPlaylist playlist) {
    this.playlist = playlist;
  }

  public int getNextIndexAfterEnd(@Player.RepeatMode int repeatMode) {
    int currentIndex = playlist.currentIndex();
    if (playlist.isEmpty() || currentIndex == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    if (repeatMode == Player.REPEAT_MODE_ONE) {
      return currentIndex;
    }
    if (currentIndex < playlist.size() - 1) {
      return currentIndex + 1;
    }
    return repeatMode == Player.REPEAT_MODE_ALL ? 0 : C.INDEX_UNSET;
  }

  public boolean isRepeatOneLoopSeek(
      @Player.RepeatMode int repeatMode, MpvPlaybackState playbackState) {
    return repeatMode == Player.REPEAT_MODE_ONE
        && playbackState.isReady()
        && playbackState.isNearEnd();
  }
}
