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
package androidx.media3.mpvplayer.nativebridge;

import static is.xyz.mpv.MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_SUCCESS;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;

public final class MpvPlaybackErrorFactory {

  private final MpvClient client;

  public MpvPlaybackErrorFactory(MpvClient client) {
    this.client = client;
  }

  public static boolean isEndFileError(int reason, int error) {
    return reason == MPV_END_FILE_REASON_ERROR || error < MPV_ERROR_SUCCESS;
  }

  private static String messageWithMpvDetail(String defaultMessage, @Nullable String detail) {
    return TextUtils.isEmpty(detail) ? defaultMessage : defaultMessage + ": " + detail;
  }

  public PlaybackException create(String message, @Nullable Throwable cause, int errorCode) {
    return new PlaybackException(message, cause, errorCode);
  }

  public PlaybackException createInitializationFailure(Throwable cause) {
    return create(
        "mpv initialization failed", cause, PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
  }

  public PlaybackException createCreateFailure() {
    return create(
        messageWithMpvDetail("mpv create failed", client.getLastMessage()),
        null,
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
  }

  public PlaybackException createInitFailure() {
    return create(
        messageWithMpvDetail("mpv init failed", client.getLastMessage()),
        null,
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
  }

  public PlaybackException createLoadFailure(@Nullable Uri uri) {
    return create(
        messageWithMpvDetail("mpv failed to load media", client.getLastProblemMessage()),
        client.getLastThrowable(),
        MpvErrorMapper.getErrorCode(uri, client.getLastError()));
  }

  public PlaybackException createDestroyFailure() {
    return create(
        messageWithMpvDetail("mpv destroy failed", client.getLastProblemMessage()),
        client.getLastThrowable(),
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
  }

  public PlaybackException createUnexpectedShutdown() {
    return create(
        "mpv shut down unexpectedly", null, PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
  }

  public PlaybackException createCommandFailure(String command) {
    return create(
        messageWithMpvDetail("mpv " + command + " failed", client.getLastProblemMessage()),
        client.getLastThrowable(),
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
  }

  public PlaybackException createEndFileFailure(
      @Nullable Uri uri, boolean loadFailed, int error, @Nullable String errorString) {
    String defaultMessage = loadFailed ? "mpv failed to load media" : "mpv playback failed";
    return create(
        messageWithMpvDetail(defaultMessage, getEndFileProblemMessage(error, errorString)),
        client.getLastThrowable(),
        MpvErrorMapper.getErrorCode(uri, error));
  }

  @Nullable
  private String getEndFileProblemMessage(int error, @Nullable String errorString) {
    String problemMessage = client.getLastProblemMessage();
    if (error >= MPV_ERROR_SUCCESS || TextUtils.isEmpty(errorString)) {
      return problemMessage;
    }
    if (TextUtils.isEmpty(problemMessage) || errorString.equals(problemMessage)) {
      return errorString;
    }
    return errorString + ": " + problemMessage;
  }
}
