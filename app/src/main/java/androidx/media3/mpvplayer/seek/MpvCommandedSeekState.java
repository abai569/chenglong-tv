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

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

final class MpvCommandedSeekState {

  private static final long POSITION_TOLERANCE_MS = 1500;

  @Nullable private SettableFuture<?> pendingFuture;
  @Nullable private SettableFuture<?> queuedFuture;
  private boolean pending;
  private boolean awaitingReady;
  private boolean maskingPosition;
  private long targetPositionMs;
  private long queuedPositionMs;

  MpvCommandedSeekState() {
    targetPositionMs = C.TIME_UNSET;
    queuedPositionMs = C.TIME_UNSET;
  }

  boolean isPending() {
    return pending;
  }

  boolean isAwaitingReady() {
    return awaitingReady;
  }

  boolean isMaskingPosition() {
    return maskingPosition;
  }

  ListenableFuture<?> start(long positionMs) {
    return start(positionMs, null);
  }

  void start(QueuedSeek seek) {
    start(seek.positionMs, seek.future);
  }

  ListenableFuture<?> queue(long positionMs) {
    completeQueuedFuture();
    awaitingReady = false;
    maskingPosition = true;
    targetPositionMs = positionMs;
    queuedPositionMs = positionMs;
    queuedFuture = SettableFuture.create();
    return queuedFuture;
  }

  @Nullable
  QueuedSeek consumeQueued() {
    if (queuedFuture == null || queuedPositionMs == C.TIME_UNSET) {
      return null;
    }
    QueuedSeek seek = new QueuedSeek(queuedPositionMs, queuedFuture);
    queuedPositionMs = C.TIME_UNSET;
    queuedFuture = null;
    return seek;
  }

  boolean isEndingPlayback() {
    return pending || queuedFuture != null || awaitingReady || maskingPosition;
  }

  void clearPending() {
    pending = false;
  }

  void clearAwaitingReady() {
    awaitingReady = false;
  }

  void startWaitingForReady() {
    awaitingReady = true;
  }

  void reset() {
    pending = false;
    awaitingReady = false;
    clearPositionMask();
    completePendingFuture();
    completeQueuedFuture();
  }

  boolean shouldIgnorePosition(long positionMs) {
    return maskingPosition && !isTargetPosition(positionMs);
  }

  void clearPositionMask() {
    maskingPosition = false;
    targetPositionMs = C.TIME_UNSET;
  }

  void maybeClearPositionMask(long positionMs) {
    if (maskingPosition && isTargetPosition(positionMs)) {
      clearPositionMask();
    }
  }

  void completePendingFuture() {
    if (pendingFuture == null) {
      return;
    }
    pendingFuture.set(null);
    pendingFuture = null;
  }

  private ListenableFuture<?> start(long positionMs, @Nullable SettableFuture<?> seekFuture) {
    awaitingReady = false;
    pending = true;
    maskingPosition = true;
    targetPositionMs = positionMs;
    pendingFuture = seekFuture == null ? SettableFuture.create() : seekFuture;
    return pendingFuture;
  }

  private void completeQueuedFuture() {
    if (queuedFuture == null) {
      return;
    }
    queuedFuture.set(null);
    queuedFuture = null;
    queuedPositionMs = C.TIME_UNSET;
  }

  private boolean isTargetPosition(long positionMs) {
    return positionMs >= 0 && Math.abs(positionMs - targetPositionMs) <= POSITION_TOLERANCE_MS;
  }

  static final class QueuedSeek {

    private final long positionMs;
    private final SettableFuture<?> future;

    private QueuedSeek(long positionMs, SettableFuture<?> future) {
      this.positionMs = positionMs;
      this.future = future;
    }

    long positionMs() {
      return positionMs;
    }
  }
}
