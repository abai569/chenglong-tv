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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.TRACK_AID;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.TRACK_ALANG;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.TRACK_SID;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.TRACK_VID;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.TRACK_VLANG;
import static androidx.media3.mpvplayer.util.MpvUtil.trimToNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.mpvplayer.nativebridge.MpvTrackProperties;
import java.util.ArrayList;
import java.util.List;

final class MpvTrackMapper {

  private static final String TRACK_AUDIO = "audio";
  private static final String TRACK_SUB = "sub";
  private static final String TRACK_VIDEO = "video";
  private static final String TRACK_LIST_AUDIO_ID = "mpv-audio";
  private static final String TRACK_LIST_TEXT_ID = "mpv-text";
  private static final String TRACK_LIST_VIDEO_ID = "mpv-video";
  private static final String TRACK_SLANG = "slang";

  static Tracks read(Reader reader) {
    List<MpvTrack> video = new ArrayList<>();
    List<MpvTrack> audio = new ArrayList<>();
    List<MpvTrack> text = new ArrayList<>();
    Integer count = reader.getInt(MpvTrackProperties.listCount());
    if (count == null || count <= 0) {
      return Tracks.EMPTY;
    }
    for (int i = 0; i < count; i++) {
      String typeName = reader.getString(MpvTrackProperties.type(i));
      Integer type = getTrackType(typeName);
      Integer id = reader.getInt(MpvTrackProperties.id(i));
      if (type == null || id == null) {
        continue;
      }
      if (type == C.TRACK_TYPE_VIDEO && isAlbumArtTrack(reader, i)) {
        continue;
      }
      MpvTrack track =
          new MpvTrack(id, buildFormat(reader, i, id, type), isTrackSelected(reader, i, id, type));
      addTrackByType(video, audio, text, type, track);
    }
    List<Tracks.Group> groups = new ArrayList<>(video.size() + audio.size() + text.size());
    addTrackGroups(groups, TRACK_LIST_VIDEO_ID, video);
    addTrackGroups(groups, TRACK_LIST_AUDIO_ID, audio);
    addTrackGroups(groups, TRACK_LIST_TEXT_ID, text);
    return groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
  }

  static @Nullable String getTrackProperty(int type) {
    switch (type) {
      case C.TRACK_TYPE_VIDEO:
        return TRACK_VID;
      case C.TRACK_TYPE_AUDIO:
        return TRACK_AID;
      case C.TRACK_TYPE_TEXT:
        return TRACK_SID;
      default:
        return null;
    }
  }

  static @Nullable String getTrackLanguageProperty(int type) {
    switch (type) {
      case C.TRACK_TYPE_VIDEO:
        return TRACK_VLANG;
      case C.TRACK_TYPE_AUDIO:
        return TRACK_ALANG;
      case C.TRACK_TYPE_TEXT:
        return TRACK_SLANG;
      default:
        return null;
    }
  }

  static int parseTrackId(@Nullable String value) {
    if (value == null) {
      return C.INDEX_UNSET;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return C.INDEX_UNSET;
    }
  }

  private static Format buildFormat(Reader reader, int index, int id, int type) {
    String title = trimToNull(reader.getString(MpvTrackProperties.title(index)));
    String language = normalizeLanguage(reader.getString(MpvTrackProperties.language(index)));
    String codec = trimToNull(reader.getString(MpvTrackProperties.codec(index)));
    String codecProfile = trimToNull(reader.getString(MpvTrackProperties.codecProfile(index)));
    String sampleMimeType = MpvCodecMimeTypes.getSampleMimeType(type, codec, codecProfile);
    boolean defaultTrack = isFlagSet(reader, MpvTrackProperties.defaultFlag(index));
    boolean forcedTrack = isFlagSet(reader, MpvTrackProperties.forced(index));
    Format.Builder builder =
        new Format.Builder()
            .setId(String.valueOf(id))
            .setLabel(title)
            .setLanguage(language)
            .setCodecs(MpvCodecMimeTypes.getCodecs(codec, sampleMimeType, codecProfile))
            .setSampleMimeType(sampleMimeType)
            .setSelectionFlags(getSelectionFlags(defaultTrack, forcedTrack))
            .setRoleFlags(getRoleFlags(reader, index, type, defaultTrack));
    if (type == C.TRACK_TYPE_VIDEO) {
      setOptionalInt(builder::setWidth, reader.getInt(MpvTrackProperties.demuxWidth(index)));
      setOptionalInt(builder::setHeight, reader.getInt(MpvTrackProperties.demuxHeight(index)));
      setOptionalInt(
          builder::setAverageBitrate, reader.getInt(MpvTrackProperties.demuxBitrate(index)));
      setOptionalFloat(builder::setFrameRate, reader.getDouble(MpvTrackProperties.demuxFps(index)));
    } else if (type == C.TRACK_TYPE_AUDIO) {
      setOptionalInt(
          builder::setChannelCount, reader.getInt(MpvTrackProperties.demuxChannelCount(index)));
      setOptionalInt(
          builder::setSampleRate, reader.getInt(MpvTrackProperties.demuxSampleRate(index)));
      setOptionalInt(
          builder::setAverageBitrate, reader.getInt(MpvTrackProperties.demuxBitrate(index)));
      setPcmEncoding(builder, codec);
    }
    return builder.build();
  }

  private static void addTrackGroups(
      List<Tracks.Group> groups, String idPrefix, List<MpvTrack> tracks) {
    for (MpvTrack track : tracks) {
      addTrackGroup(groups, idPrefix + "-" + track.id(), track);
    }
  }

  private static void addTrackGroup(List<Tracks.Group> groups, String id, MpvTrack track) {
    groups.add(
        new Tracks.Group(
            new TrackGroup(id, track.format()),
            false,
            new int[] {C.FORMAT_HANDLED},
            new boolean[] {track.selected()}));
  }

  private static void addTrackByType(
      List<MpvTrack> video, List<MpvTrack> audio, List<MpvTrack> text, int type, MpvTrack track) {
    switch (type) {
      case C.TRACK_TYPE_VIDEO:
        video.add(track);
        break;
      case C.TRACK_TYPE_AUDIO:
        audio.add(track);
        break;
      case C.TRACK_TYPE_TEXT:
        text.add(track);
        break;
      default:
        break;
    }
  }

  private static @Nullable Integer getTrackType(@Nullable String type) {
    if (type == null) {
      return null;
    }
    switch (type) {
      case TRACK_VIDEO:
        return C.TRACK_TYPE_VIDEO;
      case TRACK_AUDIO:
        return C.TRACK_TYPE_AUDIO;
      case TRACK_SUB:
        return C.TRACK_TYPE_TEXT;
      default:
        return null;
    }
  }

  private static boolean isTrackSelected(Reader reader, int index, int id, int type) {
    Boolean selected = reader.getBoolean(MpvTrackProperties.selected(index));
    if (selected != null) {
      return selected;
    }
    String property = getTrackProperty(type);
    if (property == null) {
      return false;
    }
    Integer current = reader.getInt(property);
    return current != null && current == id;
  }

  private static int getSelectionFlags(boolean defaultTrack, boolean forcedTrack) {
    int flags = 0;
    if (defaultTrack) {
      flags |= C.SELECTION_FLAG_DEFAULT;
    }
    if (forcedTrack) {
      flags |= C.SELECTION_FLAG_FORCED;
    }
    return flags;
  }

  private static int getRoleFlags(Reader reader, int index, int type, boolean defaultTrack) {
    int flags = type == C.TRACK_TYPE_TEXT ? C.ROLE_FLAG_SUBTITLE : 0;
    if (defaultTrack) {
      flags |= C.ROLE_FLAG_MAIN;
    }
    if (isFlagSet(reader, MpvTrackProperties.dependent(index))) {
      flags |= C.ROLE_FLAG_SUPPLEMENTARY;
    }
    if (isFlagSet(reader, MpvTrackProperties.visualImpaired(index))) {
      flags |= C.ROLE_FLAG_DESCRIBES_VIDEO;
    }
    if (isFlagSet(reader, MpvTrackProperties.hearingImpaired(index))) {
      flags |= C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND;
    }
    return flags;
  }

  private static boolean isAlbumArtTrack(Reader reader, int index) {
    return isFlagSet(reader, MpvTrackProperties.albumArt(index));
  }

  private static boolean isFlagSet(Reader reader, String property) {
    return Boolean.TRUE.equals(reader.getBoolean(property));
  }

  private static void setPcmEncoding(Format.Builder builder, @Nullable String codec) {
    Integer encoding = MpvCodecMimeTypes.getPcmEncoding(codec);
    if (encoding != null) {
      builder.setPcmEncoding(encoding);
    }
  }

  private static @Nullable String normalizeLanguage(@Nullable String language) {
    String value = trimToNull(language);
    return value == null || "und".equalsIgnoreCase(value) ? null : value;
  }

  private static void setOptionalInt(IntSetter setter, @Nullable Integer value) {
    if (value != null && value > 0) {
      setter.set(value);
    }
  }

  private static void setOptionalFloat(FloatSetter setter, @Nullable Double value) {
    if (value != null && value > 0 && Double.isFinite(value)) {
      setter.set(value.floatValue());
    }
  }

  interface Reader {

    @Nullable
    Integer getInt(String property);

    @Nullable
    Boolean getBoolean(String property);

    @Nullable
    String getString(String property);

    @Nullable
    Double getDouble(String property);
  }

  private interface FloatSetter {

    void set(float value);
  }

  private interface IntSetter {

    void set(int value);
  }

  static final class MpvTrack {

    private final int id;
    private final Format format;
    private final boolean selected;

    MpvTrack(int id, Format format, boolean selected) {
      this.id = id;
      this.format = format;
      this.selected = selected;
    }

    int id() {
      return id;
    }

    Format format() {
      return format;
    }

    boolean selected() {
      return selected;
    }
  }
}
