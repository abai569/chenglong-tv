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

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.mpvplayer.core.MpvPlaybackState;
import java.util.List;

public final class MpvTimelineController {

  private final MpvPlaylist playlist;
  private final MpvPlaybackState playbackState;
  private final MpvPlaybackNavigator playbackNavigator;
  private final Host host;

  public MpvTimelineController(
      MpvPlaylist playlist,
      MpvPlaybackState playbackState,
      MpvPlaybackNavigator playbackNavigator,
      Host host) {
    this.playlist = playlist;
    this.playbackState = playbackState;
    this.playbackNavigator = playbackNavigator;
    this.host = host;
  }

  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    if (host.stopActiveFile()) {
      playlist.set(mediaItems, startIndex);
      host.resetCurrentMediaState(startPositionMs);
      host.clearPlayerError();
      playbackState.setIdle();
    }
  }

  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    if (!playlist.add(index, mediaItems)) {
      return;
    }
    playbackState.setIdle();
    host.resetCurrentMediaState(0);
  }

  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    playlist.move(fromIndex, toIndex, newIndex);
  }

  public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    MpvPlaylist.Change change = playlist.replace(fromIndex, toIndex, mediaItems);
    if (change.becameNonEmpty()) {
      playbackState.setIdle();
      host.resetCurrentMediaState(0);
    } else {
      handleCurrentItemChange(change.oldCurrent(), change.changedCurrent());
    }
  }

  public void removeMediaItems(int fromIndex, int toIndex) {
    MpvPlaylist.Change change = playlist.remove(fromIndex, toIndex);
    handleCurrentItemChange(change.oldCurrent(), change.changedCurrent());
  }

  public boolean maybeLoadNextAfterEnd(@Player.RepeatMode int repeatMode) {
    int nextIndex = playbackNavigator.getNextIndexAfterEnd(repeatMode);
    if (nextIndex == C.INDEX_UNSET) {
      return false;
    }
    playlist.setCurrentIndex(nextIndex);
    host.resetCurrentMediaState(0);
    host.setPendingDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 0);
    host.loadCurrent(checkNotNull(playlist.current()), 0);
    return true;
  }

  private void handleCurrentItemChange(@Nullable MediaItem oldCurrent, boolean changedCurrent) {
    if (playlist.isEmpty()) {
      if (host.stopActiveFile()) {
        host.releaseAudioFocus();
        playbackState.setIdle();
        host.resetCurrentMediaState(0);
      }
      return;
    }
    playlist.ensureCurrentIndex();
    if (!playlist.hasCurrentChanged(oldCurrent, changedCurrent)) {
      return;
    }
    host.resetCurrentMediaState(0);
    if (host.hasActiveMpvFile()) {
      host.loadCurrent(checkNotNull(playlist.current()), 0);
    } else {
      playbackState.setIdle();
    }
  }

  public interface Host {

    boolean stopActiveFile();

    void releaseAudioFocus();

    boolean hasActiveMpvFile();

    void loadCurrent(MediaItem item, long startPositionMs);

    void resetCurrentMediaState(long startPositionMs);

    void clearPlayerError();

    void setPendingDiscontinuity(@Player.DiscontinuityReason int reason, long positionMs);
  }
}
