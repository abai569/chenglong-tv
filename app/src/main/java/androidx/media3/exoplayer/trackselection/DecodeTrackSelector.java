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
package androidx.media3.exoplayer.trackselection;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.C.FormatSupport;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.RendererCapabilities;
import java.util.Objects;

/**
 * A {@link DefaultTrackSelector} that can switch renderer preference between platform and extension
 * decoders without rebuilding the player.
 */
@UnstableApi
public class DecodeTrackSelector extends DefaultTrackSelector {

  private static final String MEDIA_CODEC_AUDIO_RENDERER_NAME = "MediaCodecAudioRenderer";
  private static final String MEDIA_CODEC_VIDEO_RENDERER_NAME = "MediaCodecVideoRenderer";
  private static final String FFMPEG_AUDIO_RENDERER_NAME = "FfmpegAudioRenderer";
  private static final String FFMPEG_VIDEO_RENDERER_NAME = "FfmpegVideoRenderer";
  private static final RendererDecodePreferences DEFAULT_RENDERER_DECODE_PREFERENCES =
      new RendererDecodePreferences(C.DECODE_HARDWARE, C.DECODE_HARDWARE);

  private volatile RendererDecodePreferences rendererDecodePreferences =
      DEFAULT_RENDERER_DECODE_PREFERENCES;

  /**
   * @param context Any {@link Context}.
   */
  public DecodeTrackSelector(Context context) {
    super(context);
  }

  /**
   * @param context Any {@link Context}.
   * @param trackSelectionFactory A factory for {@link ExoTrackSelection}s.
   */
  public DecodeTrackSelector(Context context, ExoTrackSelection.Factory trackSelectionFactory) {
    super(context, trackSelectionFactory);
  }

  /**
   * @param context Any {@link Context}.
   * @param parameters Initial {@link TrackSelectionParameters}.
   */
  public DecodeTrackSelector(Context context, TrackSelectionParameters parameters) {
    super(context, parameters);
  }

  /**
   * @deprecated Use {@link #DecodeTrackSelector(Context, TrackSelectionParameters,
   *     ExoTrackSelection.Factory)}
   */
  @Deprecated
  public DecodeTrackSelector(
      TrackSelectionParameters parameters, ExoTrackSelection.Factory trackSelectionFactory) {
    super(parameters, trackSelectionFactory);
  }

  /**
   * @param context Any {@link Context}.
   * @param parameters Initial {@link TrackSelectionParameters}.
   * @param trackSelectionFactory A factory for {@link ExoTrackSelection}s.
   */
  public DecodeTrackSelector(
      Context context,
      TrackSelectionParameters parameters,
      ExoTrackSelection.Factory trackSelectionFactory) {
    super(context, parameters, trackSelectionFactory);
  }

  /**
   * Sets decode preferences used when mapping audio and video track groups to renderers.
   *
   * <p>In hardware mode, FFmpeg remains available as a fallback while platform renderers are
   * preferred. In software mode, only the corresponding FFmpeg renderer is allowed.
   */
  public final void setRendererDecodePreferences(
      @C.DecodeMode int audioDecode, @C.DecodeMode int videoDecode) {
    RendererDecodePreferences preferences = new RendererDecodePreferences(audioDecode, videoDecode);
    if (!preferences.equals(rendererDecodePreferences)) {
      rendererDecodePreferences = preferences;
      invalidate(/* parameters= */ null);
    }
  }

  @Override
  protected boolean isRendererAllowed(RendererCapabilities rendererCapability, TrackGroup group) {
    RendererDecodePreferences decodePreferences = rendererDecodePreferences;
    if (group.type == C.TRACK_TYPE_AUDIO) {
      return isRendererAllowed(rendererCapability, group.type, decodePreferences.audioDecode);
    } else if (group.type == C.TRACK_TYPE_VIDEO) {
      return isRendererAllowed(rendererCapability, group.type, decodePreferences.videoDecode);
    }
    return true;
  }

  @Override
  protected boolean isPreferredRenderer(
      RendererCapabilities rendererCapability,
      TrackGroup group,
      @FormatSupport int formatSupportLevel) {
    if (group.type != C.TRACK_TYPE_AUDIO && group.type != C.TRACK_TYPE_VIDEO) {
      return false;
    }
    if (rendererCapability.getTrackType() != group.type) {
      return false;
    }
    RendererDecodePreferences decodePreferences = rendererDecodePreferences;
    @C.DecodeMode
    int decode =
        group.type == C.TRACK_TYPE_AUDIO
            ? decodePreferences.audioDecode
            : decodePreferences.videoDecode;
    if (decode == C.DECODE_HARDWARE) {
      return isMediaCodecRenderer(rendererCapability, group.type)
          && formatSupportLevel == C.FORMAT_HANDLED;
    } else if (decode == C.DECODE_SOFTWARE) {
      return isFfmpegRenderer(rendererCapability, group.type)
          && formatSupportLevel >= C.FORMAT_EXCEEDS_CAPABILITIES;
    }
    return false;
  }

  private static boolean isRendererAllowed(
      RendererCapabilities rendererCapability,
      @C.TrackType int trackType,
      @C.DecodeMode int decode) {
    return decode != C.DECODE_SOFTWARE || isFfmpegRenderer(rendererCapability, trackType);
  }

  private static boolean isMediaCodecRenderer(
      RendererCapabilities rendererCapability, @C.TrackType int trackType) {
    String name = rendererCapability.getName();
    if (trackType == C.TRACK_TYPE_AUDIO) {
      return MEDIA_CODEC_AUDIO_RENDERER_NAME.equals(name);
    } else if (trackType == C.TRACK_TYPE_VIDEO) {
      return MEDIA_CODEC_VIDEO_RENDERER_NAME.equals(name);
    }
    return false;
  }

  private static boolean isFfmpegRenderer(
      RendererCapabilities rendererCapability, @C.TrackType int trackType) {
    String name = rendererCapability.getName();
    if (trackType == C.TRACK_TYPE_AUDIO) {
      return FFMPEG_AUDIO_RENDERER_NAME.equals(name);
    } else if (trackType == C.TRACK_TYPE_VIDEO) {
      return FFMPEG_VIDEO_RENDERER_NAME.equals(name);
    }
    return false;
  }

  private static final class RendererDecodePreferences {

    private final @C.DecodeMode int audioDecode;
    private final @C.DecodeMode int videoDecode;

    private RendererDecodePreferences(
        @C.DecodeMode int audioDecode, @C.DecodeMode int videoDecode) {
      this.audioDecode = audioDecode;
      this.videoDecode = videoDecode;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof RendererDecodePreferences)) {
        return false;
      }
      RendererDecodePreferences other = (RendererDecodePreferences) obj;
      return audioDecode == other.audioDecode && videoDecode == other.videoDecode;
    }

    @Override
    public int hashCode() {
      return Objects.hash(audioDecode, videoDecode);
    }
  }
}
