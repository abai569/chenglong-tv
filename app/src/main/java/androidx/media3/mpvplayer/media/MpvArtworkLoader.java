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

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import androidx.media3.mpvplayer.nativebridge.MpvTrackProperties;

public final class MpvArtworkLoader {

  private final MpvPropertyAccessor properties;
  private final MpvMediaMetadata mediaMetadata;

  private boolean released;

  public MpvArtworkLoader(MpvPropertyAccessor properties, MpvMediaMetadata mediaMetadata) {
    this.properties = properties;
    this.mediaMetadata = mediaMetadata;
  }

  private static boolean hasMediaItemArtworkData(@Nullable MediaItem item) {
    return item != null && item.mediaMetadata.artworkData != null;
  }

  public void load(@Nullable MediaItem item, @Nullable byte[] artworkData) {
    if (released) {
      return;
    }
    if (hasMediaItemArtworkData(item)) {
      return;
    }
    if (mediaMetadata.hasSourceArtworkData()) {
      return;
    }
    if (artworkData != null && artworkData.length > 0) {
      mediaMetadata.setSourceArtworkData(artworkData);
    }
  }

  public void clear() {
    mediaMetadata.clear();
  }

  public void release() {
    released = true;
    clear();
  }

  public @Nullable byte[] readAttachedPicture() {
    Integer count = properties.getInt(MpvTrackProperties.listCount());
    if (count == null || count <= 0) {
      return null;
    }
    for (int i = 0; i < count; i++) {
      if (!Boolean.TRUE.equals(properties.getBoolean(MpvTrackProperties.albumArt(i)))) {
        continue;
      }
      @Nullable byte[] artworkData = properties.getByteArray(MpvTrackProperties.albumArtData(i));
      if (artworkData != null && artworkData.length > 0) {
        return artworkData;
      }
    }
    return null;
  }
}
