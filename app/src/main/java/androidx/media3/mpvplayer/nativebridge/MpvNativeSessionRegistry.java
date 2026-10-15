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
package androidx.media3.mpvplayer.nativebridge;

import androidx.annotation.Nullable;

final class MpvNativeSessionRegistry {

  private static final Object LOCK = new Object();

  @Nullable private static Object activeOwner;
  private static boolean activeOwnerReleasing;
  private static boolean activeOwnerReserved;
  @Nullable private static Object pendingOwner;
  @Nullable private static Runnable pendingOwnerAvailable;

  static AcquireResult acquire(Object owner, Runnable onAvailable) {
    synchronized (LOCK) {
      if (activeOwner == null || activeOwner == owner) {
        activeOwner = owner;
        activeOwnerReserved = false;
        return AcquireResult.ACQUIRED;
      }
      if (!activeOwnerReleasing || (pendingOwner != null && pendingOwner != owner)) {
        return AcquireResult.UNAVAILABLE;
      }
      pendingOwner = owner;
      pendingOwnerAvailable = onAvailable;
      return AcquireResult.PENDING;
    }
  }

  static void beginRelease(Object owner) {
    synchronized (LOCK) {
      if (activeOwner == owner) {
        activeOwnerReleasing = true;
      }
    }
  }

  static void abortRelease(Object owner) {
    @Nullable Runnable onAvailable = null;
    synchronized (LOCK) {
      if (activeOwner != owner) {
        return;
      }
      activeOwnerReleasing = false;
      if (pendingOwner != null) {
        pendingOwner = null;
        onAvailable = pendingOwnerAvailable;
        pendingOwnerAvailable = null;
      }
    }
    if (onAvailable != null) {
      onAvailable.run();
    }
  }

  static void release(Object owner) {
    @Nullable Runnable onAvailable = null;
    synchronized (LOCK) {
      if (activeOwner != owner) {
        if (pendingOwner == owner) {
          pendingOwner = null;
          pendingOwnerAvailable = null;
        }
        return;
      }
      activeOwner = null;
      activeOwnerReleasing = false;
      activeOwnerReserved = false;
      if (pendingOwner != null) {
        activeOwner = pendingOwner;
        activeOwnerReserved = true;
        pendingOwner = null;
        onAvailable = pendingOwnerAvailable;
        pendingOwnerAvailable = null;
      }
    }
    if (onAvailable != null) {
      onAvailable.run();
    }
  }

  static void cancelPendingAcquire(Object owner) {
    synchronized (LOCK) {
      if (pendingOwner == owner) {
        pendingOwner = null;
        pendingOwnerAvailable = null;
      } else if (activeOwner == owner && activeOwnerReserved) {
        activeOwner = null;
        activeOwnerReserved = false;
      }
    }
  }

  enum AcquireResult {
    ACQUIRED,
    PENDING,
    UNAVAILABLE
  }
}
