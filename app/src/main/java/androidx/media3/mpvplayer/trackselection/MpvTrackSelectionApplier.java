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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_AUTO;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_NO;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import java.util.List;

final class MpvTrackSelectionApplier {

  private static final int[] TRACK_TYPES = {
    C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT
  };
  private static final int TRACK_AUTO = Integer.MIN_VALUE;

  private final MpvPropertyAccessor properties;
  private final boolean[] languageControlled;
  private final boolean[] selectionControlled;

  MpvTrackSelectionApplier(MpvPropertyAccessor properties) {
    this.properties = properties;
    this.languageControlled = new boolean[TRACK_TYPES.length];
    this.selectionControlled = new boolean[TRACK_TYPES.length];
  }

  private static @Nullable TrackSelectionOverride findApplicableOverride(
      int type, TrackSelectionParameters parameters, Tracks tracks) {
    TrackSelectionOverride applicableOverride = null;
    for (TrackSelectionOverride override : parameters.overrides.values()) {
      if (override.getType() == type && hasTrackGroup(tracks, override.mediaTrackGroup)) {
        applicableOverride = override;
      }
    }
    return applicableOverride;
  }

  private static boolean hasTrackGroup(Tracks tracks, TrackGroup mediaTrackGroup) {
    for (Tracks.Group group : tracks.getGroups()) {
      if (group.getMediaTrackGroup().equals(mediaTrackGroup)) {
        return true;
      }
    }
    return false;
  }

  boolean requiresNewFileApplication(TrackSelectionParameters parameters) {
    if (!parameters.preferredVideoLanguages.isEmpty()
        || !parameters.preferredAudioLanguages.isEmpty()
        || !parameters.preferredTextLanguages.isEmpty()
        || !parameters.disabledTrackTypes.isEmpty()
        || !parameters.overrides.isEmpty()) {
      return true;
    }
    for (int i = 0; i < TRACK_TYPES.length; i++) {
      if (languageControlled[i] || selectionControlled[i]) {
        return true;
      }
    }
    return false;
  }

  void applyForNewFile(TrackSelectionParameters parameters, Tracks tracks) {
    for (int i = 0; i < TRACK_TYPES.length; i++) {
      int type = TRACK_TYPES[i];
      List<String> languages = getTrackLanguages(type, parameters);
      if (!languages.isEmpty()) {
        setTrackLanguages(type, languages);
        languageControlled[i] = true;
      } else if (languageControlled[i]) {
        setTrackLanguages(type, languages);
        languageControlled[i] = false;
      }
      int selection = getExplicitTrackSelection(type, parameters, tracks);
      if (selection != TRACK_AUTO) {
        setTrackSelection(type, selection);
        selectionControlled[i] = true;
      } else if (selectionControlled[i]) {
        setTrackAuto(type);
        selectionControlled[i] = false;
      }
    }
  }

  void applyChanged(
      TrackSelectionParameters previousParameters,
      TrackSelectionParameters parameters,
      Tracks tracks) {
    for (int i = 0; i < TRACK_TYPES.length; i++) {
      int type = TRACK_TYPES[i];
      List<String> previousLanguages = getTrackLanguages(type, previousParameters);
      List<String> languages = getTrackLanguages(type, parameters);
      if (!previousLanguages.equals(languages)) {
        setTrackLanguages(type, languages);
        languageControlled[i] = !languages.isEmpty();
      }
      int previousSelection = getExplicitTrackSelection(type, previousParameters, tracks);
      int selection = getExplicitTrackSelection(type, parameters, tracks);
      if (previousSelection != selection) {
        setTrackSelection(type, selection);
        selectionControlled[i] = selection != TRACK_AUTO;
      }
    }
  }

  void reset() {
    for (int i = 0; i < TRACK_TYPES.length; i++) {
      languageControlled[i] = false;
      selectionControlled[i] = false;
    }
  }

  private static int getExplicitTrackSelection(
      int type, TrackSelectionParameters parameters, Tracks tracks) {
    if (parameters.disabledTrackTypes.contains(type)) {
      return C.INDEX_UNSET;
    }
    TrackSelectionOverride override = findApplicableOverride(type, parameters, tracks);
    if (override == null) {
      return TRACK_AUTO;
    }
    if (override.trackIndices.isEmpty()) {
      return C.INDEX_UNSET;
    }
    Format format = override.mediaTrackGroup.getFormat(override.trackIndices.get(0));
    return MpvTrackMapper.parseTrackId(format.id);
  }

  private static List<String> getTrackLanguages(int type, TrackSelectionParameters parameters) {
    switch (type) {
      case C.TRACK_TYPE_VIDEO:
        return parameters.preferredVideoLanguages;
      case C.TRACK_TYPE_AUDIO:
        return parameters.preferredAudioLanguages;
      case C.TRACK_TYPE_TEXT:
        return parameters.preferredTextLanguages;
      default:
        return List.of();
    }
  }

  private void setTrackLanguages(int type, List<String> languages) {
    String property = MpvTrackMapper.getTrackLanguageProperty(type);
    if (property != null) {
      properties.setStringOptionOrProperty(property, TextUtils.join(",", languages));
    }
  }

  private void setTrackSelection(int type, int selection) {
    if (selection == TRACK_AUTO) {
      setTrackAuto(type);
    } else {
      setTrackId(type, selection);
    }
  }

  private void setTrackAuto(int type) {
    String property = MpvTrackMapper.getTrackProperty(type);
    if (property != null) {
      properties.setStringProperty(property, VALUE_AUTO);
    }
  }

  private void setTrackId(int type, @Nullable Integer id) {
    String property = MpvTrackMapper.getTrackProperty(type);
    if (property == null) {
      return;
    }
    if (id == null || id == C.INDEX_UNSET) {
      properties.setStringProperty(property, VALUE_NO);
    } else {
      properties.setIntProperty(property, id);
    }
  }
}
