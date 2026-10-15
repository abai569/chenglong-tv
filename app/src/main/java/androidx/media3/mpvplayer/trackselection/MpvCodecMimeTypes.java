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
package androidx.media3.mpvplayer.trackselection;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import com.google.common.base.Ascii;

final class MpvCodecMimeTypes {

  private static final String PROFILE_DDP_ATMOS = "Dolby Digital Plus + Dolby Atmos";
  private static final String PROFILE_TRUEHD_ATMOS = "Dolby TrueHD + Dolby Atmos";

  static @Nullable String getSampleMimeType(
      int type, @Nullable String codec, @Nullable String codecProfile) {
    String mediaMimeType = MimeTypes.getMediaMimeType(codec);
    if (mediaMimeType != null
        && (type != C.TRACK_TYPE_VIDEO || !MimeTypes.isImage(mediaMimeType))) {
      return getProfileAdjustedMimeType(mediaMimeType, codecProfile);
    }
    String value = normalizeCodecName(codec);
    if (value.startsWith("pcm_")) {
      return getPcmMimeType(value);
    }
    String codecMimeType = getCodecMimeType(value);
    String sampleMimeType = codecMimeType != null ? codecMimeType : getUnknownMimeType(type);
    return getProfileAdjustedMimeType(sampleMimeType, codecProfile);
  }

  static @Nullable String getCodecs(
      @Nullable String codec, @Nullable String sampleMimeType, @Nullable String codecProfile) {
    return MimeTypes.AUDIO_TRUEHD.equals(sampleMimeType)
            && PROFILE_TRUEHD_ATMOS.equals(codecProfile)
        ? MimeTypes.CODEC_TRUEHD_ATMOS
        : codec;
  }

  static @Nullable Integer getPcmEncoding(@Nullable String codec) {
    String value = normalizeCodecName(codec);
    if (value.startsWith("pcm_u8")) {
      return C.ENCODING_PCM_8BIT;
    }
    if (value.startsWith("pcm_s16be")) {
      return C.ENCODING_PCM_16BIT_BIG_ENDIAN;
    }
    if (value.startsWith("pcm_s16")) {
      return C.ENCODING_PCM_16BIT;
    }
    if (value.startsWith("pcm_s24be")) {
      return C.ENCODING_PCM_24BIT_BIG_ENDIAN;
    }
    if (value.startsWith("pcm_s24")) {
      return C.ENCODING_PCM_24BIT;
    }
    if (value.startsWith("pcm_s32be")) {
      return C.ENCODING_PCM_32BIT_BIG_ENDIAN;
    }
    if (value.startsWith("pcm_s32")) {
      return C.ENCODING_PCM_32BIT;
    }
    if (value.startsWith("pcm_f32")) {
      return C.ENCODING_PCM_FLOAT;
    }
    if (value.startsWith("pcm_f64")) {
      return C.ENCODING_PCM_DOUBLE;
    }
    return null;
  }

  private static @Nullable String getCodecMimeType(String codec) {
    String mimeType = getVideoMimeType(codec);
    if (mimeType != null) {
      return mimeType;
    }
    mimeType = getAudioMimeType(codec);
    if (mimeType != null) {
      return mimeType;
    }
    return getTextMimeType(codec);
  }

  private static @Nullable String getVideoMimeType(String codec) {
    switch (codec) {
      case "h264":
      case "avc":
      case "avc1":
        return MimeTypes.VIDEO_H264;
      case "hevc":
      case "h265":
      case "hev1":
      case "hvc1":
        return MimeTypes.VIDEO_H265;
      case "h266":
      case "vvc":
      case "vvc1":
      case "vvi1":
        return MimeTypes.VIDEO_H266;
      case "av1":
        return MimeTypes.VIDEO_AV1;
      case "vp8":
        return MimeTypes.VIDEO_VP8;
      case "vp9":
        return MimeTypes.VIDEO_VP9;
      case "mpeg1video":
      case "mpegvideo":
      case "mpeg1":
        return MimeTypes.VIDEO_MPEG;
      case "mpeg2video":
      case "mpeg2":
        return MimeTypes.VIDEO_MPEG2;
      case "mpeg4":
      case "mpeg4video":
      case "mp4v":
        return MimeTypes.VIDEO_MP4V;
      case "h263":
      case "h263p":
        return MimeTypes.VIDEO_H263;
      case "vc1":
        return MimeTypes.VIDEO_VC1;
      case "wmv1":
        return MimeTypes.VIDEO_WMV1;
      case "wmv2":
        return MimeTypes.VIDEO_WMV2;
      case "wmv3":
        return MimeTypes.VIDEO_WMV;
      case "mjpeg":
      case "mjpegb":
        return MimeTypes.VIDEO_MJPEG;
      case "flv1":
        return MimeTypes.VIDEO_FLV;
      case "rv10":
        return MimeTypes.VIDEO_RV10;
      case "rv20":
        return MimeTypes.VIDEO_RV20;
      case "rv30":
        return MimeTypes.VIDEO_RV30;
      case "rv40":
        return MimeTypes.VIDEO_RV40;
      default:
        return null;
    }
  }

  private static @Nullable String getAudioMimeType(String codec) {
    switch (codec) {
      case "aac":
      case "aac_latm":
        return MimeTypes.AUDIO_AAC;
      case "mp3":
      case "mp3float":
        return MimeTypes.AUDIO_MPEG;
      case "mp1":
        return MimeTypes.AUDIO_MPEG_L1;
      case "mp2":
        return MimeTypes.AUDIO_MPEG_L2;
      case "ac3":
        return MimeTypes.AUDIO_AC3;
      case "eac3":
      case "eac3_core":
        return MimeTypes.AUDIO_E_AC3;
      case "eac3_atmos":
      case "eac3_joc":
        return MimeTypes.AUDIO_E_AC3_JOC;
      case "ac4":
        return MimeTypes.AUDIO_AC4;
      case "dts":
      case "dca":
        return MimeTypes.AUDIO_DTS;
      case "dts_hd":
      case "dtshd":
        return MimeTypes.AUDIO_DTS_HD;
      case "dts_hd_ma":
      case "dtsma":
        return MimeTypes.AUDIO_DTS_HD_MA;
      case "dts_express":
        return MimeTypes.AUDIO_DTS_EXPRESS;
      case "dtsx":
        return MimeTypes.AUDIO_DTS_UHD_P2;
      case "flac":
        return MimeTypes.AUDIO_FLAC;
      case "opus":
        return MimeTypes.AUDIO_OPUS;
      case "vorbis":
        return MimeTypes.AUDIO_VORBIS;
      case "alac":
        return MimeTypes.AUDIO_ALAC;
      case "amr_nb":
        return MimeTypes.AUDIO_AMR_NB;
      case "amr_wb":
        return MimeTypes.AUDIO_AMR_WB;
      case "wma":
        return MimeTypes.AUDIO_WMA;
      case "wmav1":
        return MimeTypes.AUDIO_WMA1;
      case "wmav2":
        return MimeTypes.AUDIO_WMA2;
      case "wmapro":
        return MimeTypes.AUDIO_WMA_PRO;
      case "wmavoice":
        return MimeTypes.AUDIO_WMA_VOICE;
      case "wmalossless":
        return MimeTypes.AUDIO_WMA_LOSSLESS;
      case "dsd":
        return MimeTypes.AUDIO_DSD;
      default:
        return null;
    }
  }

  private static @Nullable String getTextMimeType(String codec) {
    switch (codec) {
      case "ass":
      case "ssa":
        return MimeTypes.TEXT_SSA;
      case "webvtt":
      case "vtt":
        return MimeTypes.TEXT_VTT;
      case "subrip":
      case "srt":
        return MimeTypes.APPLICATION_SUBRIP;
      case "hdmv_pgs_subtitle":
      case "pgssub":
        return MimeTypes.APPLICATION_PGS;
      case "dvd_subtitle":
      case "dvdsub":
        return MimeTypes.APPLICATION_VOBSUB;
      case "dvb_subtitle":
      case "dvbsub":
        return MimeTypes.APPLICATION_DVBSUBS;
      case "mov_text":
      case "tx3g":
        return MimeTypes.APPLICATION_TX3G;
      case "ttml":
        return MimeTypes.APPLICATION_TTML;
      case "eia_608":
      case "cea_608":
        return MimeTypes.APPLICATION_CEA608;
      case "eia_708":
      case "cea_708":
        return MimeTypes.APPLICATION_CEA708;
      default:
        return null;
    }
  }

  private static String getPcmMimeType(String codec) {
    switch (codec) {
      case "pcm_alaw":
        return MimeTypes.AUDIO_ALAW;
      case "pcm_mulaw":
        return MimeTypes.AUDIO_MLAW;
      default:
        return MimeTypes.AUDIO_RAW;
    }
  }

  private static @Nullable String getUnknownMimeType(int type) {
    switch (type) {
      case C.TRACK_TYPE_VIDEO:
        return MimeTypes.VIDEO_UNKNOWN;
      case C.TRACK_TYPE_AUDIO:
        return MimeTypes.AUDIO_UNKNOWN;
      case C.TRACK_TYPE_TEXT:
        return MimeTypes.TEXT_UNKNOWN;
      default:
        return null;
    }
  }

  private static String normalizeCodecName(@Nullable String codec) {
    if (codec == null) {
      return "";
    }
    String value = codec.trim();
    return value.isEmpty() ? "" : Ascii.toLowerCase(value).replace('-', '_');
  }

  private static @Nullable String getProfileAdjustedMimeType(
      @Nullable String sampleMimeType, @Nullable String codecProfile) {
    return MimeTypes.AUDIO_E_AC3.equals(sampleMimeType) && PROFILE_DDP_ATMOS.equals(codecProfile)
        ? MimeTypes.AUDIO_E_AC3_JOC
        : sampleMimeType;
  }
}
