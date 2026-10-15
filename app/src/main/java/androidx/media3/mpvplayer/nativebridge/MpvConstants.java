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

public final class MpvConstants {

  public static final String VALUE_NO = "no";
  public static final String VALUE_YES = "yes";
  public static final String VALUE_AUTO = "auto";
  public static final String VALUE_EMPTY = "";
  public static final String PROP_ANDROID_DOLBY_VISION_OUTPUT = "android-dolby-vision-output";
  public static final String PROP_ANDROID_OSD_SURFACE_SIZE = "android-osd-surface-size";
  public static final String PROP_ANDROID_SURFACE_SIZE = "android-surface-size";
  public static final String PROP_CHAPTER = "chapter";
  public static final String PROP_CHAPTER_LIST = "chapter-list";
  public static final String PROP_CURRENT_EDITION = "current-edition";
  public static final String PROP_CURRENT_VIDEO_ALBUMART = "current-tracks/video/albumart";
  public static final String PROP_DURATION_FULL = "duration/full";
  public static final String PROP_EDITION = "edition";
  public static final String PROP_EDITION_LIST = "edition-list";
  public static final String OPT_FORCE_WINDOW = "force-window";
  public static final String OPT_USER_AGENT = "user-agent";
  public static final String OPT_VIDEO_OUTPUT = "vo";
  public static final String PROP_HWDEC = "hwdec";
  public static final String PROP_MEDIA_LIVE = "media-live";
  public static final String PROP_SEEKABLE = "seekable";
  public static final String PROP_TIME_POS = "time-pos";
  public static final String PROP_TRACK_LIST = "track-list";
  public static final String PROP_VIDEO_ASPECT = "video-params/aspect";
  public static final String PROP_VIDEO_HEIGHT = "height";
  public static final String PROP_VIDEO_ROTATION = "video-params/rotate";
  public static final String PROP_VIDEO_WIDTH = "width";
  public static final long SEEK_INCREMENT_MS = 10000;
  public static final String TRACK_AID = "aid";
  public static final String TRACK_ALANG = "alang";
  public static final String TRACK_SID = "sid";
  public static final String TRACK_VID = "vid";
  public static final String TRACK_VLANG = "vlang";
  static final String VALUE_CACHED = "cached";
  static final String VALUE_REPLACE = "replace";
  static final String FILTER_OPERATION_ADD = "add";
  static final String FILTER_OPERATION_REMOVE = "remove";
  static final String CMD_AUDIO_FILTER = "af";
  static final String CMD_AUDIO_FILTER_COMMAND = "af-command";
  static final String CMD_LOAD_FILE = "loadfile";
  static final String CMD_SCRIPT_BINDING = "script-binding";
  static final String CMD_STOP = "stop";
  static final String CMD_SUB_ADD = "sub-add";
  static final String PROP_AUDIO_CHANNEL_COUNT = "audio-params/channel-count";
  static final String PROP_AUDIO_DELAY = "audio-delay";
  static final String PROP_AUDIO_OUTPUT_FORMAT = "audio-out-params/format";
  static final String PROP_BRIGHTNESS = "brightness";
  static final String PROP_CONTRAST = "contrast";
  static final String PROP_CURRENT_VIDEO_OUTPUT = "current-vo";
  static final String PROP_DEMUXER_CACHE_DURATION = "demuxer-cache-duration";
  static final String PROP_DEMUXER_CACHE_TIME = "demuxer-cache-time";
  static final String PROP_GAMMA = "gamma";
  static final String PROP_HUE = "hue";
  static final String PROP_IDLE_ACTIVE = "idle-active";
  static final String PROP_LOOP_FILE = "loop-file";
  static final String PROP_PAUSE = "pause";
  static final String PROP_PAUSED_FOR_CACHE = "paused-for-cache";
  static final String PROP_PITCH = "pitch";
  static final String PROP_SATURATION = "saturation";
  static final String PROP_SHARPEN = "sharpen";
  static final String PROP_SPEED = "speed";
  static final String PROP_SUB_DELAY = "sub-delay";
  static final String PROP_VOLUME = "volume";
  static final String STATS_GENERAL_PAGE_TOGGLE = "stats/display-page-1-toggle";
}
