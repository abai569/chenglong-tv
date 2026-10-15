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

import static androidx.media3.mpvplayer.util.MpvUtil.normalizePositionMs;

import androidx.media3.common.C;
import androidx.media3.mpvplayer.core.MpvPlaybackState;

final class MpvSeekPositionResolver {

  private final MpvPlaybackState playbackState;

  MpvSeekPositionResolver(MpvPlaybackState playbackState) {
    this.playbackState = playbackState;
  }

  long resolvePositionMs(int mediaItemIndex, int currentIndex, long positionMs) {
    boolean currentMediaItem = mediaItemIndex == C.INDEX_UNSET || mediaItemIndex == currentIndex;
    long resolvedPositionMs =
        positionMs == C.TIME_UNSET && currentMediaItem
            ? playbackState.getDefaultPositionMs()
            : normalizePositionMs(positionMs);
    return currentMediaItem ? clampPositionMs(resolvedPositionMs) : resolvedPositionMs;
  }

  boolean isEndPosition(long positionMs) {
    long durationMs = playbackState.getTimelineDurationMs();
    return !playbackState.isCurrentMediaLive() && durationMs > 0 && positionMs >= durationMs;
  }

  private long clampPositionMs(long positionMs) {
    long durationMs = playbackState.getTimelineDurationMs();
    if (durationMs <= 0 || positionMs < durationMs) {
      return positionMs;
    }
    return durationMs;
  }
}
