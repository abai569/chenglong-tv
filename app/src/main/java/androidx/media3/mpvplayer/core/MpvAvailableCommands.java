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
package androidx.media3.mpvplayer.core;

import androidx.media3.common.Player;

final class MpvAvailableCommands {

  private static final Player.Commands PERMANENT =
      new Player.Commands.Builder()
          .addAll(
              Player.COMMAND_PLAY_PAUSE,
              Player.COMMAND_PREPARE,
              Player.COMMAND_STOP,
              Player.COMMAND_SET_SPEED_AND_PITCH,
              Player.COMMAND_SET_REPEAT_MODE,
              Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
              Player.COMMAND_GET_TIMELINE,
              Player.COMMAND_GET_METADATA,
              Player.COMMAND_SET_MEDIA_ITEM,
              Player.COMMAND_CHANGE_MEDIA_ITEMS,
              Player.COMMAND_GET_AUDIO_ATTRIBUTES,
              Player.COMMAND_GET_VOLUME,
              Player.COMMAND_SET_VOLUME,
              Player.COMMAND_SET_AUDIO_ATTRIBUTES,
              Player.COMMAND_SET_VIDEO_SURFACE,
              Player.COMMAND_GET_TEXT_OFFSET,
              Player.COMMAND_SET_TEXT_OFFSET,
              Player.COMMAND_GET_AUDIO_OFFSET,
              Player.COMMAND_SET_AUDIO_OFFSET,
              Player.COMMAND_GET_TRACKS,
              Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
              Player.COMMAND_RELEASE)
          .build();

  static Player.Commands build(
      boolean timelineEmpty,
      boolean currentMediaSeekable,
      boolean currentMediaLive,
      boolean hasPreviousMediaItem,
      boolean hasNextMediaItem) {
    boolean canSeekToPrevious =
        !timelineEmpty && (hasPreviousMediaItem || !currentMediaLive || currentMediaSeekable);
    boolean canSeekToNext = !timelineEmpty && (hasNextMediaItem || currentMediaLive);
    return new Player.Commands.Builder()
        .addAll(PERMANENT)
        .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
        .addIf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, currentMediaSeekable)
        .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem)
        .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, canSeekToPrevious)
        .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem)
        .addIf(Player.COMMAND_SEEK_TO_NEXT, canSeekToNext)
        .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        .addIf(Player.COMMAND_SEEK_BACK, currentMediaSeekable)
        .addIf(Player.COMMAND_SEEK_FORWARD, currentMediaSeekable)
        .build();
  }
}
