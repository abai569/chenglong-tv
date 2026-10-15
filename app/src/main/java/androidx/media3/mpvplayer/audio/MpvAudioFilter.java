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
package androidx.media3.mpvplayer.audio;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MpvAudioFilter {

  public static final String LABEL = "media3_audio_effect";
  public static final MpvAudioFilter EMPTY = create(null, null, ImmutableList.of());

  private final String filter;
  private final String graphKey;
  private final ImmutableList<MpvAudioFilterCommand> commands;

  public static MpvAudioFilter create(
      @Nullable String filter, @Nullable String graphKey, List<MpvAudioFilterCommand> commands) {
    return new MpvAudioFilter(normalize(filter), normalize(graphKey), commands);
  }

  private MpvAudioFilter(String filter, String graphKey, List<MpvAudioFilterCommand> commands) {
    this.filter = checkNotNull(filter);
    this.graphKey = checkNotNull(graphKey);
    this.commands = ImmutableList.copyOf(checkNotNull(commands));
  }

  public String getFilter() {
    return filter;
  }

  public String getLabeledFilter() {
    return filter.isEmpty() ? "" : "@" + LABEL + ":" + filter;
  }

  public String getGraphKey() {
    return graphKey;
  }

  public ImmutableList<MpvAudioFilterCommand> getCommands() {
    return commands;
  }

  private static String normalize(@Nullable String value) {
    return value == null ? "" : value;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof MpvAudioFilter)) {
      return false;
    }
    MpvAudioFilter other = (MpvAudioFilter) object;
    return filter.equals(other.filter)
        && graphKey.equals(other.graphKey)
        && commands.equals(other.commands);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filter, graphKey, commands);
  }

  /** Builder for {@link MpvAudioFilter}. */
  public static final class Builder {

    private static final int STEREO_MODE_NORMAL = 0;
    private static final int STEREO_MODE_REVERSE = 6;

    private final StringBuilder filters = new StringBuilder();
    private final StringBuilder graphKey = new StringBuilder();
    private final List<MpvAudioFilterCommand> commands = new ArrayList<>();
    private boolean buildCalled;
    private boolean fixPts;

    @CanIgnoreReturnValue
    public Builder addChannelMix(float[][] channelMix) {
      checkState(!buildCalled);
      float[][] checkedChannelMix = checkNotNull(channelMix);
      String outputLayout = outputChannelLayout(checkedChannelMix.length);
      String definition = channelMixDefinition(outputLayout, checkedChannelMix);
      return addFilter("pan=" + definition, "pan=" + definition);
    }

    @CanIgnoreReturnValue
    public Builder addRuntimeChannelMix(String id, float[][] channelMix) {
      checkState(!buildCalled);
      float[][] checkedChannelMix = checkNotNull(channelMix);
      String outputLayout = outputChannelLayout(checkedChannelMix.length);
      String target = target("pan", id);
      String definition = channelMixDefinition(outputLayout, checkedChannelMix);
      commands.add(command("remix", definition, target));
      return addFilter(
          target + "=" + definition,
          channelMixGraphKey(target, outputLayout, checkedChannelMix.length));
    }

    @CanIgnoreReturnValue
    public Builder addEqualizer(String id, int frequencyHz, double gainDb) {
      checkState(!buildCalled);
      checkArgument(frequencyHz > 0);
      String target = target("equalizer", id);
      String gain = decimal(gainDb, 1);
      commands.add(command("g", gain, target));
      return addFilter(
          Util.formatInvariant("%s=f=%d:t=q:w=1:g=%s", target, frequencyHz, gain),
          Util.formatInvariant("%s=f=%d:t=q:w=1:g=0.0", target, frequencyHz));
    }

    @CanIgnoreReturnValue
    public Builder addVolume(String id, double gainDb) {
      checkState(!buildCalled);
      String target = target("volume", id);
      String gain = decimal(gainDb, 1) + "dB";
      commands.add(command("volume", gain, target));
      return addFilter(
          Util.formatInvariant("%s=volume=%s", target, gain),
          Util.formatInvariant("%s=volume=0.0dB", target));
    }

    @CanIgnoreReturnValue
    public Builder addCompressor(
        String id, double threshold, double ratio, double makeup, double mix) {
      checkState(!buildCalled);
      String target = target("acompressor", id);
      String thresholdValue = decimal(threshold, 3);
      String ratioValue = decimal(ratio, 2);
      String makeupValue = decimal(makeup, 2);
      String mixValue = decimal(mix, 2);
      commands.add(command("threshold", thresholdValue, target));
      commands.add(command("ratio", ratioValue, target));
      commands.add(command("makeup", makeupValue, target));
      commands.add(command("mix", mixValue, target));
      return addFilter(
          compressor(target, thresholdValue, ratioValue, makeupValue, mixValue),
          compressor(target, "0.250", "1.40", "1.00", "0.00"));
    }

    @CanIgnoreReturnValue
    public Builder addLoudnessNormalization(
        String id, double integratedLoudness, double loudnessRange, double truePeak) {
      checkState(!buildCalled);
      String target = target("loudnorm", id);
      String filter =
          Util.formatInvariant(
              "%s=I=%s:LRA=%s:TP=%s",
              target,
              decimal(integratedLoudness, 1),
              decimal(loudnessRange, 1),
              decimal(truePeak, 1));
      fixPts = true;
      return addFilter(filter, filter);
    }

    @CanIgnoreReturnValue
    public Builder addStereoTools(String id, boolean reverse, double balance) {
      checkState(!buildCalled);
      String target = target("stereotools", id);
      String mode = String.valueOf(reverse ? STEREO_MODE_REVERSE : STEREO_MODE_NORMAL);
      String balanceValue = decimal(balance, 3);
      commands.add(command("mode", mode, target));
      commands.add(command("balance_out", balanceValue, target));
      return addFilter(
          Util.formatInvariant("%s=mode=%s:balance_out=%s", target, mode, balanceValue),
          Util.formatInvariant("%s=mode=%d:balance_out=0.000", target, STEREO_MODE_NORMAL));
    }

    @CanIgnoreReturnValue
    public Builder addLimiter(String id, double limit) {
      checkState(!buildCalled);
      String target = target("alimiter", id);
      String limitValue = decimal(limit, 2);
      commands.add(command("limit", limitValue, target));
      return addFilter(
          Util.formatInvariant("%s=limit=%s:attack=5:release=50:level=0", target, limitValue),
          Util.formatInvariant("%s=limit=1.00:attack=5:release=50:level=0", target));
    }

    public MpvAudioFilter build() {
      checkState(!buildCalled);
      buildCalled = true;
      return filters.length() == 0
          ? EMPTY
          : create(
              "lavfi=[" + filters + "]" + (fixPts ? ":fix-pts=yes" : ""),
              graphKey.toString(),
              commands);
    }

    private static String channelMixDefinition(String outputLayout, float[][] channelMix) {
      int inputChannelCount = checkNotNull(channelMix[0]).length;
      checkArgument(inputChannelCount > 0);
      StringBuilder definition = new StringBuilder(outputLayout);
      for (int outputChannel = 0; outputChannel < channelMix.length; outputChannel++) {
        float[] gains = checkNotNull(channelMix[outputChannel]);
        checkArgument(gains.length == inputChannelCount);
        definition.append("|c").append(outputChannel).append('=').append(channelExpression(gains));
      }
      return definition.toString();
    }

    private static String outputChannelLayout(int channelCount) {
      checkArgument(channelCount > 0);
      return channelCount == 1 ? "mono" : channelCount == 2 ? "stereo" : channelCount + "c";
    }

    private static String channelMixGraphKey(
        String target, String outputLayout, int outputChannelCount) {
      StringBuilder filter = new StringBuilder(target).append('=').append(outputLayout);
      for (int outputChannel = 0; outputChannel < outputChannelCount; outputChannel++) {
        filter.append("|c").append(outputChannel).append("=0*c0");
      }
      return filter.toString();
    }

    private static String channelExpression(float[] gains) {
      StringBuilder expression = new StringBuilder();
      for (int channel = 0; channel < gains.length; channel++) {
        float gain = gains[channel];
        if (gain == 0.0f) {
          continue;
        }
        if (expression.length() > 0) {
          expression.append('+');
        }
        expression.append(gain == 1.0f ? "c" + channel : decimal(gain, 4) + "*c" + channel);
      }
      return expression.length() == 0 ? "0*c0" : expression.toString();
    }

    private static String compressor(
        String target, String threshold, String ratio, String makeup, String mix) {
      return Util.formatInvariant(
          "%s=threshold=%s:ratio=%s:attack=20:release=250:makeup=%s:knee=4:detection=rms:mix=%s",
          target, threshold, ratio, makeup, mix);
    }

    private static String decimal(double value, int digits) {
      checkArgument(Double.isFinite(value));
      return Util.formatInvariant("%." + digits + "f", value);
    }

    private static String target(String filter, String id) {
      checkArgument(!checkNotNull(id).isEmpty());
      return filter + "@" + id;
    }

    private MpvAudioFilterCommand command(String command, String argument, String target) {
      return new MpvAudioFilterCommand(LABEL, command, argument, target);
    }

    private Builder addFilter(String filter, String neutralFilter) {
      if (filters.length() > 0) {
        filters.append(',');
        graphKey.append(',');
      }
      filters.append(filter);
      graphKey.append(neutralFilter);
      return this;
    }
  }
}
