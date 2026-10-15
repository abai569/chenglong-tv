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

import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_GENERIC;
import static is.xyz.mpv.MPVLib.MpvError.MPV_ERROR_SUCCESS;

import android.content.Context;
import android.text.TextUtils;
import android.view.Surface;
import androidx.annotation.Nullable;
import is.xyz.mpv.MPVLib;
import java.util.HashMap;
import java.util.Map;

public final class MpvClient implements MPVLib.LogObserver, MpvNativeClient {

  private static final int MPV_LOG_LEVEL_WARN = 30;
  private static final String COMMAND_SET_PROPERTY = "set";
  private static final NativeCommandQueue DEFAULT_COMMAND_QUEUE = MPVLib::enqueueCommand;

  private final LastResult lastResult = new LastResult();
  private final Map<Long, PendingRequest> pendingRequests = new HashMap<>();
  private final NativeCommandQueue commandQueue;
  private long nextRequestId = 1;

  public MpvClient() {
    this(DEFAULT_COMMAND_QUEUE);
  }

  MpvClient(NativeCommandQueue commandQueue) {
    this.commandQueue = commandQueue;
  }

  public boolean create(Context context) {
    return run("create", () -> MPVLib.create(context));
  }

  public boolean init() {
    return run("init", MPVLib::init);
  }

  public boolean destroy() {
    return runResult("destroy", MPVLib::destroy);
  }

  public boolean attachSurface(Surface surface) {
    return run("attachSurface", () -> MPVLib.attachSurface(surface));
  }

  public boolean replaceSurface(Surface surface) {
    return run("replaceSurface", () -> MPVLib.replaceSurface(surface));
  }

  public boolean detachSurface() {
    return run("detachSurface", MPVLib::detachSurface);
  }

  public boolean attachOsdSurface(Surface surface) {
    return run("attachOsdSurface", () -> MPVLib.attachOsdSurface(surface));
  }

  public boolean replaceOsdSurface(Surface surface) {
    return run("replaceOsdSurface", () -> MPVLib.replaceOsdSurface(surface));
  }

  public boolean detachOsdSurface() {
    return run("detachOsdSurface", MPVLib::detachOsdSurface);
  }

  public boolean command(String[] command) {
    String action = command.length == 0 ? "command" : "command:" + command[0];
    return submitCommand(action, command, /* callback= */ null);
  }

  public void setOptionString(String name, String value) {
    runResult("setOptionString:" + name, () -> MPVLib.setOptionString(name, value));
  }

  public @Nullable Integer getPropertyInt(String property) {
    return callNullable(() -> MPVLib.getPropertyInt(property));
  }

  public @Nullable Boolean getPropertyBoolean(String property) {
    return callNullable(() -> MPVLib.getPropertyBoolean(property));
  }

  public @Nullable String getPropertyString(String property) {
    return callNullable(() -> MPVLib.getPropertyString(property));
  }

  public @Nullable byte[] getPropertyByteArray(String property) {
    return callNullable(() -> MPVLib.getPropertyByteArray(property));
  }

  public @Nullable Double getPropertyDouble(String property) {
    return callNullable(() -> MPVLib.getPropertyDouble(property));
  }

  public void setPropertyInt(String property, int value) {
    setProperty(property, Integer.toString(value), /* callback= */ null);
  }

  public boolean setPropertyDouble(
      String property, double value, @Nullable ResultCallback callback) {
    return setProperty(property, Double.toString(value), callback);
  }

  public void setPropertyDouble(String property, double value) {
    setPropertyDouble(property, value, /* callback= */ null);
  }

  public void setPropertyString(String property, String value) {
    setProperty(property, value, /* callback= */ null);
  }

  public boolean observeProperty(String property, int format) {
    return runResult("observeProperty:" + property, () -> MPVLib.observeProperty(property, format));
  }

  public void addObserver(MPVLib.EventObserver observer) {
    MPVLib.addObserver(observer);
  }

  public void removeObserver(MPVLib.EventObserver observer) {
    MPVLib.removeObserver(observer);
  }

  public void addLogObserver() {
    MPVLib.addLogObserver(this);
  }

  public void removeLogObserver() {
    MPVLib.removeLogObserver(this);
  }

  public @Nullable Throwable getLastThrowable() {
    return lastResult.throwable();
  }

  public void clearLastResult() {
    lastResult.clear();
  }

  public void onCommandReply(long requestId, int error) {
    PendingRequest request = pendingRequests.remove(requestId);
    if (request == null) {
      return;
    }
    boolean success = error >= MPV_ERROR_SUCCESS;
    if (!success) {
      fail(request.action, error);
    } else {
      lastResult.recordResult(error);
    }
    if (request.callback != null) {
      request.callback.onResult(success);
    }
  }

  @Override
  public void clearPendingRequests() {
    pendingRequests.clear();
  }

  public int getLastError() {
    return lastResult.error();
  }

  public @Nullable String getLastMessage() {
    return lastResult.message();
  }

  public @Nullable String getLastProblemMessage() {
    return lastResult.problemMessage();
  }

  public boolean hasLastFailure() {
    return lastResult.hasFailure();
  }

  @Override
  public void logMessage(String prefix, int level, String text) {
    if (TextUtils.isEmpty(text)) {
      return;
    }
    String message = text.trim();
    if (TextUtils.isEmpty(message)) {
      return;
    }
    String log = (TextUtils.isEmpty(prefix) ? "" : prefix + ": ") + message;
    lastResult.recordLog(log, level <= MPV_LOG_LEVEL_WARN);
  }

  private boolean run(String action, Runnable runnable) {
    try {
      runnable.run();
      lastResult.recordSuccess();
      return true;
    } catch (RuntimeException e) {
      fail(action, e);
      return false;
    }
  }

  private boolean runResult(String action, IntCall call) {
    try {
      int result = call.run();
      lastResult.recordResult(result);
      if (result >= 0) {
        return true;
      }
      fail(action, result);
      return false;
    } catch (RuntimeException e) {
      fail(action, e);
      return false;
    }
  }

  private boolean setProperty(
      String property, String value, @Nullable ResultCallback callback) {
    return submitCommand(
        "setProperty:" + property,
        new String[] {COMMAND_SET_PROPERTY, property, value},
        callback);
  }

  private boolean submitCommand(
      String action, String[] command, @Nullable ResultCallback callback) {
    long requestId = allocateRequestId();
    pendingRequests.put(requestId, new PendingRequest(action, callback));
    try {
      int result = commandQueue.enqueue(requestId, command);
      if (result >= MPV_ERROR_SUCCESS) {
        return true;
      }
      pendingRequests.remove(requestId);
      fail(action, result);
    } catch (RuntimeException e) {
      pendingRequests.remove(requestId);
      fail(action, e);
    }
    return false;
  }

  private long allocateRequestId() {
    long requestId = nextRequestId;
    nextRequestId = requestId == Long.MAX_VALUE ? 1 : requestId + 1;
    return requestId;
  }

  private <T> @Nullable T callNullable(NullableCall<T> call) {
    try {
      return call.run();
    } catch (RuntimeException e) {
      lastResult.recordThrowable(e);
      return null;
    }
  }

  private void fail(String action, Throwable e) {
    lastResult.recordFailure(action, e);
  }

  private void fail(String action, int result) {
    lastResult.recordFailure(action, result);
  }

  private interface IntCall {

    int run();
  }

  interface NativeCommandQueue {

    int enqueue(long requestId, String[] command);
  }

  public interface ResultCallback {

    void onResult(boolean success);
  }

  private static final class PendingRequest {

    private final String action;
    private final @Nullable ResultCallback callback;

    private PendingRequest(String action, @Nullable ResultCallback callback) {
      this.action = action;
      this.callback = callback;
    }
  }

  private interface NullableCall<T> {

    @Nullable
    T run();
  }

  private static final class LastResult {

    private volatile @Nullable Throwable throwable;
    private volatile @Nullable String log;
    private volatile @Nullable String problemLog;
    private volatile int error = MPV_ERROR_SUCCESS;
    private volatile boolean failure;

    @Nullable
    Throwable throwable() {
      return throwable;
    }

    int error() {
      return error;
    }

    void clear() {
      throwable = null;
      log = null;
      problemLog = null;
      error = MPV_ERROR_SUCCESS;
      failure = false;
    }

    @Nullable
    String message() {
      String value = problemLog;
      if (!TextUtils.isEmpty(value)) {
        return value;
      }
      value = log;
      if (!TextUtils.isEmpty(value)) {
        return value;
      }
      return throwableMessage();
    }

    @Nullable
    String problemMessage() {
      String value = problemLog;
      if (!TextUtils.isEmpty(value)) {
        return value;
      }
      return throwableMessage();
    }

    boolean hasFailure() {
      return failure;
    }

    void recordSuccess() {
      error = MPV_ERROR_SUCCESS;
    }

    void recordResult(int result) {
      error = result;
    }

    void recordThrowable(Throwable throwable) {
      this.throwable = throwable;
      error = MPV_ERROR_GENERIC;
      failure = true;
    }

    void recordLog(String log, boolean problem) {
      this.log = log;
      if (problem) {
        problemLog = log;
      }
    }

    void recordFailure(String action, Throwable throwable) {
      this.throwable = throwable;
      error = MPV_ERROR_GENERIC;
      failure = true;
      recordProblem(action + " failed");
    }

    void recordFailure(String action, int result) {
      throwable = null;
      error = result;
      failure = true;
      recordProblem(action + " failed (" + result + ")");
    }

    private void recordProblem(String message) {
      problemLog = message;
      log = message;
    }

    private @Nullable String throwableMessage() {
      Throwable currentThrowable = throwable;
      if (currentThrowable == null) {
        return null;
      }
      String message = currentThrowable.getMessage();
      return TextUtils.isEmpty(message) ? currentThrowable.getClass().getSimpleName() : message;
    }
  }
}
