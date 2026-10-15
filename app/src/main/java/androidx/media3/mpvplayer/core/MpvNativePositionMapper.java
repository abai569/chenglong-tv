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
package androidx.media3.mpvplayer.core;

import static androidx.media3.mpvplayer.util.MpvUtil.normalizePositionMs;

import androidx.media3.common.C;

final class MpvNativePositionMapper {

  private static final long NATIVE_POSITION_RESET_TOLERANCE_MS = 1500;
  private static final long NATIVE_POSITION_FORWARD_JUMP_TOLERANCE_MS = 30000;

  private long positionOffsetMs;
  private long lastNativePositionMs;
  private boolean anchorNextNativePosition;

  MpvNativePositionMapper() {
    reset();
  }

  void reset() {
    positionOffsetMs = 0;
    lastNativePositionMs = C.TIME_UNSET;
    anchorNextNativePosition = true;
  }

  void anchorNextNativePosition() {
    anchorNextNativePosition = true;
  }

  long map(long currentPositionMs, long nativePositionMs) {
    if (anchorNextNativePosition) {
      return anchor(currentPositionMs, nativePositionMs);
    }
    long mappedPositionMs = nativePositionMs + positionOffsetMs;
    if (isNativePositionReset(nativePositionMs, mappedPositionMs, currentPositionMs)
        || isNativePositionForwardJump(nativePositionMs, mappedPositionMs, currentPositionMs)) {
      return anchor(currentPositionMs, nativePositionMs);
    }
    lastNativePositionMs = nativePositionMs;
    return normalizePositionMs(Math.max(currentPositionMs, mappedPositionMs));
  }

  long toMediaPositionMs(long nativePositionMs) {
    return normalizePositionMs(nativePositionMs + positionOffsetMs);
  }

  long toNativePositionMs(long mediaPositionMs) {
    return normalizePositionMs(mediaPositionMs - positionOffsetMs);
  }

  private long anchor(long currentPositionMs, long nativePositionMs) {
    positionOffsetMs = currentPositionMs - nativePositionMs;
    lastNativePositionMs = nativePositionMs;
    anchorNextNativePosition = false;
    return currentPositionMs;
  }

  private boolean isNativePositionReset(
      long nativePositionMs, long mappedPositionMs, long currentPositionMs) {
    return lastNativePositionMs != C.TIME_UNSET
        && nativePositionMs + NATIVE_POSITION_RESET_TOLERANCE_MS < lastNativePositionMs
        && mappedPositionMs + NATIVE_POSITION_RESET_TOLERANCE_MS < currentPositionMs;
  }

  private boolean isNativePositionForwardJump(
      long nativePositionMs, long mappedPositionMs, long currentPositionMs) {
    return lastNativePositionMs != C.TIME_UNSET
        && nativePositionMs > lastNativePositionMs + NATIVE_POSITION_FORWARD_JUMP_TOLERANCE_MS
        && mappedPositionMs > currentPositionMs + NATIVE_POSITION_FORWARD_JUMP_TOLERANCE_MS;
  }
}
