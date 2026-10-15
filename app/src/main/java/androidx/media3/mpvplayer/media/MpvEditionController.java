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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CURRENT_EDITION;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_EDITION;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_EDITION_LIST;
import static androidx.media3.mpvplayer.util.MpvUtil.trimToNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaEdition;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import com.google.common.collect.ImmutableList;

public final class MpvEditionController {

  private static final int MIN_SELECTABLE_ENTRIES = 2;
  private static final String EDITION_LIST_COUNT = PROP_EDITION_LIST + "/count";
  private static final String EDITION_LIST_PREFIX = PROP_EDITION_LIST + "/";

  private final MpvPropertyAccessor properties;
  private ImmutableList<MediaEdition> editions;

  public MpvEditionController(MpvPropertyAccessor properties) {
    this.properties = properties;
    this.editions = ImmutableList.of();
  }

  public ImmutableList<MediaEdition> getEditions() {
    return editions;
  }

  public @Nullable MediaEdition getEdition(int index) {
    for (MediaEdition edition : editions) {
      if (edition.index == index) {
        return edition;
      }
    }
    return null;
  }

  public void clear() {
    editions = ImmutableList.of();
  }

  public boolean updateEditions(ImmutableList<MediaEdition> editions) {
    return replaceEditions(editions);
  }

  public boolean selectEdition(int index) {
    @Nullable MediaEdition edition = getEdition(index);
    if (edition == null || edition.selected) {
      return false;
    }
    properties.setIntProperty(PROP_EDITION, index);
    return applyEditionSelection(index);
  }

  public boolean updateEditionSelection(long index) {
    if (index < C.INDEX_UNSET || index > Integer.MAX_VALUE) {
      return false;
    }
    return applyEditionSelection((int) index);
  }

  public ImmutableList<MediaEdition> readEditions() {
    Integer count = properties.getInt(EDITION_LIST_COUNT);
    if (count == null || count < MIN_SELECTABLE_ENTRIES) {
      return ImmutableList.of();
    }
    int current = readCurrentEditionIndex();
    ImmutableList.Builder<MediaEdition> builder = ImmutableList.builderWithExpectedSize(count);
    for (int i = 0; i < count; i++) {
      builder.add(MediaEdition.edition(i, C.TIME_UNSET, readLabel(i), i == current));
    }
    return builder.build();
  }

  private int readCurrentEditionIndex() {
    Integer current = properties.getInt(PROP_CURRENT_EDITION);
    return current != null ? current : C.INDEX_UNSET;
  }

  private String readLabel(int index) {
    String title = trimToNull(properties.getString(EDITION_LIST_PREFIX + index + "/title"));
    return title != null ? title : "Edition " + (index + 1);
  }

  private boolean applyEditionSelection(int index) {
    return replaceEditions(withSelectedEdition(editions, index));
  }

  private static ImmutableList<MediaEdition> withSelectedEdition(
      ImmutableList<MediaEdition> editions, int index) {
    ImmutableList.Builder<MediaEdition> builder =
        ImmutableList.builderWithExpectedSize(editions.size());
    for (MediaEdition edition : editions) {
      builder.add(edition.withSelected(edition.index == index));
    }
    return builder.build();
  }

  private boolean replaceEditions(ImmutableList<MediaEdition> updated) {
    if (editions.equals(updated)) {
      return false;
    }
    editions = updated;
    return true;
  }
}
