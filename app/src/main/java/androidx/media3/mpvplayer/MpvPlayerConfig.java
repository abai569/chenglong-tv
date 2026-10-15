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
package androidx.media3.mpvplayer;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.DolbyVisionOutputPolicy;
import androidx.media3.mpvplayer.options.MpvNetworkOptions;
import androidx.media3.mpvplayer.options.MpvOptions;
import androidx.media3.mpvplayer.options.MpvPlayerOptionDefaults;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MpvPlayerConfig {

  private final String defaultUserAgent;
  private final String defaultHardwareDecode;
  private final ImmutableList<StringOption> preInitStringOptions;
  private final ImmutableList<StringOption> appOwnedStringOptions;
  private final ImmutableList<StringOption> subtitleStringOptions;
  private final ImmutableList<DoubleOption> subtitleDoubleOptions;
  @Nullable private final String requestedPassthroughCodecs;
  private final @DolbyVisionOutputPolicy.Mode int dolbyVisionOutputPolicy;
  private final boolean dolbyVisionOutputPolicySet;
  private final boolean videoSharpnessSupported;

  private MpvPlayerConfig(Builder builder) {
    this.defaultUserAgent = nullToEmpty(builder.defaultUserAgent);
    this.defaultHardwareDecode = nullToEmpty(builder.defaultHardwareDecode);
    this.preInitStringOptions = ImmutableList.copyOf(builder.preInitStringOptions);
    this.appOwnedStringOptions = ImmutableList.copyOf(builder.appOwnedStringOptions);
    this.subtitleStringOptions = ImmutableList.copyOf(builder.subtitleStringOptions);
    this.subtitleDoubleOptions = ImmutableList.copyOf(builder.subtitleDoubleOptions);
    this.requestedPassthroughCodecs = builder.requestedPassthroughCodecs;
    this.dolbyVisionOutputPolicy = builder.dolbyVisionOutputPolicy;
    this.dolbyVisionOutputPolicySet = builder.dolbyVisionOutputPolicySet;
    this.videoSharpnessSupported = builder.videoSharpnessSupported;
  }

  private static void applyStrings(
      List<StringOption> options, MpvOptions.StringOptionWriter writer) {
    for (StringOption option : options) {
      writer.set(option.name, option.value);
    }
  }

  private static void applyDoubles(
      List<DoubleOption> options, MpvOptions.DoubleOptionWriter writer) {
    for (DoubleOption option : options) {
      writer.set(option.name, option.value);
    }
  }

  private static ImmutableList<String> getStringNames(List<StringOption> options) {
    ImmutableList.Builder<String> names = ImmutableList.builder();
    for (StringOption option : options) {
      names.add(option.name);
    }
    return names.build();
  }

  private static ImmutableList<String> getDoubleNames(List<DoubleOption> options) {
    ImmutableList.Builder<String> names = ImmutableList.builder();
    for (DoubleOption option : options) {
      names.add(option.name);
    }
    return names.build();
  }

  @RestrictTo(LIBRARY_GROUP)
  public String getDefaultUserAgent() {
    return defaultUserAgent;
  }

  @RestrictTo(LIBRARY_GROUP)
  public String getDefaultHardwareDecode() {
    return defaultHardwareDecode;
  }

  @RestrictTo(LIBRARY_GROUP)
  public @DolbyVisionOutputPolicy.Mode int getDolbyVisionOutputPolicy() {
    return dolbyVisionOutputPolicy;
  }

  @RestrictTo(LIBRARY_GROUP)
  public boolean isDolbyVisionOutputPolicySet() {
    return dolbyVisionOutputPolicySet;
  }

  @RestrictTo(LIBRARY_GROUP)
  public boolean isVideoSharpnessSupported() {
    return videoSharpnessSupported;
  }

  @Nullable
  @RestrictTo(LIBRARY_GROUP)
  public String getRequestedPassthroughCodecs() {
    return requestedPassthroughCodecs;
  }

  @RestrictTo(LIBRARY_GROUP)
  public void applyPreInit(MpvOptions.StringOptionWriter strings) {
    applyStrings(preInitStringOptions, strings);
  }

  @RestrictTo(LIBRARY_GROUP)
  public void applyAppOwned(MpvOptions.StringOptionWriter strings) {
    applyStrings(appOwnedStringOptions, strings);
  }

  @RestrictTo(LIBRARY_GROUP)
  public void applySubtitle(
      MpvOptions.StringOptionWriter strings, MpvOptions.DoubleOptionWriter doubles) {
    applyStrings(subtitleStringOptions, strings);
    applyDoubles(subtitleDoubleOptions, doubles);
  }

  @RestrictTo(LIBRARY_GROUP)
  public ImmutableList<String> getSubtitleStringOptionNames() {
    return getStringNames(subtitleStringOptions);
  }

  @RestrictTo(LIBRARY_GROUP)
  public ImmutableList<String> getSubtitleDoubleOptionNames() {
    return getDoubleNames(subtitleDoubleOptions);
  }

  @RestrictTo(LIBRARY_GROUP)
  public void applyNetworkOptions(
      Uri uri, @Nullable String mimeType, MpvOptions.StringOptionWriter strings) {
    MpvNetworkOptions.apply(uri, mimeType, strings);
  }

  public static final class Builder {

    private final List<StringOption> preInitStringOptions = new ArrayList<>();
    private final List<StringOption> appOwnedStringOptions = new ArrayList<>();
    private final List<StringOption> subtitleStringOptions = new ArrayList<>();
    private final List<DoubleOption> subtitleDoubleOptions = new ArrayList<>();
    @Nullable private String defaultUserAgent;
    @Nullable private String defaultHardwareDecode;
    @Nullable private String requestedPassthroughCodecs;
    private @DolbyVisionOutputPolicy.Mode int dolbyVisionOutputPolicy =
        DolbyVisionOutputPolicy.AUTO;
    private boolean dolbyVisionOutputPolicySet;
    private boolean videoSharpnessSupported = true;
    private boolean buildCalled;

    private static void addString(List<StringOption> options, String name, String value) {
      if (checkNotNull(name).isEmpty()) {
        return;
      }
      options.add(new StringOption(name, checkNotNull(value)));
    }

    private static void addDouble(List<DoubleOption> options, String name, double value) {
      if (checkNotNull(name).isEmpty()) {
        return;
      }
      options.add(new DoubleOption(name, value));
    }

    private static void addUniqueString(List<StringOption> options, String name, String value) {
      for (StringOption option : options) {
        checkArgument(!option.name.equals(name), "Duplicate application-owned option: %s", name);
      }
      addString(options, name, value);
    }

    @CanIgnoreReturnValue
    public Builder setDefaultUserAgent(@Nullable String defaultUserAgent) {
      checkState(!buildCalled);
      this.defaultUserAgent = defaultUserAgent;
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder setDefaultHardwareDecode(@Nullable String defaultHardwareDecode) {
      checkState(!buildCalled);
      this.defaultHardwareDecode = defaultHardwareDecode;
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder setRequestedPassthroughCodecs(@Nullable String requestedPassthroughCodecs) {
      checkState(!buildCalled);
      this.requestedPassthroughCodecs = requestedPassthroughCodecs;
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder setDolbyVisionOutputPolicy(
        @DolbyVisionOutputPolicy.Mode int dolbyVisionOutputPolicy) {
      checkState(!buildCalled);
      this.dolbyVisionOutputPolicy = dolbyVisionOutputPolicy;
      dolbyVisionOutputPolicySet = true;
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder setVideoSharpnessSupported(boolean videoSharpnessSupported) {
      checkState(!buildCalled);
      this.videoSharpnessSupported = videoSharpnessSupported;
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder addPreInitStringOption(String name, String value) {
      checkState(!buildCalled);
      addString(preInitStringOptions, name, value);
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder addAppOwnedStringOption(String name, String value) {
      checkState(!buildCalled);
      addUniqueString(appOwnedStringOptions, name, value);
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder addSubtitleStringOption(String name, String value) {
      checkState(!buildCalled);
      addString(subtitleStringOptions, name, value);
      return this;
    }

    @CanIgnoreReturnValue
    @RestrictTo(LIBRARY_GROUP)
    public Builder addSubtitleDoubleOption(String name, double value) {
      checkState(!buildCalled);
      addDouble(subtitleDoubleOptions, name, value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAndroidDefaults(MpvAndroidOptions options) {
      checkState(!buildCalled);
      return MpvPlayerOptionDefaults.addAndroidDefaults(this, checkNotNull(options));
    }

    @CanIgnoreReturnValue
    public Builder addConfigDirectory(File configDirectory) {
      checkState(!buildCalled);
      return MpvPlayerOptionDefaults.addConfigOptions(this, checkNotNull(configDirectory));
    }

    @CanIgnoreReturnValue
    public Builder addAndroidFontConfig(File configDirectory, File cacheDirectory) {
      checkState(!buildCalled);
      return MpvPlayerOptionDefaults.addAndroidFontConfig(
          this, checkNotNull(configDirectory), checkNotNull(cacheDirectory));
    }

    @CanIgnoreReturnValue
    public Builder addTlsCaFileFromAsset(Context context, String assetName, File outputFile) {
      checkState(!buildCalled);
      return MpvPlayerOptionDefaults.addTlsCaFileFromAsset(
          this, checkNotNull(context), checkNotNull(assetName), checkNotNull(outputFile));
    }

    @CanIgnoreReturnValue
    public Builder addDiskCacheOptions(File cacheDirectory, int cacheSeconds) {
      checkState(!buildCalled);
      return MpvPlayerOptionDefaults.addDiskCacheOptions(
          this, checkNotNull(cacheDirectory), cacheSeconds);
    }

    @CanIgnoreReturnValue
    public Builder addAndroidSubtitleOptions(Context context, MpvSubtitleOptions options) {
      checkState(!buildCalled);
      return MpvPlayerOptionDefaults.addSubtitleOptions(
          this, checkNotNull(context), checkNotNull(options));
    }

    public MpvPlayerConfig build() {
      checkState(!buildCalled);
      buildCalled = true;
      return new MpvPlayerConfig(this);
    }
  }

  private static final class StringOption {

    private final String name;
    private final String value;

    private StringOption(String name, String value) {
      this.name = name;
      this.value = value;
    }
  }

  private static final class DoubleOption {

    private final String name;
    private final double value;

    private DoubleOption(String name, double value) {
      this.name = name;
      this.value = value;
    }
  }
}
