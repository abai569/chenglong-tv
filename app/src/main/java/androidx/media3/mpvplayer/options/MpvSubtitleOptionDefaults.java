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
package androidx.media3.mpvplayer.options;

import static androidx.media3.mpvplayer.MpvSubtitleOptions.SECONDARY_TRACK_AUTO;
import static androidx.media3.mpvplayer.MpvSubtitleOptions.SECONDARY_TRACK_OFF;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_AUTO;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_NO;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.VALUE_YES;

import android.content.Context;
import android.graphics.Color;
import android.view.accessibility.CaptioningManager;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.mpvplayer.MpvSubtitleOptions;

final class MpvSubtitleOptionDefaults {

  private static final String OPT_EMBEDDED_FONTS = "embeddedfonts";
  private static final String OPT_SECONDARY_SUBTITLE_ASS_OVERRIDE = "secondary-sub-ass-override";
  private static final String OPT_SECONDARY_SUBTITLE_POSITION = "secondary-sub-pos";
  private static final String OPT_SECONDARY_SUBTITLE_TRACK = "secondary-sid";
  private static final String OPT_SUBTITLE_ASS_OVERRIDE = "sub-ass-override";
  private static final String OPT_SUBTITLE_BACKGROUND_COLOR = "sub-back-color";
  private static final String OPT_SUBTITLE_BORDER_STYLE = "sub-border-style";
  private static final String OPT_SUBTITLE_COLOR = "sub-color";
  private static final String OPT_SUBTITLE_OUTLINE_COLOR = "sub-outline-color";
  private static final String OPT_SUBTITLE_OUTLINE_SIZE = "sub-outline-size";
  private static final String OPT_SUBTITLE_SHADOW_OFFSET = "sub-shadow-offset";
  private static final String SUB_ASS_OVERRIDE_FORCE = "force";
  private static final String SUB_ASS_OVERRIDE_SCALE = "scale";
  private static final String SUB_BORDER_STYLE_BACKGROUND_BOX = "background-box";
  private static final String SUB_BORDER_STYLE_OUTLINE_AND_SHADOW = "outline-and-shadow";
  private static final String PROP_SUB_POS = "sub-pos";
  private static final String PROP_SUB_SCALE = "sub-scale";
  private static final String PROP_SUB_SCALE_SIGNS = "sub-scale-signs";
  private static final double DEFAULT_SUB_OUTLINE_SIZE = 1.65;
  private static final double DEFAULT_SUB_SHADOW_OFFSET = 0.0;
  private static final double DISABLED_SUB_OUTLINE_SIZE = 0.0;
  private static final double DROP_SHADOW_OFFSET = 2.0;

  static MpvPlayerConfig.Builder addSubtitleOptions(
      MpvPlayerConfig.Builder builder, Context context, MpvSubtitleOptions options) {
    if (options.usesSystemCaptionStyle()) {
      addSubtitleString(builder, OPT_EMBEDDED_FONTS, VALUE_NO);
      addSubtitleString(builder, OPT_SUBTITLE_ASS_OVERRIDE, SUB_ASS_OVERRIDE_FORCE);
      addSystemStyleOptions(builder, context);
    } else if (options.hasCustomStyle()) {
      addCustomStyleOptions(builder, options);
    }
    if (options.isPositionSet()) {
      addSubtitleDouble(builder, PROP_SUB_POS, options.getPosition());
    }
    if (options.isScaleSet()) {
      addSubtitleDouble(builder, PROP_SUB_SCALE, options.getScale());
      addSubtitleString(builder, PROP_SUB_SCALE_SIGNS, VALUE_YES);
    }
    if (options.isSecondarySubtitleConfigured()) {
      addSecondarySubtitleOptions(builder, options);
    }
    return builder;
  }

  private static void addCustomStyleOptions(
      MpvPlayerConfig.Builder builder, MpvSubtitleOptions options) {
    addSubtitleString(builder, OPT_EMBEDDED_FONTS, VALUE_NO);
    addSubtitleString(builder, OPT_SUBTITLE_ASS_OVERRIDE, SUB_ASS_OVERRIDE_FORCE);
    addSubtitleString(builder, OPT_SUBTITLE_COLOR, formatColor(options.getForegroundColor()));
    int background = options.getBackgroundColor();
    boolean useEdgeColorAsBackground =
        options.getEdgeType() == CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW
            && Color.alpha(background) == 0;
    addBackgroundOptions(
        builder,
        useEdgeColorAsBackground ? options.getEdgeColor() : background,
        useEdgeColorAsBackground);
    addCustomEdgeOptions(builder, options);
  }

  private static void addSystemStyleOptions(MpvPlayerConfig.Builder builder, Context context) {
    CaptioningManager.CaptionStyle style = getUserCaptionStyle(context);
    int foreground =
        style != null && style.hasForegroundColor() ? style.foregroundColor : Color.WHITE;
    int background =
        style != null && style.hasBackgroundColor() ? style.backgroundColor : Color.BLACK;
    int edgeType =
        style != null && style.hasEdgeType()
            ? style.edgeType
            : CaptioningManager.CaptionStyle.EDGE_TYPE_NONE;
    int edgeColor = style != null && style.hasEdgeColor() ? style.edgeColor : Color.WHITE;
    addSubtitleString(builder, OPT_SUBTITLE_COLOR, formatColor(foreground));
    boolean useEdgeColorAsBackground =
        edgeType == CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW;
    addBackgroundOptions(
        builder, useEdgeColorAsBackground ? edgeColor : background, useEdgeColorAsBackground);
    addEdgeOptions(builder, edgeType, edgeColor);
  }

  private static void addBackgroundOptions(
      MpvPlayerConfig.Builder builder, int background, boolean useOutlineAndShadow) {
    addSubtitleString(builder, OPT_SUBTITLE_BACKGROUND_COLOR, formatColor(background));
    addSubtitleString(
        builder,
        OPT_SUBTITLE_BORDER_STYLE,
        useOutlineAndShadow || Color.alpha(background) == 0
            ? SUB_BORDER_STYLE_OUTLINE_AND_SHADOW
            : SUB_BORDER_STYLE_BACKGROUND_BOX);
  }

  private static void addEdgeOptions(MpvPlayerConfig.Builder builder, int edgeType, int edgeColor) {
    addSubtitleString(builder, OPT_SUBTITLE_OUTLINE_COLOR, formatColor(edgeColor));
    if (edgeType == CaptioningManager.CaptionStyle.EDGE_TYPE_NONE) {
      addSubtitleDouble(builder, OPT_SUBTITLE_OUTLINE_SIZE, DISABLED_SUB_OUTLINE_SIZE);
      addSubtitleDouble(builder, OPT_SUBTITLE_SHADOW_OFFSET, DEFAULT_SUB_SHADOW_OFFSET);
    } else if (edgeType == CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW) {
      addSubtitleDouble(builder, OPT_SUBTITLE_OUTLINE_SIZE, DISABLED_SUB_OUTLINE_SIZE);
      addSubtitleDouble(builder, OPT_SUBTITLE_SHADOW_OFFSET, DROP_SHADOW_OFFSET);
    } else {
      addSubtitleDouble(builder, OPT_SUBTITLE_OUTLINE_SIZE, DEFAULT_SUB_OUTLINE_SIZE);
      addSubtitleDouble(builder, OPT_SUBTITLE_SHADOW_OFFSET, DEFAULT_SUB_SHADOW_OFFSET);
    }
  }

  private static void addCustomEdgeOptions(
      MpvPlayerConfig.Builder builder, MpvSubtitleOptions options) {
    int edgeType = options.getEdgeType();
    int edgeColor = options.getEdgeColor();
    addSubtitleString(builder, OPT_SUBTITLE_OUTLINE_COLOR, formatColor(edgeColor));
    if (edgeType == CaptioningManager.CaptionStyle.EDGE_TYPE_NONE) {
      addSubtitleDouble(builder, OPT_SUBTITLE_OUTLINE_SIZE, DISABLED_SUB_OUTLINE_SIZE);
      addSubtitleDouble(builder, OPT_SUBTITLE_SHADOW_OFFSET, DEFAULT_SUB_SHADOW_OFFSET);
    } else {
      addSubtitleDouble(
          builder,
          OPT_SUBTITLE_OUTLINE_SIZE,
          edgeType == CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE
              ? options.getEdgeSize()
              : DISABLED_SUB_OUTLINE_SIZE);
      addSubtitleDouble(
          builder,
          OPT_SUBTITLE_SHADOW_OFFSET,
          edgeType == CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW
              ? options.getShadowOffset()
              : DEFAULT_SUB_SHADOW_OFFSET);
    }
  }

  private static void addSecondarySubtitleOptions(
      MpvPlayerConfig.Builder builder, MpvSubtitleOptions options) {
    addSubtitleString(
        builder,
        OPT_SECONDARY_SUBTITLE_ASS_OVERRIDE,
        options.shouldOverrideSecondaryAssStyles()
            ? SUB_ASS_OVERRIDE_FORCE
            : SUB_ASS_OVERRIDE_SCALE);
    int track = options.getSecondaryTrack();
    addSubtitleString(builder, OPT_SECONDARY_SUBTITLE_TRACK, getSecondarySubtitleTrackValue(track));
    if (track != SECONDARY_TRACK_OFF) {
      addSubtitleDouble(builder, OPT_SECONDARY_SUBTITLE_POSITION, options.getSecondaryPosition());
    }
  }

  private static String getSecondarySubtitleTrackValue(int track) {
    if (track == SECONDARY_TRACK_OFF) {
      return VALUE_NO;
    }
    if (track == SECONDARY_TRACK_AUTO) {
      return VALUE_AUTO;
    }
    return String.valueOf(track);
  }

  private static @Nullable CaptioningManager.CaptionStyle getUserCaptionStyle(Context context) {
    CaptioningManager manager =
        (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
    return manager == null ? null : manager.getUserStyle();
  }

  private static void addSubtitleString(
      MpvPlayerConfig.Builder builder, String name, String value) {
    builder.addSubtitleStringOption(name, value);
  }

  private static void addSubtitleDouble(
      MpvPlayerConfig.Builder builder, String name, double value) {
    builder.addSubtitleDoubleOption(name, value);
  }

  private static String formatColor(int color) {
    return Util.formatInvariant(
        "#%02X%02X%02X%02X",
        Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color));
  }
}
