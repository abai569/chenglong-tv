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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CHAPTER;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CHAPTER_LIST;
import static androidx.media3.mpvplayer.util.MpvUtil.trimToNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaChapter;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import com.google.common.collect.ImmutableList;

public final class MpvChapterController {

  private static final int MIN_SELECTABLE_ENTRIES = 2;
  private static final String CHAPTER_LIST_COUNT = PROP_CHAPTER_LIST + "/count";
  private static final String CHAPTER_LIST_PREFIX = PROP_CHAPTER_LIST + "/";

  private final MpvPropertyAccessor properties;
  private ImmutableList<MediaChapter> chapters;

  public MpvChapterController(MpvPropertyAccessor properties) {
    this.properties = properties;
    this.chapters = ImmutableList.of();
  }

  private static String field(int index, String name) {
    return CHAPTER_LIST_PREFIX + index + "/" + name;
  }

  public ImmutableList<MediaChapter> getChapters() {
    return chapters;
  }

  public @Nullable MediaChapter getChapter(int index) {
    for (MediaChapter chapter : chapters) {
      if (chapter.index == index) {
        return chapter;
      }
    }
    return null;
  }

  public void clear() {
    chapters = ImmutableList.of();
  }

  public boolean updateChapters(ImmutableList<MediaChapter> chapters) {
    return replaceChapters(chapters);
  }

  public boolean selectChapter(int index) {
    if (getChapter(index) == null) {
      return false;
    }
    properties.setIntProperty(PROP_CHAPTER, index);
    return applyChapterSelection(index);
  }

  public boolean updateChapterSelection(long index) {
    if (index < C.INDEX_UNSET || index > Integer.MAX_VALUE) {
      return false;
    }
    return applyChapterSelection((int) index);
  }

  public ImmutableList<MediaChapter> readChapters() {
    Integer count = properties.getInt(CHAPTER_LIST_COUNT);
    if (count == null || count < MIN_SELECTABLE_ENTRIES) {
      return ImmutableList.of();
    }
    int current = readCurrentChapterIndex();
    ImmutableList.Builder<MediaChapter> builder = ImmutableList.builderWithExpectedSize(count);
    for (int i = 0; i < count; i++) {
      builder.add(MediaChapter.chapter(i, readTimeUs(i), readLabel(i), i == current));
    }
    return builder.build();
  }

  private int readCurrentChapterIndex() {
    Integer current = properties.getInt(PROP_CHAPTER);
    return current != null ? current : C.INDEX_UNSET;
  }

  private long readTimeUs(int index) {
    Double seconds = properties.getDouble(field(index, "time"));
    if (seconds == null || !Double.isFinite(seconds) || seconds < 0) {
      return C.TIME_UNSET;
    }
    return Math.round(seconds * C.MICROS_PER_SECOND);
  }

  private String readLabel(int index) {
    String title = trimToNull(properties.getString(field(index, "title")));
    return title != null ? title : "Chapter " + (index + 1);
  }

  private boolean applyChapterSelection(int index) {
    return replaceChapters(withSelectedChapter(chapters, index));
  }

  private static ImmutableList<MediaChapter> withSelectedChapter(
      ImmutableList<MediaChapter> chapters, int index) {
    ImmutableList.Builder<MediaChapter> builder =
        ImmutableList.builderWithExpectedSize(chapters.size());
    for (MediaChapter chapter : chapters) {
      builder.add(chapter.withSelected(chapter.index == index));
    }
    return builder.build();
  }

  private boolean replaceChapters(ImmutableList<MediaChapter> updated) {
    if (chapters.equals(updated)) {
      return false;
    }
    chapters = updated;
    return true;
  }
}
