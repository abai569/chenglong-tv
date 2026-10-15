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
import androidx.media3.common.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;

final class MpvMediaItems {

  static @Nullable Uri getUri(MediaItem item) {
    if (item.localConfiguration != null) {
      return item.localConfiguration.uri;
    }
    return item.requestMetadata.mediaUri;
  }

  static @Nullable String getMimeType(MediaItem item) {
    if (item.localConfiguration == null) {
      return null;
    }
    return item.localConfiguration.mimeType;
  }

  static boolean isIsoMimeType(MediaItem item) {
    return MimeTypes.VIDEO_ISO.equals(getMimeType(item));
  }

  static List<MediaItem.SubtitleConfiguration> getSubtitleConfigurations(MediaItem item) {
    if (item.localConfiguration == null) {
      return ImmutableList.of();
    }
    return item.localConfiguration.subtitleConfigurations;
  }

  static long getImageDurationMs(MediaItem item) {
    if (item.localConfiguration == null) {
      return C.TIME_UNSET;
    }
    return item.localConfiguration.imageDurationMs;
  }

  static boolean samePlaybackRequest(@Nullable MediaItem oldItem, @Nullable MediaItem newItem) {
    if (oldItem == null || newItem == null) {
      return oldItem == newItem;
    }
    return Objects.equals(getUri(oldItem), getUri(newItem))
        && Objects.equals(getMimeType(oldItem), getMimeType(newItem))
        && Objects.equals(getSubtitleConfigurations(oldItem), getSubtitleConfigurations(newItem))
        && sameClippingConfiguration(oldItem.clippingConfiguration, newItem.clippingConfiguration)
        && getImageDurationMs(oldItem) == getImageDurationMs(newItem)
        && MpvRequestHeaders.same(oldItem, newItem);
  }

  private static boolean sameClippingConfiguration(
      MediaItem.ClippingConfiguration first, MediaItem.ClippingConfiguration second) {
    return first.startPositionMs == second.startPositionMs
        && first.endPositionMs == second.endPositionMs
        && first.relativeToLiveWindow == second.relativeToLiveWindow
        && first.relativeToDefaultPosition == second.relativeToDefaultPosition;
  }
}
