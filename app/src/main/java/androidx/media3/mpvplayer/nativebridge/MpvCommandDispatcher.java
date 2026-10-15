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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.CMD_AUDIO_FILTER;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.CMD_AUDIO_FILTER_COMMAND;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.CMD_LOAD_FILE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.CMD_SCRIPT_BINDING;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.CMD_STOP;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.CMD_SUB_ADD;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.FILTER_OPERATION_ADD;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.FILTER_OPERATION_REMOVE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_TIME_POS;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.STATS_GENERAL_PAGE_TOGGLE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_AUTO;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_CACHED;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_REPLACE;

import android.net.Uri;
import android.text.TextUtils;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.mpvplayer.audio.MpvAudioFilterCommand;
import java.util.ArrayList;
import java.util.List;

public final class MpvCommandDispatcher {

  private static final String COMMAND_PREFIX_ASYNC = "async";
  private static final String LOAD_FILE_DEFAULT_INDEX = "-1";

  private final MpvClient client;

  public MpvCommandDispatcher(MpvClient client) {
    this.client = client;
  }

  private static String getSubtitleFlags(@C.SelectionFlags int selectionFlags, boolean select) {
    StringBuilder flags = new StringBuilder(select ? VALUE_CACHED : VALUE_AUTO);
    if ((selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0) {
      flags.append("+default");
    }
    if ((selectionFlags & C.SELECTION_FLAG_FORCED) != 0) {
      flags.append("+forced");
    }
    return flags.toString();
  }

  public boolean loadFile(Uri uri, List<String> perFileOptions) {
    String loadUri = uri.toString();
    if (!perFileOptions.isEmpty()) {
      return client.command(
          new String[] {
            CMD_LOAD_FILE,
            loadUri,
            VALUE_REPLACE,
            LOAD_FILE_DEFAULT_INDEX,
            encodePerFileOptions(perFileOptions)
          });
    }
    return client.command(new String[] {CMD_LOAD_FILE, loadUri, VALUE_REPLACE});
  }

  static String encodePerFileOptions(List<String> options) {
    List<String> encodedOptions = new ArrayList<>(options.size());
    for (String option : options) {
      int separator = option.indexOf('=');
      if (separator <= 0) {
        throw new IllegalArgumentException("Invalid per-file option: " + option);
      }
      encodedOptions.add(
          encodeSubparameter(option.substring(0, separator))
              + "="
              + encodeSubparameter(option.substring(separator + 1)));
    }
    return TextUtils.join(",", encodedOptions);
  }

  private static String encodeSubparameter(String value) {
    return "%" + Util.getUtf8Bytes(value).length + "%" + value;
  }

  public boolean seekTo(long positionMs, MpvClient.ResultCallback callback) {
    return client.setPropertyDouble(PROP_TIME_POS, positionMs / 1000.0, callback);
  }

  public boolean addAudioFilter(String audioFilter) {
    return !TextUtils.isEmpty(audioFilter)
        && client.command(new String[] {CMD_AUDIO_FILTER, FILTER_OPERATION_ADD, audioFilter});
  }

  public boolean removeAudioFilter(String label) {
    return !TextUtils.isEmpty(label)
        && client.command(
            new String[] {CMD_AUDIO_FILTER, FILTER_OPERATION_REMOVE, "@" + label});
  }

  public boolean sendAudioFilterCommands(List<MpvAudioFilterCommand> commands) {
    for (MpvAudioFilterCommand command : commands) {
      if (!sendAudioFilterCommand(command)) {
        return false;
      }
    }
    return true;
  }

  private boolean sendAudioFilterCommand(MpvAudioFilterCommand command) {
    return client.command(
        new String[] {
          CMD_AUDIO_FILTER_COMMAND,
          command.getLabel(),
          command.getCommand(),
          command.getArgument(),
          command.getTarget()
        });
  }

  public boolean stop() {
    return client.command(new String[] {CMD_STOP});
  }

  public boolean toggleGeneralStats() {
    return client.command(new String[] {CMD_SCRIPT_BINDING, STATS_GENERAL_PAGE_TOGGLE});
  }

  public boolean addSubtitle(MediaItem.SubtitleConfiguration subtitle, boolean select) {
    return client.command(getAddSubtitleCommand(subtitle, select));
  }

  static String[] getAddSubtitleCommand(
      MediaItem.SubtitleConfiguration subtitle, boolean select) {
    String label = TextUtils.isEmpty(subtitle.label) ? "" : subtitle.label;
    String language = TextUtils.isEmpty(subtitle.language) ? "" : subtitle.language;
    return new String[] {
      COMMAND_PREFIX_ASYNC,
      CMD_SUB_ADD,
      subtitle.uri.toString(),
      getSubtitleFlags(subtitle.selectionFlags, select),
      label,
      language
    };
  }
}
