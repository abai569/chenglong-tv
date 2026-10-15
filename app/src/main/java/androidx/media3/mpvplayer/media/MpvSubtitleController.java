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
package androidx.media3.mpvplayer.media;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.mpvplayer.nativebridge.MpvCommandDispatcher;
import androidx.media3.mpvplayer.options.MpvPerFileOptions;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MpvSubtitleController {

  private final MpvCommandDispatcher commandDispatcher;
  private final Map<String, String> originalStringOptions;
  private final Map<String, Double> originalDoubleOptions;
  private final Set<String> activeStringOptions;
  private final Set<String> activeDoubleOptions;
  private final Host host;

  private List<MediaItem.SubtitleConfiguration> pendingSubtitles;
  private MpvPlayerConfig options;

  public MpvSubtitleController(
      MpvCommandDispatcher commandDispatcher, Host host, MpvPlayerConfig initialOptions) {
    this.originalStringOptions = new HashMap<>();
    this.originalDoubleOptions = new HashMap<>();
    this.activeStringOptions = new HashSet<>();
    this.activeDoubleOptions = new HashSet<>();
    this.commandDispatcher = commandDispatcher;
    this.pendingSubtitles = ImmutableList.of();
    this.options = initialOptions;
    this.host = host;
  }

  private static boolean shouldSelectSubtitle(MediaItem.SubtitleConfiguration subtitle) {
    return (subtitle.selectionFlags & (C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED)) != 0;
  }

  public void setOptions(MpvPlayerConfig options) {
    this.options = options;
    applyOptionsIfInitialized();
  }

  public void applyOptionsIfInitialized() {
    if (host.isInitialized()) {
      applyOptions(options);
    }
  }

  public void addPerFileOptions(MpvPerFileOptions perFileOptions) {
    options.applySubtitle(perFileOptions::add, perFileOptions::add);
  }

  public void onNativeSessionEnded() {
    originalStringOptions.clear();
    originalDoubleOptions.clear();
    activeStringOptions.clear();
    activeDoubleOptions.clear();
  }

  private void applyOptions(MpvPlayerConfig options) {
    Set<String> nextStringOptions = new HashSet<>(options.getSubtitleStringOptionNames());
    Set<String> nextDoubleOptions = new HashSet<>(options.getSubtitleDoubleOptionNames());
    saveOriginalStringOptions(nextStringOptions);
    saveOriginalDoubleOptions(nextDoubleOptions);
    restoreRemovedStringOptions(nextStringOptions);
    restoreRemovedDoubleOptions(nextDoubleOptions);
    options.applySubtitle(host::setStringOptionOrProperty, host::setDoubleOptionOrProperty);
    activeStringOptions.clear();
    activeStringOptions.addAll(nextStringOptions);
    activeDoubleOptions.clear();
    activeDoubleOptions.addAll(nextDoubleOptions);
  }

  private void saveOriginalStringOptions(Set<String> nextOptions) {
    for (String option : nextOptions) {
      if (activeStringOptions.contains(option) || originalStringOptions.containsKey(option)) {
        continue;
      }
      @Nullable String originalValue = host.getStringOptionOrProperty(option);
      if (originalValue != null) {
        originalStringOptions.put(option, originalValue);
      }
    }
  }

  private void saveOriginalDoubleOptions(Set<String> nextOptions) {
    for (String option : nextOptions) {
      if (activeDoubleOptions.contains(option) || originalDoubleOptions.containsKey(option)) {
        continue;
      }
      @Nullable Double originalValue = host.getDoubleOptionOrProperty(option);
      if (originalValue != null) {
        originalDoubleOptions.put(option, originalValue);
      }
    }
  }

  private void restoreRemovedStringOptions(Set<String> nextOptions) {
    for (String option : ImmutableList.copyOf(activeStringOptions)) {
      if (nextOptions.contains(option)) {
        continue;
      }
      @Nullable String originalValue = originalStringOptions.remove(option);
      if (originalValue != null) {
        host.setStringOptionOrProperty(option, originalValue);
      }
    }
  }

  private void restoreRemovedDoubleOptions(Set<String> nextOptions) {
    for (String option : ImmutableList.copyOf(activeDoubleOptions)) {
      if (nextOptions.contains(option)) {
        continue;
      }
      @Nullable Double originalValue = originalDoubleOptions.remove(option);
      if (originalValue != null) {
        host.setDoubleOptionOrProperty(option, originalValue);
      }
    }
  }

  public void resetFor(MediaItem item) {
    pendingSubtitles = MpvMediaItems.getSubtitleConfigurations(item);
  }

  public void clear() {
    pendingSubtitles = ImmutableList.of();
  }

  public void addPendingSubtitles() {
    boolean selected = false;
    for (MediaItem.SubtitleConfiguration subtitle : pendingSubtitles) {
      boolean select = !selected && shouldSelectSubtitle(subtitle);
      if (commandDispatcher.addSubtitle(subtitle, select)) {
        selected |= select;
      }
    }
    clear();
  }

  public boolean addSubtitle(MediaItem.SubtitleConfiguration subtitle, boolean select) {
    if (!host.hasActiveFile() || host.currentMediaItem() == null || host.hasPlayerError()) {
      return false;
    }
    if (!host.isCurrentFileLoaded()) {
      pendingSubtitles =
          ImmutableList.<MediaItem.SubtitleConfiguration>builder()
              .addAll(pendingSubtitles)
              .add(subtitle)
              .build();
      return true;
    }
    commandDispatcher.addSubtitle(subtitle, select);
    return true;
  }

  public interface Host {

    boolean isInitialized();

    boolean hasActiveFile();

    @Nullable
    String getStringOptionOrProperty(String name);

    @Nullable
    Double getDoubleOptionOrProperty(String name);

    void setStringOptionOrProperty(String name, String value);

    void setDoubleOptionOrProperty(String name, double value);

    @Nullable
    MediaItem currentMediaItem();

    boolean isCurrentFileLoaded();

    boolean hasPlayerError();
  }
}
