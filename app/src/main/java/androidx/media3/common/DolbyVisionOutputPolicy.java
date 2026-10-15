/*
 * Copyright 2026 The Android Open Source Project
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
package androidx.media3.common;

import static android.os.Build.VERSION.SDK_INT;
import static android.view.Display.DEFAULT_DISPLAY;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Policy for deciding whether native Dolby Vision output may be sent to a display. */
@UnstableApi
public final class DolbyVisionOutputPolicy {

  /** Uses the Dolby Vision capability reported by Android for the selected display. */
  public static final int AUTO = 0;

  /** Assumes that the selected display supports native Dolby Vision output. */
  public static final int ASSUME_SUPPORTED = 1;

  /** Assumes that the selected display does not support native Dolby Vision output. */
  public static final int ASSUME_UNSUPPORTED = 2;

  /** A Dolby Vision output policy mode. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({AUTO, ASSUME_SUPPORTED, ASSUME_UNSUPPORTED})
  public @interface Mode {}

  private DolbyVisionOutputPolicy() {}

  /**
   * Returns whether native Dolby Vision output is allowed on the default display by {@code mode}.
   */
  public static boolean isNativeOutputAllowed(Context context, @Mode int mode) {
    if (mode != AUTO) {
      return mode == ASSUME_SUPPORTED;
    }
    if (SDK_INT < 24) {
      return false;
    }
    Context applicationContext = context.getApplicationContext();
    Context displayContext = applicationContext != null ? applicationContext : context;
    DisplayManager displayManager =
        (DisplayManager) displayContext.getSystemService(Context.DISPLAY_SERVICE);
    Display display = displayManager == null ? null : displayManager.getDisplay(DEFAULT_DISPLAY);
    return isNativeOutputAllowedOnDisplay(display, mode);
  }

  /** Returns whether native Dolby Vision output is allowed on {@code display} by {@code mode}. */
  public static boolean isNativeOutputAllowedOnDisplay(@Nullable Display display, @Mode int mode) {
    if (mode == ASSUME_SUPPORTED) {
      return true;
    }
    if (mode == ASSUME_UNSUPPORTED || SDK_INT < 24 || display == null) {
      return false;
    }
    return Api24.doesDisplaySupportDolbyVision(display);
  }

  @RequiresApi(24)
  private static final class Api24 {

    public static boolean doesDisplaySupportDolbyVision(Display display) {
      if (SDK_INT >= 34) {
        return containsDolbyVision(Api34.getSupportedHdrTypes(display));
      }
      return doesDisplaySupportDolbyVisionBeforeApi34(display);
    }

    @SuppressWarnings("deprecation") // Required because the replacement was added in API 34.
    private static boolean doesDisplaySupportDolbyVisionBeforeApi34(Display display) {
      Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();
      return hdrCapabilities != null && containsDolbyVision(hdrCapabilities.getSupportedHdrTypes());
    }

    private static boolean containsDolbyVision(int[] hdrTypes) {
      for (int hdrType : hdrTypes) {
        if (hdrType == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
          return true;
        }
      }
      return false;
    }
  }

  @RequiresApi(34)
  private static final class Api34 {

    public static int[] getSupportedHdrTypes(Display display) {
      return display.getMode().getSupportedHdrTypes();
    }
  }
}
