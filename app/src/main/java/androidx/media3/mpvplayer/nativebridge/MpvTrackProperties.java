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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_TRACK_LIST;

public final class MpvTrackProperties {

  private static final String TRACK_LIST_COUNT = PROP_TRACK_LIST + "/count";
  private static final String TRACK_LIST_PREFIX = PROP_TRACK_LIST + "/";

  public static String listCount() {
    return TRACK_LIST_COUNT;
  }

  public static String albumArt(int index) {
    return field(index, "albumart");
  }

  public static String albumArtData(int index) {
    return field(index, "albumart-data");
  }

  public static String codec(int index) {
    return field(index, "codec");
  }

  public static String codecProfile(int index) {
    return field(index, "codec-profile");
  }

  public static String defaultFlag(int index) {
    return field(index, "default");
  }

  public static String demuxBitrate(int index) {
    return field(index, "demux-bitrate");
  }

  public static String demuxChannelCount(int index) {
    return field(index, "demux-channel-count");
  }

  public static String demuxFps(int index) {
    return field(index, "demux-fps");
  }

  public static String demuxHeight(int index) {
    return field(index, "demux-h");
  }

  public static String demuxSampleRate(int index) {
    return field(index, "demux-samplerate");
  }

  public static String demuxWidth(int index) {
    return field(index, "demux-w");
  }

  public static String dependent(int index) {
    return field(index, "dependent");
  }

  public static String forced(int index) {
    return field(index, "forced");
  }

  public static String hearingImpaired(int index) {
    return field(index, "hearing-impaired");
  }

  public static String id(int index) {
    return field(index, "id");
  }

  public static String language(int index) {
    return field(index, "lang");
  }

  public static String selected(int index) {
    return field(index, "selected");
  }

  public static String title(int index) {
    return field(index, "title");
  }

  public static String type(int index) {
    return field(index, "type");
  }

  public static String visualImpaired(int index) {
    return field(index, "visual-impaired");
  }

  private static String field(int index, String field) {
    return TRACK_LIST_PREFIX + index + "/" + field;
  }
}
