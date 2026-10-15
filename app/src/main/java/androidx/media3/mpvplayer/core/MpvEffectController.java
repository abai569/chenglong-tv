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

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.mpvplayer.audio.MpvAudioFilter;
import androidx.media3.mpvplayer.audio.MpvAudioFilterCommand;
import androidx.media3.mpvplayer.nativebridge.MpvCommandDispatcher;
import androidx.media3.mpvplayer.nativebridge.MpvPlaybackProperties;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;
import androidx.media3.mpvplayer.video.MpvVideoEqualizer;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

public final class MpvEffectController {

  private static final String OPT_AUDIO_FILTER_ADD = "af-add";

  private final MpvPlaybackProperties playbackProperties;
  private final MpvCommandDispatcher commandDispatcher;
  private final Supplier<Boolean> activeFileSupplier;
  private final boolean audioPassthroughEnabled;

  @Nullable private MpvVideoEqualizer videoEqualizer;
  @Nullable private MpvVideoEqualizer appliedVideoEqualizer;
  @Nullable private MpvAudioFilter audioFilter;
  @Nullable private MpvAudioFilter appliedAudioFilter;

  public MpvEffectController(
      MpvPlaybackProperties playbackProperties,
      MpvCommandDispatcher commandDispatcher,
      Supplier<Boolean> activeFileSupplier,
      boolean audioPassthroughEnabled) {
    this.playbackProperties = playbackProperties;
    this.commandDispatcher = commandDispatcher;
    this.activeFileSupplier = activeFileSupplier;
    this.audioPassthroughEnabled = audioPassthroughEnabled;
  }

  public void setVideoEqualizer(MpvVideoEqualizer videoEqualizer) {
    MpvVideoEqualizer checkedVideoEqualizer = checkNotNull(videoEqualizer);
    this.videoEqualizer = checkedVideoEqualizer;
    if (!activeFileSupplier.get() || checkedVideoEqualizer.equals(appliedVideoEqualizer)) {
      return;
    }
    applyVideoEqualizer(checkedVideoEqualizer);
  }

  public boolean setAudioFilter(MpvAudioFilter audioFilter) {
    this.audioFilter = checkNotNull(audioFilter);
    if (!activeFileSupplier.get()) {
      return true;
    }
    @Nullable MpvAudioFilter targetAudioFilter = getTargetAudioFilter();
    return targetAudioFilter == null
        || targetAudioFilter.equals(appliedAudioFilter)
        || applyAudioFilter(targetAudioFilter);
  }

  public void addPerFileOptions(MpvPerFileOptions options) {
    if (videoEqualizer != null) {
      playbackProperties.addVideoEqualizerPerFileOptions(options, videoEqualizer);
      appliedVideoEqualizer = videoEqualizer;
    }
    if (audioFilter != null) {
      if (!audioPassthroughEnabled && !audioFilter.getFilter().isEmpty()) {
        options.add(OPT_AUDIO_FILTER_ADD, audioFilter.getLabeledFilter());
      }
      appliedAudioFilter = audioPassthroughEnabled ? MpvAudioFilter.EMPTY : audioFilter;
    }
  }

  public void onAudioOutputChanged() {
    if (!activeFileSupplier.get() || audioFilter == null) {
      return;
    }
    @Nullable MpvAudioFilter targetAudioFilter = getTargetAudioFilter();
    if (targetAudioFilter != null && !targetAudioFilter.equals(appliedAudioFilter)) {
      applyAudioFilter(targetAudioFilter);
    }
  }

  public void onNativeSessionEnded() {
    appliedAudioFilter = null;
    appliedVideoEqualizer = null;
  }

  private void applyVideoEqualizer(MpvVideoEqualizer videoEqualizer) {
    playbackProperties.setVideoEqualizer(videoEqualizer);
    appliedVideoEqualizer = videoEqualizer;
  }

  private @Nullable MpvAudioFilter getTargetAudioFilter() {
    if (!audioPassthroughEnabled) {
      return audioFilter;
    }
    @Nullable String format = playbackProperties.getAudioOutputFormat();
    if (format == null || format.isEmpty()) {
      return null;
    }
    return format.startsWith("spdif-") ? MpvAudioFilter.EMPTY : audioFilter;
  }

  private boolean applyAudioFilter(MpvAudioFilter audioFilter) {
    if (audioFilter.getFilter().isEmpty()) {
      return removeAppliedAudioFilter();
    }
    return hasSameGraph(audioFilter)
        ? updateAudioFilter(audioFilter)
        : replaceAudioFilter(audioFilter);
  }

  private boolean hasSameGraph(MpvAudioFilter audioFilter) {
    return appliedAudioFilter != null
        && audioFilter.getGraphKey().equals(appliedAudioFilter.getGraphKey());
  }

  private boolean updateAudioFilter(MpvAudioFilter audioFilter) {
    if (!commandDispatcher.sendAudioFilterCommands(
        getChangedAudioFilterCommands(audioFilter, checkNotNull(appliedAudioFilter)))) {
      return false;
    }
    appliedAudioFilter = audioFilter;
    return true;
  }

  private boolean replaceAudioFilter(MpvAudioFilter audioFilter) {
    if (!removeAppliedAudioFilter()) {
      return false;
    }
    if (!commandDispatcher.addAudioFilter(audioFilter.getLabeledFilter())) {
      return false;
    }
    appliedAudioFilter = audioFilter;
    return true;
  }

  private boolean removeAppliedAudioFilter() {
    if (appliedAudioFilter != null
        && !appliedAudioFilter.getFilter().isEmpty()
        && !commandDispatcher.removeAudioFilter(MpvAudioFilter.LABEL)) {
      return false;
    }
    appliedAudioFilter = MpvAudioFilter.EMPTY;
    return true;
  }

  private static ImmutableList<MpvAudioFilterCommand> getChangedAudioFilterCommands(
      MpvAudioFilter audioFilter, MpvAudioFilter appliedAudioFilter) {
    ImmutableList.Builder<MpvAudioFilterCommand> commands = ImmutableList.builder();
    for (MpvAudioFilterCommand command : audioFilter.getCommands()) {
      if (!appliedAudioFilter.getCommands().contains(command)) {
        commands.add(command);
      }
    }
    return commands.build();
  }
}
