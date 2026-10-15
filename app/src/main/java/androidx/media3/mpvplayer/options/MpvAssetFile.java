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
package androidx.media3.mpvplayer.options;

import android.content.Context;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class MpvAssetFile {

  static void copyIfNeeded(Context context, String assetName, File outputFile) {
    if (outputFile.isFile() && outputFile.length() > 0) {
      return;
    }
    try {
      MpvFileUtil.createDirectories(outputFile.getParentFile());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create mpv asset directory: " + assetName, e);
    }
    try (InputStream in = context.getAssets().open(assetName);
        FileOutputStream out = new FileOutputStream(outputFile, false)) {
      ByteStreams.copy(in, out);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to copy mpv asset: " + assetName, e);
    }
  }
}
