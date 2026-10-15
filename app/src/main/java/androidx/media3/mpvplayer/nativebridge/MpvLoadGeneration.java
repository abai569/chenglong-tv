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

import java.util.concurrent.atomic.AtomicLong;

public final class MpvLoadGeneration {

  private final AtomicLong requestedGeneration = new AtomicLong();
  private final AtomicLong activeGeneration = new AtomicLong();

  public void onLoadRequested() {
    requestedGeneration.incrementAndGet();
  }

  void onStartFile() {
    activeGeneration.set(requestedGeneration.get());
  }

  long captureActive() {
    return activeGeneration.get();
  }

  boolean isCurrent(long generation) {
    return requestedGeneration.get() == generation;
  }
}
