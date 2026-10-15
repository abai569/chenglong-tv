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
package androidx.media3.mpvplayer.audio;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@RestrictTo(LIBRARY_GROUP)
public final class MpvAudioCapabilities {

  private static final String CODEC_AC3 = "ac3";
  private static final String CODEC_DTS = "dts";
  private static final String CODEC_DTS_HD = "dts-hd";
  private static final String CODEC_E_AC3 = "eac3";
  private static final String CODEC_TRUEHD = "truehd";

  public static @Nullable String getSupportedPassthroughCodecs(
      Context context, @Nullable String requestedCodecs) {
    if (TextUtils.isEmpty(requestedCodecs)) {
      return requestedCodecs;
    }
    AudioCapabilities capabilities =
        AudioCapabilities.getCapabilities(
            context, AudioAttributes.DEFAULT, null, Collections.emptyList());
    return getSupportedPassthroughCodecs(capabilities, requestedCodecs);
  }

  @VisibleForTesting
  static String getSupportedPassthroughCodecs(
      AudioCapabilities capabilities, String requestedCodecs) {
    Set<String> supportedCodecs = new LinkedHashSet<>();
    for (String requestedCodec : requestedCodecs.split(",")) {
      switch (requestedCodec.trim()) {
        case CODEC_AC3:
          if (capabilities.supportsEncoding(C.ENCODING_AC3)) {
            supportedCodecs.add(CODEC_AC3);
          }
          break;
        case CODEC_DTS:
          if (capabilities.supportsEncoding(C.ENCODING_DTS)) {
            supportedCodecs.add(CODEC_DTS);
          }
          break;
        case CODEC_DTS_HD:
          if (supportsDtsHd(capabilities)) {
            supportedCodecs.add(CODEC_DTS_HD);
          } else if (capabilities.supportsEncoding(C.ENCODING_DTS)) {
            supportedCodecs.add(CODEC_DTS);
          }
          break;
        case CODEC_E_AC3:
          if (capabilities.supportsEncoding(C.ENCODING_E_AC3)
              || capabilities.supportsEncoding(C.ENCODING_E_AC3_JOC)) {
            supportedCodecs.add(CODEC_E_AC3);
          }
          break;
        case CODEC_TRUEHD:
          if (capabilities.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)) {
            supportedCodecs.add(CODEC_TRUEHD);
          }
          break;
      }
    }
    return TextUtils.join(",", supportedCodecs);
  }

  private static boolean supportsDtsHd(AudioCapabilities capabilities) {
    return capabilities.supportsEncoding(C.ENCODING_DTS_HD)
        || capabilities.supportsEncoding(C.ENCODING_DTS_HD_MA)
        || capabilities.supportsEncoding(C.ENCODING_DTS_UHD_P2);
  }
}
