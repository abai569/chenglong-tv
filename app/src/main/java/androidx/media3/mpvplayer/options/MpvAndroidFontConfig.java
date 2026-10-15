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

import android.util.Xml;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.xmlpull.v1.XmlSerializer;

final class MpvAndroidFontConfig {

  private static final String FONTS_CONF = "fonts.conf";

  static void writeIfNeeded(File configDirectory, File cacheDirectory) {
    File outputFile = new File(configDirectory, FONTS_CONF);
    if (outputFile.isFile() && outputFile.length() > 0) {
      return;
    }
    try {
      MpvFileUtil.createDirectories(outputFile.getParentFile());
      MpvFileUtil.createDirectories(cacheDirectory);
    } catch (IOException e) {
      return;
    }
    try (FileOutputStream out = new FileOutputStream(outputFile, false)) {
      write(out, cacheDirectory);
    } catch (IOException e) {
      // Fontconfig improves Android OSD font selection, but playback can continue without it.
    }
  }

  private static void write(OutputStream out, File cacheDirectory) throws IOException {
    XmlSerializer serializer = Xml.newSerializer();
    serializer.setOutput(out, StandardCharsets.UTF_8.name());
    serializer.startDocument(StandardCharsets.UTF_8.name(), true);
    serializer.startTag(null, "fontconfig");
    writeTextTag(serializer, "dir", "/system/fonts/");
    writeTextTag(serializer, "dir", "/product/fonts/");
    writeTextTag(serializer, "cachedir", cacheDirectory.getAbsolutePath());
    writeFontAlias(serializer, "serif", "Noto Serif");
    writeFontAlias(serializer, "sans-serif", "Roboto", "Noto Sans");
    writeFontAlias(serializer, "monospace", "Droid Sans Mono");
    serializer.endTag(null, "fontconfig");
    serializer.endDocument();
    serializer.flush();
  }

  private static void writeFontAlias(
      XmlSerializer serializer, String family, String... preferredFamilies) throws IOException {
    serializer.startTag(null, "alias");
    writeTextTag(serializer, "family", family);
    serializer.startTag(null, "prefer");
    for (String preferredFamily : preferredFamilies) {
      writeTextTag(serializer, "family", preferredFamily);
    }
    serializer.endTag(null, "prefer");
    serializer.endTag(null, "alias");
  }

  private static void writeTextTag(XmlSerializer serializer, String name, String value)
      throws IOException {
    serializer.startTag(null, name);
    serializer.text(value);
    serializer.endTag(null, name);
  }
}
