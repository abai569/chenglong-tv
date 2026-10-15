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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.mpvplayer.MpvPlayerConfig;

@RestrictTo(LIBRARY_GROUP)
public final class MpvNetworkOptions {

  private static final String HLS_LAVF_OPTIONS_NO_HTTP_PERSISTENT = "http_persistent=0";
  private static final String HLS_LAVF_OPTIONS_NON_STANDARD_URI = "extension_picky=0," + HLS_LAVF_OPTIONS_NO_HTTP_PERSISTENT;
  private static final String OPT_DEMUXER_LAVF_FORMAT = "demuxer-lavf-format";
  private static final String OPT_DEMUXER_LAVF_O_ADD = "demuxer-lavf-o-add";
  private static final String OPT_PROXY_URL = "proxy-url";
  private static final String VALUE_HLS = "hls";

  static void addProxyUrlOption(MpvPlayerConfig.Builder builder) {
    builder.addPreInitStringOption(OPT_PROXY_URL, getLocalProxyUrl());
  }

  public static void apply(
      Uri uri, @Nullable String mimeType, MpvOptions.StringOptionWriter writer) {
    int uriContentType = Util.inferContentType(uri);
    if (uriContentType != C.CONTENT_TYPE_HLS
        && Util.inferContentTypeForUriAndMimeType(uri, mimeType) != C.CONTENT_TYPE_HLS) {
      return;
    }
    writer.set(OPT_DEMUXER_LAVF_FORMAT, VALUE_HLS);
    writer.set(
        OPT_DEMUXER_LAVF_O_ADD,
        uriContentType == C.CONTENT_TYPE_HLS
            ? HLS_LAVF_OPTIONS_NO_HTTP_PERSISTENT
            : HLS_LAVF_OPTIONS_NON_STANDARD_URI);
  }

  private static String getLocalProxyUrl() {
    return "http://127.0.0.1:" + getProxyPort() + "/proxy?";
  }

  private static int getProxyPort() {
    try {
      Object port = Class.forName("com.github.catvod.Proxy").getMethod("getPort").invoke(null);
      return port instanceof Number ? ((Number) port).intValue() : 9978;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return 9978;
    }
  }
}
