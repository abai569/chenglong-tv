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
import static androidx.media3.mpvplayer.util.MpvUtil.formatDouble;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.RestrictTo;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.Map;

@RestrictTo(LIBRARY_GROUP)
public final class MpvPerFileOptions {

  private final Map<String, String> values;

  public MpvPerFileOptions() {
    values = new LinkedHashMap<>();
  }

  public void add(String name, String value) {
    validate(name, value);
    checkArgument(!values.containsKey(name), "Duplicate per-file option: %s", name);
    values.put(name, value);
  }

  public void add(String name, double value) {
    add(name, formatDouble(value));
  }

  public void set(String name, String value) {
    validate(name, value);
    values.put(name, value);
  }

  public ImmutableList<String> build() {
    ImmutableList.Builder<String> options = ImmutableList.builderWithExpectedSize(values.size());
    for (Map.Entry<String, String> entry : values.entrySet()) {
      options.add(entry.getKey() + "=" + entry.getValue());
    }
    return options.build();
  }

  private static void validate(String name, String value) {
    checkArgument(!checkNotNull(name).isEmpty());
    checkNotNull(value);
  }
}
