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

/** Shared channel mixing policy for PCM processing and mpv audio filter matrices. */
public final class AudioChannelMix {

  private static final int MAX_MIX_CHANNEL_COUNT = 8;
  private static final float POWER_GAIN = 0.7071f;
  private static final float LOW_FREQUENCY_GAIN = 0.5f;

  private static final float[][] STEREO_LEFT = {
    {POWER_GAIN},
    {1.0f, 0.0f},
    {1.0f, 0.0f, POWER_GAIN},
    {1.0f, 0.0f, POWER_GAIN, 0.0f},
    {1.0f, 0.0f, POWER_GAIN, POWER_GAIN, 0.0f},
    {1.0f, 0.0f, POWER_GAIN, LOW_FREQUENCY_GAIN, POWER_GAIN, 0.0f},
    {1.0f, 0.0f, POWER_GAIN, LOW_FREQUENCY_GAIN, POWER_GAIN, 0.0f, POWER_GAIN},
    {1.0f, 0.0f, POWER_GAIN, LOW_FREQUENCY_GAIN, POWER_GAIN, 0.0f, POWER_GAIN, 0.0f}
  };

  private static final float[][] STEREO_RIGHT = {
    {POWER_GAIN},
    {0.0f, 1.0f},
    {0.0f, 1.0f, POWER_GAIN},
    {0.0f, 1.0f, 0.0f, POWER_GAIN},
    {0.0f, 1.0f, POWER_GAIN, 0.0f, POWER_GAIN},
    {0.0f, 1.0f, POWER_GAIN, LOW_FREQUENCY_GAIN, 0.0f, POWER_GAIN},
    {0.0f, 1.0f, POWER_GAIN, LOW_FREQUENCY_GAIN, 0.0f, POWER_GAIN, POWER_GAIN},
    {0.0f, 1.0f, POWER_GAIN, LOW_FREQUENCY_GAIN, 0.0f, POWER_GAIN, 0.0f, POWER_GAIN}
  };

  private static final float[][] MONO = {
    {1.0f},
    {POWER_GAIN, POWER_GAIN},
    {POWER_GAIN, POWER_GAIN, 1.0f},
    {POWER_GAIN, POWER_GAIN, LOW_FREQUENCY_GAIN, LOW_FREQUENCY_GAIN},
    {POWER_GAIN, POWER_GAIN, 1.0f, LOW_FREQUENCY_GAIN, LOW_FREQUENCY_GAIN},
    {POWER_GAIN, POWER_GAIN, 1.0f, POWER_GAIN, LOW_FREQUENCY_GAIN, LOW_FREQUENCY_GAIN},
    {
      POWER_GAIN,
      POWER_GAIN,
      1.0f,
      POWER_GAIN,
      LOW_FREQUENCY_GAIN,
      LOW_FREQUENCY_GAIN,
      LOW_FREQUENCY_GAIN
    },
    {
      POWER_GAIN,
      POWER_GAIN,
      1.0f,
      POWER_GAIN,
      LOW_FREQUENCY_GAIN,
      LOW_FREQUENCY_GAIN,
      LOW_FREQUENCY_GAIN,
      LOW_FREQUENCY_GAIN
    }
  };

  public static float mixStereoLeft(float[] samples) {
    float[] checkedSamples = checkSamples(samples);
    return mix(checkedSamples, forCount(STEREO_LEFT, checkedSamples.length));
  }

  public static float mixStereoRight(float[] samples) {
    float[] checkedSamples = checkSamples(samples);
    return mix(checkedSamples, forCount(STEREO_RIGHT, checkedSamples.length));
  }

  public static float mixMono(float[] samples) {
    float[] checkedSamples = checkSamples(samples);
    return mix(checkedSamples, forCount(MONO, checkedSamples.length));
  }

  public static float[][] createStereoMix(int channelCount, boolean reverse) {
    checkMixChannelCount(channelCount);
    float[][] mix = new float[channelCount][channelCount];
    float[] left = forCount(STEREO_LEFT, channelCount);
    float[] right = forCount(STEREO_RIGHT, channelCount);
    setGains(mix[0], reverse ? right : left);
    setGains(mix[1], reverse ? left : right);
    return mix;
  }

  public static float[][] createMonoMix(int channelCount) {
    checkMixChannelCount(channelCount);
    float[] gains = forCount(MONO, channelCount);
    float[][] mix = new float[channelCount][channelCount];
    setGains(mix[0], gains);
    setGains(mix[1], gains);
    return mix;
  }

  public static float[][] createFrontCenterGainMix(int channelCount, float gain) {
    checkArgument(channelCount == 6 || channelCount == 8);
    checkArgument(Float.isFinite(gain) && gain >= 0.0f);
    float[][] mix = createIdentityMix(channelCount);
    mix[2][2] = gain;
    return mix;
  }

  public static float[][] createFrontBalanceMix(int channelCount, float balance) {
    checkMixChannelCount(channelCount);
    checkArgument(Float.isFinite(balance) && balance >= -1.0f && balance <= 1.0f);
    float[][] mix = createIdentityMix(channelCount);
    mix[0][0] = balance > 0.0f ? 1.0f - balance : 1.0f;
    mix[1][1] = balance < 0.0f ? 1.0f + balance : 1.0f;
    return mix;
  }

  /** Returns the channel mix produced by applying {@code first} and then {@code second}. */
  public static float[][] compose(float[][] first, float[][] second) {
    float[][] checkedFirst = checkSquareMix(first);
    float[][] checkedSecond = checkSquareMix(second);
    int channelCount = checkedFirst.length;
    checkArgument(checkedSecond.length == channelCount);
    float[][] result = new float[channelCount][channelCount];
    for (int output = 0; output < channelCount; output++) {
      for (int intermediate = 0; intermediate < channelCount; intermediate++) {
        float gain = checkedSecond[output][intermediate];
        if (gain == 0.0f) {
          continue;
        }
        for (int input = 0; input < channelCount; input++) {
          result[output][input] += gain * checkedFirst[intermediate][input];
        }
      }
    }
    return result;
  }

  private static float[] checkSamples(float[] samples) {
    float[] checkedSamples = checkNotNull(samples);
    checkArgument(checkedSamples.length > 0 && checkedSamples.length <= MAX_MIX_CHANNEL_COUNT);
    return checkedSamples;
  }

  private static float[][] checkSquareMix(float[][] mix) {
    float[][] checkedMix = checkNotNull(mix);
    checkArgument(checkedMix.length > 0 && checkedMix.length <= MAX_MIX_CHANNEL_COUNT);
    for (float[] gains : checkedMix) {
      checkArgument(checkNotNull(gains).length == checkedMix.length);
    }
    return checkedMix;
  }

  private static float mix(float[] samples, float[] gains) {
    float value = 0.0f;
    for (int channel = 0; channel < samples.length; channel++) {
      value += gains[channel] * samples[channel];
    }
    return value;
  }

  private static float[] forCount(float[][] mixes, int channelCount) {
    return mixes[channelCount - 1];
  }

  private static void checkMixChannelCount(int channelCount) {
    checkArgument(channelCount >= 2 && channelCount <= MAX_MIX_CHANNEL_COUNT);
  }

  private static float[][] createIdentityMix(int channelCount) {
    float[][] mix = new float[channelCount][channelCount];
    for (int channel = 0; channel < channelCount; channel++) {
      mix[channel][channel] = 1.0f;
    }
    return mix;
  }

  private static void setGains(float[] output, float[] gains) {
    System.arraycopy(gains, 0, output, 0, gains.length);
  }
}
