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
package androidx.media3.mpvplayer.trackselection;

import androidx.annotation.Nullable;
import androidx.media3.common.Tracks;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;

final class MpvTrackReader implements MpvTrackMapper.Reader {

  private final MpvPropertyAccessor properties;

  MpvTrackReader(MpvPropertyAccessor properties) {
    this.properties = properties;
  }

  Tracks read() {
    return MpvTrackMapper.read(this);
  }

  @Override
  public @Nullable Integer getInt(String property) {
    return properties.getInt(property);
  }

  @Override
  public @Nullable Boolean getBoolean(String property) {
    return properties.getBoolean(property);
  }

  @Override
  public @Nullable String getString(String property) {
    return properties.getString(property);
  }

  @Override
  public @Nullable Double getDouble(String property) {
    return properties.getDouble(property);
  }
}
