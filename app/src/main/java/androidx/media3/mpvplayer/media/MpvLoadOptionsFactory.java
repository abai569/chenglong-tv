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

import static androidx.media3.mpvplayer.util.MpvUtil.trimToNull;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;

final class MpvLoadOptionsFactory {

  private static final String OPTION_END = "end";
  private static final String OPTION_FORCE_ISO = "force-iso";
  private static final String OPTION_FORCE_MEDIA_TITLE = "force-media-title";
  private static final String OPTION_IMAGE_DISPLAY_DURATION = "image-display-duration";
  private static final String OPTION_START = "start";
  private static final String VALUE_YES = "yes";

  private final MpvMediaLoader.PerFileOptionsProvider perFileOptionsProvider;

  MpvLoadOptionsFactory(MpvMediaLoader.PerFileOptionsProvider perFileOptionsProvider) {
    this.perFileOptionsProvider = perFileOptionsProvider;
  }

  MpvLoadOptions create(MediaItem item, Uri uri, long startPositionMs) {
    MpvPerFileOptions options = new MpvPerFileOptions();
    perFileOptionsProvider.add(uri, MpvMediaItems.getMimeType(item), options);
    MpvHeaderOptions.apply(item, options::set);
    @Nullable String mediaTitle = getMediaTitle(item);
    if (mediaTitle != null) {
      options.set(OPTION_FORCE_MEDIA_TITLE, mediaTitle);
    }
    MediaItem.ClippingConfiguration clipping = item.clippingConfiguration;
    boolean canApplyClipping =
        !clipping.relativeToLiveWindow && !clipping.relativeToDefaultPosition;
    long sourceStartPositionMs = canApplyClipping ? clipping.startPositionMs : 0;
    long sourceEndPositionMs = canApplyClipping ? clipping.endPositionMs : C.TIME_END_OF_SOURCE;
    long mpvStartPositionMs = sourceStartPositionMs + Math.max(0, startPositionMs);
    if (MpvMediaItems.isIsoMimeType(item)) {
      options.add(OPTION_FORCE_ISO, VALUE_YES);
    }
    if (mpvStartPositionMs > 0) {
      options.add(OPTION_START, mpvStartPositionMs / 1000.0);
    }
    if (sourceEndPositionMs != C.TIME_END_OF_SOURCE) {
      options.add(OPTION_END, sourceEndPositionMs / 1000.0);
    }
    long imageDurationMs = MpvMediaItems.getImageDurationMs(item);
    if (imageDurationMs != C.TIME_UNSET) {
      options.add(OPTION_IMAGE_DISPLAY_DURATION, imageDurationMs / 1000.0);
    }
    return new MpvLoadOptions(options.build(), sourceStartPositionMs, sourceEndPositionMs);
  }

  private static @Nullable String getMediaTitle(MediaItem item) {
    @Nullable CharSequence title = item.mediaMetadata.displayTitle;
    @Nullable String mediaTitle = trimToNull(title == null ? null : title.toString());
    if (mediaTitle != null) {
      return mediaTitle;
    }
    title = item.mediaMetadata.title;
    return trimToNull(title == null ? null : title.toString());
  }
}
