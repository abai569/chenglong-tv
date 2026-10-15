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
package androidx.media3.mpvplayer;

import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.util.LibraryLoader;

/** Configures and queries the underlying mpv native libraries. */
public final class MpvLibrary {

  private static final LibraryLoader LOADER =
      new LibraryLoader("mpv", "player") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  static {
    MediaLibraryInfo.registerModule("media3.mpvplayer");
  }

  /**
   * Overrides the names of the mpv native libraries.
   *
   * <p>If an application calls this method, it must do so before calling any other method defined
   * by this class and before instantiating an {@link MpvPlayer}.
   *
   * @param libraries The names of the mpv native libraries.
   */
  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  /** Returns whether the underlying native libraries are available, loading them if necessary. */
  public static boolean isAvailable() {
    return LOADER.isAvailable();
  }
}
