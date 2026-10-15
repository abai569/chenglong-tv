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
package androidx.media3.mpvplayer.video;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import java.util.Objects;

public final class MpvVideoEqualizer {

  private static final double MIN_EQUALIZER_VALUE = -100.0;
  private static final double MAX_EQUALIZER_VALUE = 100.0;
  private static final double MIN_SHARPNESS_VALUE = 0.0;
  private static final double MAX_SHARPNESS_VALUE = 1.0;
  public static final MpvVideoEqualizer DEFAULT = create(0, 0, 0, 0, 0, 0);

  private final double brightness;
  private final double contrast;
  private final double saturation;
  private final double gamma;
  private final double hue;
  private final double sharpness;

  public static MpvVideoEqualizer create(
      double brightness,
      double contrast,
      double saturation,
      double gamma,
      double hue,
      double sharpness) {
    return new MpvVideoEqualizer(brightness, contrast, saturation, gamma, hue, sharpness);
  }

  private MpvVideoEqualizer(
      double brightness,
      double contrast,
      double saturation,
      double gamma,
      double hue,
      double sharpness) {
    this.brightness = clampEqualizerValue(brightness);
    this.contrast = clampEqualizerValue(contrast);
    this.saturation = clampEqualizerValue(saturation);
    this.gamma = clampEqualizerValue(gamma);
    this.hue = clampEqualizerValue(hue);
    this.sharpness = clampSharpnessValue(sharpness);
  }

  public double getBrightness() {
    return brightness;
  }

  public double getContrast() {
    return contrast;
  }

  public double getSaturation() {
    return saturation;
  }

  public double getGamma() {
    return gamma;
  }

  public double getHue() {
    return hue;
  }

  public double getSharpness() {
    return sharpness;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof MpvVideoEqualizer)) {
      return false;
    }
    MpvVideoEqualizer other = (MpvVideoEqualizer) object;
    return Double.compare(brightness, other.brightness) == 0
        && Double.compare(contrast, other.contrast) == 0
        && Double.compare(saturation, other.saturation) == 0
        && Double.compare(gamma, other.gamma) == 0
        && Double.compare(hue, other.hue) == 0
        && Double.compare(sharpness, other.sharpness) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(brightness, contrast, saturation, gamma, hue, sharpness);
  }

  private static double clampEqualizerValue(double value) {
    return Util.constrainValue(value, MIN_EQUALIZER_VALUE, MAX_EQUALIZER_VALUE);
  }

  private static double clampSharpnessValue(double value) {
    return Util.constrainValue(value, MIN_SHARPNESS_VALUE, MAX_SHARPNESS_VALUE);
  }
}
