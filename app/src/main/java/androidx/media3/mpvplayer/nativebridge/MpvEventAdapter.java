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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CHAPTER;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CHAPTER_LIST;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CURRENT_EDITION;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_CURRENT_VIDEO_ALBUMART;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_DEMUXER_CACHE_DURATION;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_DEMUXER_CACHE_TIME;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_DURATION_FULL;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_EDITION_LIST;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_MEDIA_LIVE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_PAUSE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_PAUSED_FOR_CACHE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_SEEKABLE;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_TIME_POS;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_TRACK_LIST;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_VIDEO_ASPECT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_VIDEO_HEIGHT;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_VIDEO_ROTATION;
import static androidx.media3.mpvplayer.nativebridge.MpvConstants.PROP_VIDEO_WIDTH;
import static androidx.media3.mpvplayer.nativebridge.MpvObservedProperty.booleanProperty;
import static androidx.media3.mpvplayer.nativebridge.MpvObservedProperty.doubleProperty;
import static androidx.media3.mpvplayer.nativebridge.MpvObservedProperty.invalidatedProperty;
import static androidx.media3.mpvplayer.nativebridge.MpvObservedProperty.invalidatingBooleanProperty;
import static androidx.media3.mpvplayer.nativebridge.MpvObservedProperty.invalidatingDoubleProperty;
import static androidx.media3.mpvplayer.nativebridge.MpvObservedProperty.invalidatingLongProperty;
import static androidx.media3.mpvplayer.nativebridge.MpvObservedProperty.longProperty;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.Tracks;
import com.google.common.collect.ImmutableList;
import is.xyz.mpv.MPVLib;

public final class MpvEventAdapter implements MPVLib.EventObserver {

  private static final MpvObservedProperty[] OBSERVED_PROPERTIES = {
    doubleProperty(PROP_TIME_POS, PropertyEventHost::onPositionProperty),
    doubleProperty(
        PROP_DURATION_FULL,
        unused -> false,
        (host, durationSeconds) -> {
          host.onDurationProperty(durationSeconds);
          return true;
        }),
    booleanProperty(
        PROP_MEDIA_LIVE,
        PropertyEventHost::onLivePropertyInvalidated,
        PropertyEventHost::onLiveProperty),
    booleanProperty(
        PROP_SEEKABLE,
        PropertyEventHost::onSeekablePropertyInvalidated,
        PropertyEventHost::onSeekableProperty),
    invalidatingDoubleProperty(
        PROP_DEMUXER_CACHE_TIME,
        PropertyEventHost::onCacheTimeInvalidated,
        PropertyEventHost::onCacheTimeProperty),
    invalidatingDoubleProperty(
        PROP_DEMUXER_CACHE_DURATION,
        PropertyEventHost::onCacheDurationInvalidated,
        PropertyEventHost::onCacheDurationProperty),
    invalidatingBooleanProperty(PROP_PAUSE, PropertyEventHost::onPauseProperty),
    booleanProperty(PROP_PAUSED_FOR_CACHE, PropertyEventHost::onPausedForCacheProperty),
    longProperty(
        PROP_CHAPTER,
        PropertyEventHost::onChapterPropertyInvalidated,
        PropertyEventHost::onChapterProperty),
    longProperty(
        PROP_CURRENT_EDITION,
        PropertyEventHost::onEditionPropertyInvalidated,
        PropertyEventHost::onEditionProperty),
    invalidatingLongProperty(
        PROP_VIDEO_WIDTH,
        host -> host.onVideoWidthProperty(0),
        PropertyEventHost::onVideoWidthProperty),
    invalidatingLongProperty(
        PROP_VIDEO_HEIGHT,
        host -> host.onVideoHeightProperty(0),
        PropertyEventHost::onVideoHeightProperty),
    invalidatingDoubleProperty(
        PROP_VIDEO_ASPECT,
        host -> host.onVideoAspectProperty(0),
        PropertyEventHost::onVideoAspectProperty),
    invalidatingLongProperty(
        PROP_VIDEO_ROTATION,
        host -> host.onVideoRotationProperty(0),
        PropertyEventHost::onVideoRotationProperty),
    invalidatingBooleanProperty(
        PROP_CURRENT_VIDEO_ALBUMART,
        host -> host.onAlbumArtProperty(false),
        PropertyEventHost::onAlbumArtProperty),
    invalidatedProperty(PROP_CHAPTER_LIST),
    invalidatedProperty(PROP_EDITION_LIST),
    invalidatedProperty(PROP_TRACK_LIST)
  };

  private final PlayerEventHost playerEventHost;
  private final PropertyEventHost propertyEventHost;
  private final CommandReplyListener commandReplyListener;
  private final Runnable audioOutputChangedListener;
  private final MpvLoadGeneration loadGeneration;
  private final MpvNativePlayerEvent[] playerEvents;
  private boolean trackSnapshotsEnabled;

  public MpvEventAdapter(
      PlayerEventHost playerEventHost,
      PropertyEventHost propertyEventHost,
      CommandReplyListener commandReplyListener,
      Runnable audioOutputChangedListener,
      MpvLoadGeneration loadGeneration) {
    this.playerEventHost = playerEventHost;
    this.propertyEventHost = propertyEventHost;
    this.commandReplyListener = commandReplyListener;
    this.audioOutputChangedListener = audioOutputChangedListener;
    this.loadGeneration = loadGeneration;
    this.playerEvents =
        new MpvNativePlayerEvent[] {
          new MpvNativePlayerEvent(
              MPVLib.MpvEvent.MPV_EVENT_START_FILE, playerEventHost::onStartFile),
          new MpvNativePlayerEvent(MPVLib.MpvEvent.MPV_EVENT_SEEK, playerEventHost::onSeek),
          new MpvNativePlayerEvent(MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG, this::onAudioReconfig)
        };
  }

  @Nullable
  private static MpvObservedProperty findProperty(String name) {
    for (MpvObservedProperty property : OBSERVED_PROPERTIES) {
      if (property.name.equals(name)) {
        return property;
      }
    }
    return null;
  }

  @Nullable
  private MpvNativePlayerEvent findEvent(int id) {
    for (MpvNativePlayerEvent event : playerEvents) {
      if (event.id == id) {
        return event;
      }
    }
    return null;
  }

  private void onAudioReconfig() {
    audioOutputChangedListener.run();
  }

  boolean observeProperties(MpvNativeClient client) {
    for (MpvObservedProperty property : OBSERVED_PROPERTIES) {
      if (!property.observe(client)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void eventProperty(String propertyName) {
    if (PROP_CHAPTER_LIST.equals(propertyName)) {
      ImmutableList<MediaChapter> chapters = propertyEventHost.readChapters();
      dispatchSnapshot(() -> propertyEventHost.onChapterListPropertyChanged(chapters));
      return;
    }
    if (PROP_EDITION_LIST.equals(propertyName)) {
      ImmutableList<MediaEdition> editions = propertyEventHost.readEditions();
      dispatchSnapshot(() -> propertyEventHost.onEditionListPropertyChanged(editions));
      return;
    }
    if (PROP_TRACK_LIST.equals(propertyName)) {
      if (!trackSnapshotsEnabled) {
        return;
      }
      Tracks tracks = propertyEventHost.readTracks();
      dispatchSnapshot(() -> propertyEventHost.onTrackPropertyChanged(tracks));
      return;
    }
    dispatchProperty(propertyName, property -> property.onInvalidated(propertyEventHost));
  }

  @Override
  public void eventProperty(String propertyName, long value) {
    dispatchProperty(propertyName, property -> property.onLong(propertyEventHost, value));
  }

  @Override
  public void eventProperty(String propertyName, boolean value) {
    if (PROP_CURRENT_VIDEO_ALBUMART.equals(propertyName)) {
      @Nullable byte[] artworkData = value ? propertyEventHost.readArtworkData() : null;
      playerEventHost.runOnPlayerLooper(
          () -> {
            MpvObservedProperty property = findProperty(propertyName);
            boolean stateChanged =
                property == null || property.onBoolean(propertyEventHost, value);
            if (artworkData != null) {
              propertyEventHost.onArtworkData(artworkData);
              stateChanged = true;
            }
            if (stateChanged) {
              playerEventHost.invalidateState();
            }
          });
      return;
    }
    dispatchProperty(propertyName, property -> property.onBoolean(propertyEventHost, value));
  }

  @Override
  public void eventProperty(String propertyName, String value) {}

  @Override
  public void eventProperty(String propertyName, double value) {
    dispatchProperty(propertyName, property -> property.onDouble(propertyEventHost, value));
  }

  private void dispatchProperty(String propertyName, PropertyDispatcher dispatcher) {
    playerEventHost.runOnPlayerLooper(
        () -> {
          MpvObservedProperty property = findProperty(propertyName);
          if (property == null || dispatcher.dispatch(property)) {
            playerEventHost.invalidateState();
          }
        });
  }

  private void dispatchSnapshot(SnapshotDispatcher dispatcher) {
    playerEventHost.runOnPlayerLooper(
        () -> {
          if (dispatcher.dispatch()) {
            playerEventHost.invalidateState();
          }
        });
  }

  @Override
  public void event(int eventId) {
    if (eventId == MPVLib.MpvEvent.MPV_EVENT_START_FILE) {
      loadGeneration.onStartFile();
      trackSnapshotsEnabled = false;
    }
    if (eventId == MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED) {
      trackSnapshotsEnabled = true;
      Tracks tracks = propertyEventHost.readTracks();
      playerEventHost.runOnPlayerLooper(() -> playerEventHost.onFileLoaded(tracks));
      return;
    }
    if (eventId == MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN) {
      trackSnapshotsEnabled = false;
      playerEventHost.runOnPlayerLooperAfterRelease(playerEventHost::onShutdown);
      return;
    }
    if (eventId == MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART) {
      @Nullable Double positionSeconds = propertyEventHost.readPosition();
      playerEventHost.runOnPlayerLooper(
          () -> playerEventHost.onPlaybackRestart(positionSeconds));
      return;
    }
    playerEventHost.runOnPlayerLooper(
        () -> {
          MpvNativePlayerEvent event = findEvent(eventId);
          if (event != null) {
            event.dispatch();
          }
        });
  }

  @Override
  public void eventCommandReply(long requestId, int error) {
    playerEventHost.runOnPlayerLooper(
        () -> commandReplyListener.onReply(requestId, error));
  }

  @Override
  public void eventEndFile(int reason, int error, @Nullable String errorString) {
    long generation = loadGeneration.captureActive();
    playerEventHost.runOnPlayerLooper(
        () -> {
          if (loadGeneration.isCurrent(generation)) {
            playerEventHost.onEndFile(reason, error, errorString);
          }
        });
  }

  public interface PlayerEventHost {

    void runOnPlayerLooper(Runnable runnable);

    void runOnPlayerLooperAfterRelease(Runnable runnable);

    void invalidateState();

    void onStartFile();

    void onFileLoaded(Tracks tracks);

    void onEndFile(int reason, int error, @Nullable String errorString);

    void onSeek();

    void onPlaybackRestart(@Nullable Double positionSeconds);

    void onShutdown();
  }

  public interface CommandReplyListener {

    void onReply(long requestId, int error);
  }

  public interface PropertyEventHost {

    ImmutableList<MediaChapter> readChapters();

    boolean onChapterListPropertyChanged(ImmutableList<MediaChapter> chapters);

    ImmutableList<MediaEdition> readEditions();

    boolean onEditionListPropertyChanged(ImmutableList<MediaEdition> editions);

    Tracks readTracks();

    boolean onTrackPropertyChanged(Tracks tracks);

    @Nullable
    byte[] readArtworkData();

    void onArtworkData(byte[] artworkData);

    @Nullable
    Double readPosition();

    void onCacheTimeInvalidated();

    void onCacheTimeProperty(double cacheTimeSeconds);

    void onCacheDurationInvalidated();

    void onCacheDurationProperty(double cacheDurationSeconds);

    void onVideoWidthProperty(long width);

    void onVideoHeightProperty(long height);

    void onVideoAspectProperty(double aspect);

    void onVideoRotationProperty(long rotation);

    void onAlbumArtProperty(boolean albumArt);

    void onPauseProperty(boolean paused);

    boolean onPausedForCacheProperty(boolean pausedForCache);

    boolean onChapterPropertyInvalidated();

    boolean onChapterProperty(long chapter);

    boolean onEditionPropertyInvalidated();

    boolean onEditionProperty(long edition);

    boolean onPositionProperty(double positionSeconds);

    void onDurationProperty(double durationSeconds);

    boolean onLivePropertyInvalidated();

    boolean onLiveProperty(boolean live);

    boolean onSeekablePropertyInvalidated();

    boolean onSeekableProperty(boolean seekable);
  }

  private interface PropertyDispatcher {

    boolean dispatch(MpvObservedProperty property);
  }

  private interface SnapshotDispatcher {

    boolean dispatch();
  }
}
