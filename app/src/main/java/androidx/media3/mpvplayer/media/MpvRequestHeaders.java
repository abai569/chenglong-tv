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
package androidx.media3.mpvplayer.media;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import com.google.common.net.HttpHeaders;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class MpvRequestHeaders {

  static boolean same(MediaItem first, MediaItem second) {
    return from(first).sameAs(from(second));
  }

  static Headers from(MediaItem item) {
    Bundle extras = item.requestMetadata.extras;
    String userAgent = null;
    String referrer = null;
    Map<String, String> headerFields = new LinkedHashMap<>();
    if (extras != null) {
      for (String key : extras.keySet()) {
        String value = extras.getString(key);
        if (TextUtils.isEmpty(value)) {
          continue;
        }
        if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) {
          userAgent = value;
        } else if (HttpHeaders.REFERER.equalsIgnoreCase(key)) {
          referrer = value;
        } else {
          headerFields.put(key, value);
        }
      }
    }
    return new Headers(userAgent, referrer, headerFields);
  }

  static final class Headers {

    @Nullable private final String userAgent;
    @Nullable private final String referrer;
    private final Map<String, String> headerFields;

    private Headers(
        @Nullable String userAgent, @Nullable String referrer, Map<String, String> headerFields) {
      this.userAgent = userAgent;
      this.referrer = referrer;
      this.headerFields = headerFields;
    }

    private static void appendEscaped(StringBuilder builder, String value) {
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (c == '\\' || c == ',') {
          builder.append('\\');
        }
        builder.append(c);
      }
    }

    @Nullable
    String userAgent() {
      return userAgent;
    }

    @Nullable
    String referrer() {
      return referrer;
    }

    String mpvHeaderFields() {
      StringBuilder builder = new StringBuilder();
      for (Map.Entry<String, String> entry : headerFields.entrySet()) {
        if (builder.length() > 0) {
          builder.append(',');
        }
        appendEscaped(builder, entry.getKey());
        builder.append(": ");
        appendEscaped(builder, entry.getValue());
      }
      return builder.toString();
    }

    boolean sameAs(Headers other) {
      return Objects.equals(userAgent, other.userAgent)
          && Objects.equals(referrer, other.referrer)
          && headerFields.equals(other.headerFields);
    }
  }
}
