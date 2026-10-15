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

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer.MediaItemData;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Util;
import java.util.ArrayList;
import java.util.List;

public final class MpvPlaylist {

  private final List<Entry> items;
  private int currentIndex;

  public MpvPlaylist() {
    this.items = new ArrayList<>();
    this.currentIndex = C.INDEX_UNSET;
  }

  private static int getIndexAfterMove(int index, int fromIndex, int toIndex, int newIndex) {
    if (index == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    int count = toIndex - fromIndex;
    if (index >= fromIndex && index < toIndex) {
      return newIndex + index - fromIndex;
    }
    if (index >= newIndex && index < fromIndex) {
      return index + count;
    }
    if (newIndex > fromIndex && index >= toIndex && index < newIndex + count) {
      return index - count;
    }
    return index;
  }

  public void set(List<MediaItem> mediaItems, int startIndex) {
    items.clear();
    items.addAll(createEntries(mediaItems));
    currentIndex =
        items.isEmpty() ? C.INDEX_UNSET : Util.constrainValue(startIndex, 0, items.size() - 1);
  }

  public boolean add(int index, List<MediaItem> mediaItems) {
    boolean wasEmpty = items.isEmpty();
    int correctedIndex = Util.constrainValue(index, 0, items.size());
    items.addAll(correctedIndex, createEntries(mediaItems));
    if (wasEmpty && !items.isEmpty()) {
      currentIndex = 0;
      return true;
    }
    if (currentIndex >= correctedIndex) {
      currentIndex += mediaItems.size();
    }
    return false;
  }

  public void move(int fromIndex, int toIndex, int newIndex) {
    Util.moveItems(items, fromIndex, toIndex, newIndex);
    currentIndex = getIndexAfterMove(currentIndex, fromIndex, toIndex, newIndex);
  }

  public Change replace(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    MediaItem oldCurrent = current();
    boolean wasEmpty = items.isEmpty();
    boolean changedCurrent = isCurrentIndexInRange(fromIndex, toIndex);
    Util.removeRange(items, fromIndex, toIndex);
    items.addAll(fromIndex, createEntries(mediaItems));
    if (wasEmpty && !items.isEmpty()) {
      currentIndex = 0;
      return new Change(oldCurrent, false, true);
    }
    updateIndexAfterReplace(fromIndex, toIndex, mediaItems.size(), changedCurrent);
    return new Change(oldCurrent, changedCurrent, false);
  }

  public Change remove(int fromIndex, int toIndex) {
    MediaItem oldCurrent = current();
    boolean changedCurrent = isCurrentIndexInRange(fromIndex, toIndex);
    Util.removeRange(items, fromIndex, toIndex);
    updateIndexAfterReplace(fromIndex, toIndex, 0, changedCurrent);
    return new Change(oldCurrent, changedCurrent, false);
  }

  public List<MediaItemData> build(
      Tracks currentTracks,
      MediaMetadata currentMetadata,
      long durationMs,
      long defaultPositionMs,
      boolean currentSeekable,
      boolean currentLive) {
    List<MediaItemData> data = new ArrayList<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      Entry entry = items.get(i);
      MediaItem item = entry.mediaItem;
      boolean current = i == currentIndex;
      long itemDurationUs = current ? Util.msToUs(durationMs) : C.TIME_UNSET;
      MediaItemData.Builder builder =
          new MediaItemData.Builder(entry.uid)
              .setTracks(current ? currentTracks : Tracks.EMPTY)
              .setMediaItem(item)
              .setMediaMetadata(current ? currentMetadata : item.mediaMetadata)
              .setIsSeekable(current && currentSeekable)
              .setDurationUs(itemDurationUs)
              .setDefaultPositionUs(current ? Util.msToUs(defaultPositionMs) : 0);
      if (current && currentLive) {
        builder.setLiveConfiguration(item.liveConfiguration).setIsDynamic(true);
      }
      data.add(builder.build());
    }
    return data;
  }

  public @Nullable MediaItem current() {
    return isValidIndex(currentIndex) ? items.get(currentIndex).mediaItem : null;
  }

  public @Nullable Uri currentUri() {
    MediaItem item = current();
    return item == null ? null : MpvMediaItems.getUri(item);
  }

  public MediaItem get(int index) {
    return items.get(index).mediaItem;
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  public int size() {
    return items.size();
  }

  public int currentIndex() {
    return currentIndex;
  }

  public boolean hasPreviousMediaItem(@Player.RepeatMode int repeatMode) {
    if (!isValidIndex(currentIndex)) {
      return false;
    }
    return currentIndex > 0 || repeatMode == Player.REPEAT_MODE_ALL;
  }

  public boolean hasNextMediaItem(@Player.RepeatMode int repeatMode) {
    if (!isValidIndex(currentIndex)) {
      return false;
    }
    return currentIndex < items.size() - 1 || repeatMode == Player.REPEAT_MODE_ALL;
  }

  public void setCurrentIndex(int index) {
    currentIndex = index;
  }

  public void ensureCurrentIndex() {
    if (items.isEmpty()) {
      currentIndex = C.INDEX_UNSET;
    } else if (currentIndex == C.INDEX_UNSET) {
      currentIndex = 0;
    }
  }

  public boolean hasCurrentChanged(@Nullable MediaItem oldCurrent, boolean changedCurrent) {
    return changedCurrent && !MpvMediaItems.samePlaybackRequest(oldCurrent, current());
  }

  private void updateIndexAfterReplace(
      int fromIndex, int toIndex, int insertedCount, boolean replacedCurrent) {
    int removedCount = toIndex - fromIndex;
    if (items.isEmpty()) {
      currentIndex = C.INDEX_UNSET;
    } else if (currentIndex == C.INDEX_UNSET) {
      currentIndex = 0;
    } else if (replacedCurrent) {
      currentIndex = Util.constrainValue(fromIndex, 0, items.size() - 1);
    } else if (currentIndex >= toIndex) {
      currentIndex += insertedCount - removedCount;
    }
  }

  private boolean isCurrentIndexInRange(int fromIndex, int toIndex) {
    return currentIndex != C.INDEX_UNSET && currentIndex >= fromIndex && currentIndex < toIndex;
  }

  public boolean isValidIndex(int index) {
    return index >= 0 && index < items.size();
  }

  private static List<Entry> createEntries(List<MediaItem> mediaItems) {
    List<Entry> entries = new ArrayList<>(mediaItems.size());
    for (MediaItem mediaItem : mediaItems) {
      entries.add(new Entry(mediaItem));
    }
    return entries;
  }

  private static final class Entry {

    private final Object uid;
    private final MediaItem mediaItem;

    private Entry(MediaItem mediaItem) {
      this.uid = new Object();
      this.mediaItem = mediaItem;
    }
  }

  public static final class Change {

    @Nullable private final MediaItem oldCurrent;
    private final boolean changedCurrent;
    private final boolean becameNonEmpty;

    Change(@Nullable MediaItem oldCurrent, boolean changedCurrent, boolean becameNonEmpty) {
      this.oldCurrent = oldCurrent;
      this.changedCurrent = changedCurrent;
      this.becameNonEmpty = becameNonEmpty;
    }

    public @Nullable MediaItem oldCurrent() {
      return oldCurrent;
    }

    public boolean changedCurrent() {
      return changedCurrent;
    }

    public boolean becameNonEmpty() {
      return becameNonEmpty;
    }
  }
}
