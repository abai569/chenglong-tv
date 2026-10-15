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

import android.content.Context;
import androidx.media3.common.PlaybackException;
import androidx.media3.mpvplayer.MpvLibrary;

public final class MpvNativeLifecycle {

  private final Context context;
  private final Object sessionOwner;
  private final MpvNativeClient client;
  private final MpvNativeState state;
  private final MpvEventAdapter eventAdapter;
  private final MpvPlaybackErrorFactory errorFactory;
  private final Host host;
  private boolean released;
  private boolean shutdownRequested;
  private boolean waitingForSession;

  public MpvNativeLifecycle(
      Context context,
      Object sessionOwner,
      MpvClient client,
      MpvNativeState state,
      MpvEventAdapter eventAdapter,
      MpvPlaybackErrorFactory errorFactory,
      Host host) {
    this(context, sessionOwner, (MpvNativeClient) client, state, eventAdapter, errorFactory, host);
  }

  MpvNativeLifecycle(
      Context context,
      Object sessionOwner,
      MpvNativeClient client,
      MpvNativeState state,
      MpvEventAdapter eventAdapter,
      MpvPlaybackErrorFactory errorFactory,
      Host host) {
    this.context = context;
    this.sessionOwner = sessionOwner;
    this.client = client;
    this.state = state;
    this.eventAdapter = eventAdapter;
    this.errorFactory = errorFactory;
    this.host = host;
  }

  private static RuntimeException toRuntimeException(PlaybackException error) {
    return new IllegalStateException(error.getMessage(), error);
  }

  public boolean isInitialized() {
    return state.isInitialized();
  }

  private boolean isCreated() {
    return state.isCreated();
  }

  public boolean ensureInitialized() {
    if (released || shutdownRequested || waitingForSession) {
      return false;
    }
    if (isInitialized()) {
      return true;
    }
    if (!MpvLibrary.isAvailable()) {
      host.onInitializationFailed(
          errorFactory.create(
              "mpv native library is not available",
              null,
              PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK));
      return false;
    }
    if (isCreated() && !requestNativeShutdown()) {
      host.onInitializationFailed(errorFactory.createDestroyFailure());
      return false;
    }
    MpvNativeSessionRegistry.AcquireResult acquireResult =
        MpvNativeSessionRegistry.acquire(sessionOwner, this::onNativeSessionAvailable);
    if (acquireResult == MpvNativeSessionRegistry.AcquireResult.PENDING) {
      waitingForSession = true;
      return false;
    }
    if (acquireResult == MpvNativeSessionRegistry.AcquireResult.UNAVAILABLE) {
      host.onInitializationFailed(
          errorFactory.create(
              "another mpv native session is active",
              null,
              PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK));
      return false;
    }
    client.clearLastResult();
    try {
      createMpv();
      initializeMpv();
      return true;
    } catch (RuntimeException e) {
      handleInitializationFailure(e);
      return false;
    }
  }

  public void onShutdown() {
    if (!isCreated()) {
      return;
    }
    boolean expectedShutdown = shutdownRequested;
    completeNativeShutdown();
    if (!expectedShutdown) {
      host.onShutdown();
    }
  }

  public void release() {
    released = true;
    cancelPendingInitialization();
    if (!isCreated() || shutdownRequested) {
      return;
    }
    if (!requestNativeShutdown()) {
      host.onNativeReleaseFailed(errorFactory.createDestroyFailure());
    }
  }

  public void cancelPendingInitialization() {
    if (!waitingForSession) {
      return;
    }
    waitingForSession = false;
    MpvNativeSessionRegistry.cancelPendingAcquire(sessionOwner);
  }

  private void createMpv() {
    if (!client.create(context)) {
      throw toRuntimeException(errorFactory.createCreateFailure());
    }
    state.setCreated(true);
    client.addLogObserver();
  }

  private void initializeMpv() {
    client.clearLastResult();
    host.applyPreInitOptions();
    if (client.hasLastFailure()) {
      throw toRuntimeException(errorFactory.createInitFailure());
    }
    client.clearLastResult();
    if (!client.init()) {
      throw toRuntimeException(errorFactory.createInitFailure());
    }
    state.setInitialized(true);
    client.addObserver(eventAdapter);
    client.clearLastResult();
    if (!eventAdapter.observeProperties(client)) {
      throw toRuntimeException(errorFactory.createInitFailure());
    }
    client.clearLastResult();
    host.onInitialized();
    if (client.hasLastFailure()) {
      throw toRuntimeException(errorFactory.createInitFailure());
    }
  }

  private void handleInitializationFailure(RuntimeException error) {
    if (isCreated()) {
      requestNativeShutdown();
    } else {
      MpvNativeSessionRegistry.release(sessionOwner);
    }
    host.onInitializationFailed(errorFactory.createInitializationFailure(error));
    host.releaseAudioFocus();
  }

  private void removeObservers() {
    if (isInitialized()) {
      client.removeObserver(eventAdapter);
    }
    client.removeLogObserver();
  }

  private boolean requestNativeShutdown() {
    shutdownRequested = true;
    MpvNativeSessionRegistry.beginRelease(sessionOwner);
    boolean waitForShutdownEvent = isInitialized();
    if (!client.destroy()) {
      shutdownRequested = false;
      MpvNativeSessionRegistry.abortRelease(sessionOwner);
      return false;
    }
    if (!waitForShutdownEvent) {
      completeNativeShutdown();
    }
    return true;
  }

  private void completeNativeShutdown() {
    shutdownRequested = false;
    client.clearPendingRequests();
    removeObservers();
    state.setCreated(false);
    state.setInitialized(false);
    host.onNativeSessionEnded();
    MpvNativeSessionRegistry.release(sessionOwner);
  }

  private void onNativeSessionAvailable() {
    host.runOnPlayerLooperAfterRelease(
        () -> {
          if (!waitingForSession) {
            return;
          }
          waitingForSession = false;
          if (released) {
            return;
          }
          host.onNativeSessionAvailable();
        });
  }

  public interface Host {

    void applyPreInitOptions();

    void onInitialized();

    void onInitializationFailed(PlaybackException error);

    void releaseAudioFocus();

    void onNativeSessionEnded();

    void onNativeReleaseFailed(PlaybackException error);

    void onShutdown();

    void runOnPlayerLooperAfterRelease(Runnable runnable);

    void onNativeSessionAvailable();
  }
}
