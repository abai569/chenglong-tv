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

import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_AO_INIT_FAILED;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_COMMAND;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_EVENT_QUEUE_FULL;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_GENERIC;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_INVALID_PARAMETER;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_NOMEM;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_NOTHING_TO_PLAY;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_NOT_IMPLEMENTED;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_OPTION_ERROR;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_OPTION_FORMAT;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_OPTION_NOT_FOUND;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_PROPERTY_ERROR;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_PROPERTY_FORMAT;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_PROPERTY_NOT_FOUND;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_PROPERTY_UNAVAILABLE;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_UNINITIALIZED;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_UNSUPPORTED;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import com.google.common.base.Ascii;

final class MpvErrorMapper {

  static int getErrorCode(@Nullable Uri uri, int error) {
    switch (error) {
      case MPV_ERROR_AO_INIT_FAILED:
        return PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED;
      case MPV_ERROR_VO_INIT_FAILED:
      case MPV_ERROR_EVENT_QUEUE_FULL:
      case MPV_ERROR_NOMEM:
      case MPV_ERROR_UNINITIALIZED:
      case MPV_ERROR_COMMAND:
      case MPV_ERROR_UNSUPPORTED:
      case MPV_ERROR_NOT_IMPLEMENTED:
      case MPV_ERROR_PROPERTY_NOT_FOUND:
      case MPV_ERROR_PROPERTY_FORMAT:
      case MPV_ERROR_PROPERTY_UNAVAILABLE:
      case MPV_ERROR_PROPERTY_ERROR:
        return PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK;
      case MPV_ERROR_INVALID_PARAMETER:
      case MPV_ERROR_OPTION_NOT_FOUND:
      case MPV_ERROR_OPTION_FORMAT:
      case MPV_ERROR_OPTION_ERROR:
        return PlaybackException.ERROR_CODE_BAD_VALUE;
      case MPV_ERROR_NOTHING_TO_PLAY:
      case MPV_ERROR_UNKNOWN_FORMAT:
        return PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
      case MPV_ERROR_GENERIC:
        return PlaybackException.ERROR_CODE_UNSPECIFIED;
      default:
        return getGenericLoadErrorCode(uri);
    }
  }

  private static int getGenericLoadErrorCode(@Nullable Uri uri) {
    return isNetwork(uri)
        ? PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        : PlaybackException.ERROR_CODE_UNSPECIFIED;
  }

  private static boolean isNetwork(@Nullable Uri uri) {
    if (uri == null) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null) {
      return false;
    }
    switch (Ascii.toLowerCase(scheme)) {
      case "http":
      case "https":
      case "rtmp":
      case "rtsp":
      case "rtspt":
      case "proxy":
        return true;
      default:
        return false;
    }
  }
}
