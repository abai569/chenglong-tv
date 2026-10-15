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

import static androidx.media3.mpvplayer.util.MpvUtil.formatDouble;

import androidx.annotation.Nullable;

public final class MpvPropertyAccessor {

  private final MpvClient client;
  private final MpvNativeState nativeState;

  public MpvPropertyAccessor(MpvClient client, MpvNativeState nativeState) {
    this.client = client;
    this.nativeState = nativeState;
  }

  public @Nullable Integer getInt(@Nullable String property) {
    if (property == null || !isInitialized()) {
      return null;
    }
    return client.getPropertyInt(property);
  }

  public @Nullable Boolean getBoolean(@Nullable String property) {
    if (property == null || !isInitialized()) {
      return null;
    }
    return client.getPropertyBoolean(property);
  }

  public @Nullable String getString(@Nullable String property) {
    if (property == null || !isInitialized()) {
      return null;
    }
    return client.getPropertyString(property);
  }

  public @Nullable Double getDouble(@Nullable String property) {
    if (property == null || !isInitialized()) {
      return null;
    }
    return client.getPropertyDouble(property);
  }

  public @Nullable byte[] getByteArray(@Nullable String property) {
    if (property == null || !isInitialized()) {
      return null;
    }
    return client.getPropertyByteArray(property);
  }

  public void setStringProperty(String name, String value) {
    if (!isInitialized()) {
      return;
    }
    client.setPropertyString(name, value);
  }

  public void setDoubleProperty(String name, double value) {
    if (!isInitialized()) {
      return;
    }
    client.setPropertyDouble(name, value);
  }

  public void setIntProperty(String name, int value) {
    if (!isInitialized()) {
      return;
    }
    client.setPropertyInt(name, value);
  }

  public void setStringOptionOrProperty(String name, String value) {
    if (isInitialized()) {
      client.setPropertyString(name, value);
    } else {
      client.setOptionString(name, value);
    }
  }

  public void setDoubleOptionOrProperty(String name, double value) {
    if (isInitialized()) {
      client.setPropertyDouble(name, value);
    } else {
      client.setOptionString(name, formatDouble(value));
    }
  }

  private boolean isInitialized() {
    return nativeState.isInitialized();
  }
}
