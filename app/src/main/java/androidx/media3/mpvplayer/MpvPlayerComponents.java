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
package androidx.media3.mpvplayer;

import android.content.Context;
import android.os.Handler;
import androidx.media3.mpvplayer.audio.MpvAudioFocusManager;
import androidx.media3.mpvplayer.core.MpvEffectController;
import androidx.media3.mpvplayer.core.MpvPlaybackEventHandler;
import androidx.media3.mpvplayer.core.MpvPlaybackEventState;
import androidx.media3.mpvplayer.core.MpvPlaybackPropertyUpdater;
import androidx.media3.mpvplayer.core.MpvPlaybackState;
import androidx.media3.mpvplayer.core.MpvPlayerInfo;
import androidx.media3.mpvplayer.core.MpvStateBuilder;
import androidx.media3.mpvplayer.media.MpvArtworkLoader;
import androidx.media3.mpvplayer.media.MpvChapterController;
import androidx.media3.mpvplayer.media.MpvEditionController;
import androidx.media3.mpvplayer.media.MpvEndFileGuard;
import androidx.media3.mpvplayer.media.MpvMediaLoader;
import androidx.media3.mpvplayer.media.MpvMediaMetadata;
import androidx.media3.mpvplayer.media.MpvPlaybackNavigator;
import androidx.media3.mpvplayer.media.MpvPlaylist;
import androidx.media3.mpvplayer.media.MpvSubtitleController;
import androidx.media3.mpvplayer.media.MpvTimelineController;
import androidx.media3.mpvplayer.nativebridge.MpvClient;
import androidx.media3.mpvplayer.nativebridge.MpvCommandDispatcher;
import androidx.media3.mpvplayer.nativebridge.MpvEventAdapter;
import androidx.media3.mpvplayer.nativebridge.MpvLoadGeneration;
import androidx.media3.mpvplayer.nativebridge.MpvNativeLifecycle;
import androidx.media3.mpvplayer.nativebridge.MpvNativeState;
import androidx.media3.mpvplayer.nativebridge.MpvPlaybackErrorFactory;
import androidx.media3.mpvplayer.nativebridge.MpvPlaybackProperties;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import androidx.media3.mpvplayer.options.MpvOptions;
import androidx.media3.mpvplayer.seek.MpvSeekController;
import androidx.media3.mpvplayer.trackselection.MpvTrackController;
import androidx.media3.mpvplayer.video.MpvSurfaceController;
import androidx.media3.mpvplayer.video.MpvVideoState;
import androidx.media3.mpvplayer.video.MpvVideoTrackEnableGate;

final class MpvPlayerComponents {

  final MpvPlaybackErrorFactory errorFactory;
  final MpvOptions options;
  final MpvPlaybackProperties playbackProperties;
  final MpvEffectController effectController;
  final MpvCommandDispatcher commandDispatcher;
  final MpvSurfaceController surfaceController;
  final MpvAudioFocusManager audioFocusManager;
  final MpvTrackController trackController;
  final MpvPlaybackState playbackState;
  final MpvPlayerInfo playerInfo;
  final MpvPlaybackEventState playbackEventState;
  final MpvVideoState videoState;
  final MpvVideoTrackEnableGate videoTrackEnableGate;
  final MpvEndFileGuard endFileGuard;
  final MpvChapterController chapterController;
  final MpvEditionController editionController;
  final MpvPlaylist playlist;
  final MpvSubtitleController subtitleController;
  final MpvMediaLoader mediaLoader;
  final MpvArtworkLoader artworkLoader;
  final MpvTimelineController timelineController;
  final MpvPlaybackPropertyUpdater propertyUpdater;
  final MpvSeekController seekController;
  final MpvNativeLifecycle nativeLifecycle;
  final MpvStateBuilder stateBuilder;

  private MpvPlayerComponents(
      Context applicationContext, MpvPlayer player, MpvPlayerConfig config, Handler handler) {
    MpvClient client = new MpvClient();
    MpvLoadGeneration loadGeneration = new MpvLoadGeneration();
    MpvNativeState nativeState = new MpvNativeState();
    this.options = new MpvOptions(applicationContext, config);
    MpvPropertyAccessor properties = new MpvPropertyAccessor(client, nativeState);
    MpvComponentListener componentListener = new MpvComponentListener(player);
    MpvMediaMetadata mediaMetadata = new MpvMediaMetadata();
    this.errorFactory = new MpvPlaybackErrorFactory(client);
    this.playbackProperties = new MpvPlaybackProperties(properties);
    this.commandDispatcher = new MpvCommandDispatcher(client);
    this.playbackState = new MpvPlaybackState();
    this.effectController =
        new MpvEffectController(
            playbackProperties,
            commandDispatcher,
            () -> playbackState.hasActiveFile(nativeState.isInitialized()),
            options.isAudioPassthroughEnabled());
    this.surfaceController = new MpvSurfaceController(client, componentListener);
    this.audioFocusManager =
        new MpvAudioFocusManager(applicationContext, handler, componentListener);
    this.trackController = new MpvTrackController(componentListener, properties);
    this.playerInfo = new MpvPlayerInfo();
    this.playbackEventState = new MpvPlaybackEventState();
    this.videoState = new MpvVideoState(componentListener);
    this.videoTrackEnableGate =
        new MpvVideoTrackEnableGate(
            playbackState, trackController, videoState, player::invalidatePlayerState);
    this.endFileGuard = new MpvEndFileGuard();
    this.chapterController = new MpvChapterController(properties);
    this.editionController = new MpvEditionController(properties);
    this.playlist = new MpvPlaylist();
    this.artworkLoader = new MpvArtworkLoader(properties, mediaMetadata);
    this.subtitleController =
        new MpvSubtitleController(
            commandDispatcher,
            MpvPlayerHosts.subtitleHost(
                nativeState, playlist, playbackState, playerInfo, properties),
            config);
    MpvPerFileOptionsComposer perFileOptionsComposer =
        new MpvPerFileOptionsComposer(
            options,
            playbackProperties,
            playerInfo,
            audioFocusManager,
            subtitleController,
            effectController,
            surfaceController);
    this.mediaLoader =
        new MpvMediaLoader(
            client,
            commandDispatcher,
            playbackState,
            subtitleController,
            audioFocusManager,
            errorFactory,
            loadGeneration,
            perFileOptionsComposer,
            MpvPlayerHosts.mediaLoaderHost(player, playerInfo));
    MpvPlaybackNavigator playbackNavigator = new MpvPlaybackNavigator(playlist);
    this.timelineController =
        new MpvTimelineController(
            playlist,
            playbackState,
            playbackNavigator,
            MpvPlayerHosts.timelineHost(player, playerInfo));
    this.propertyUpdater =
        new MpvPlaybackPropertyUpdater(
            properties, playbackState, videoState, videoTrackEnableGate);
    this.seekController =
        new MpvSeekController(
            playbackState,
            commandDispatcher,
            properties,
            MpvPlayerHosts.seekHost(
                player, nativeState, playerInfo, playbackEventState, playlist, propertyUpdater));
    MpvPlaybackEventHandler playbackEventHandler =
        new MpvPlaybackEventHandler(
            playbackEventState,
            playlist,
            playbackNavigator,
            playbackState,
            endFileGuard,
            chapterController,
            editionController,
            subtitleController,
            seekController,
            trackController,
            videoState,
            videoTrackEnableGate,
            propertyUpdater,
            audioFocusManager,
            errorFactory,
            artworkLoader,
            MpvPlayerHosts.playbackEventHost(player, playerInfo));
    MpvEventAdapter eventAdapter =
        new MpvEventAdapter(
            playbackEventHandler,
            playbackEventHandler.propertyEventHost(),
            client::onCommandReply,
            player::onAudioOutputChanged,
            loadGeneration);
    this.nativeLifecycle =
        new MpvNativeLifecycle(
            applicationContext,
            player,
            client,
            nativeState,
            eventAdapter,
            errorFactory,
            MpvPlayerHosts.nativeLifecycleHost(
                player,
                options,
                playbackProperties,
                properties,
                effectController,
                subtitleController,
                surfaceController,
                trackController));
    this.stateBuilder =
        new MpvStateBuilder(
            playlist,
            playbackState,
            chapterController,
            editionController,
            trackController,
            videoState,
            surfaceController,
            mediaMetadata);
  }

  static MpvPlayerComponents create(
      Context applicationContext, MpvPlayer player, MpvPlayerConfig config, Handler handler) {
    return new MpvPlayerComponents(applicationContext, player, config, handler);
  }
}
