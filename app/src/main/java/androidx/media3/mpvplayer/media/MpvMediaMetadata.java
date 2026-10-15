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
import androidx.media3.common.MediaMetadata;

public final class MpvMediaMetadata {

  private MediaMetadata sourceArtworkMetadata;

  public MpvMediaMetadata() {
    sourceArtworkMetadata = MediaMetadata.EMPTY;
  }

  public void clear() {
    sourceArtworkMetadata = MediaMetadata.EMPTY;
  }

  public boolean hasSourceArtworkData() {
    return sourceArtworkMetadata.artworkData != null;
  }

  public void setSourceArtworkData(byte[] artworkData) {
    MediaMetadata metadata =
        new MediaMetadata.Builder()
            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build();
    if (!metadata.equals(sourceArtworkMetadata)) {
      sourceArtworkMetadata = metadata;
    }
  }

  public MediaMetadata build(@Nullable MediaItem item) {
    if (item == null) {
      return sourceArtworkMetadata;
    }
    MediaMetadata itemMetadata = item.mediaMetadata;
    if (itemMetadata.artworkData != null) {
      return itemMetadata;
    }
    if (!hasSourceArtworkData()) {
      return itemMetadata;
    }
    return itemMetadata
        .buildUpon()
        .setArtworkData(sourceArtworkMetadata.artworkData, sourceArtworkMetadata.artworkDataType)
        .build();
  }
}
