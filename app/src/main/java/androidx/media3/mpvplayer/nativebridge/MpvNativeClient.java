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

import android.content.Context;
import is.xyz.mpv.MPVLib;

interface MpvNativeClient {

  void clearLastResult();

  void clearPendingRequests();

  boolean create(Context context);

  boolean init();

  boolean destroy();

  void addObserver(MPVLib.EventObserver observer);

  void removeObserver(MPVLib.EventObserver observer);

  void addLogObserver();

  void removeLogObserver();

  boolean observeProperty(String property, int format);

  boolean hasLastFailure();
}
