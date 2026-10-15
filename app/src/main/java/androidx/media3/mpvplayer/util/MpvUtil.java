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
package androidx.media3.mpvplayer.util;

import static com.google.common.base.Strings.emptyToNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;

public final class MpvUtil {

  public static long secondsToMsOrUnset(double seconds) {
    return Double.isFinite(seconds) ? (long) (seconds * 1000) : C.TIME_UNSET;
  }

  public static long normalizePositionMs(long positionMs) {
    return positionMs == C.TIME_UNSET ? 0 : Util.constrainValue(positionMs, 0, Long.MAX_VALUE);
  }

  public static String formatDouble(double value) {
    return Util.formatInvariant("%.3f", value);
  }

  public static @Nullable String trimToNull(@Nullable String value) {
    return value == null ? null : emptyToNull(value.trim());
  }
}
