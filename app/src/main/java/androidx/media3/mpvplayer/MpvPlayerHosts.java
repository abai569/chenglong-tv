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

import static androidx.media3.mpvplayer.nativebridge.MpvConstants.OPT_VIDEO_OUTPUT;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.mpvplayer.core.MpvEffectController;
import androidx.media3.mpvplayer.core.MpvPlaybackEventHandler;
import androidx.media3.mpvplayer.core.MpvPlaybackEventState;
import androidx.media3.mpvplayer.core.MpvPlaybackPropertyUpdater;
import androidx.media3.mpvplayer.core.MpvPlaybackState;
import androidx.media3.mpvplayer.core.MpvPlayerInfo;
import androidx.media3.mpvplayer.media.MpvMediaLoader;
import androidx.media3.mpvplayer.media.MpvPlaylist;
import androidx.media3.mpvplayer.media.MpvSubtitleController;
import androidx.media3.mpvplayer.media.MpvTimelineController;
import androidx.media3.mpvplayer.nativebridge.MpvNativeLifecycle;
import androidx.media3.mpvplayer.nativebridge.MpvNativeState;
import androidx.media3.mpvplayer.nativebridge.MpvPlaybackProperties;
import androidx.media3.mpvplayer.nativebridge.MpvPropertyAccessor;
import androidx.media3.mpvplayer.options.MpvOptions;
import androidx.media3.mpvplayer.seek.MpvSeekController;
import androidx.media3.mpvplayer.trackselection.MpvTrackController;
import androidx.media3.mpvplayer.video.MpvSurfaceController;

final class MpvPlayerHosts {

  static MpvMediaLoader.Host mediaLoaderHost(MpvPlayer player, MpvPlayerInfo playerInfo) {
    return new MpvMediaLoader.Host() {
      @Override
      public boolean hasActiveMpvFile() {
        return player.hasActiveMpvFile();
      }

      @Override
      public void expectProgrammaticEndFile() {
        player.expectProgrammaticEndFile();
      }

      @Override
      public boolean isPlayWhenReady() {
        return playerInfo.isPlayWhenReady();
      }

      @Override
      public void clearPlayerError() {
        playerInfo.clearPlayerError();
      }

      @Override
      public void resetSeekState() {
        player.resetSeekState();
      }

      @Override
      public void resetCurrentMediaState() {
        player.resetCurrentMediaState();
      }

      @Override
      public void invalidatePlayerState() {
        player.invalidatePlayerState();
      }

      @Override
      public void fail(PlaybackException error) {
        player.fail(error);
      }
    };
  }

  static MpvTimelineController.Host timelineHost(MpvPlayer player, MpvPlayerInfo playerInfo) {
    return new MpvTimelineController.Host() {
      @Override
      public boolean stopActiveFile() {
        return player.stopActiveFile();
      }

      @Override
      public void releaseAudioFocus() {
        player.releaseAudioFocus();
      }

      @Override
      public boolean hasActiveMpvFile() {
        return player.hasActiveMpvFile();
      }

      @Override
      public void loadCurrent(MediaItem item, long startPositionMs) {
        player.loadCurrent(item, startPositionMs);
      }

      @Override
      public void resetCurrentMediaState(long startPositionMs) {
        player.resetCurrentMediaState(startPositionMs);
      }

      @Override
      public void clearPlayerError() {
        playerInfo.clearPlayerError();
      }

      @Override
      public void setPendingDiscontinuity(@Player.DiscontinuityReason int reason, long positionMs) {
        player.setPendingDiscontinuity(reason, positionMs);
      }
    };
  }

  static MpvSubtitleController.Host subtitleHost(
      MpvNativeState nativeState,
      MpvPlaylist playlist,
      MpvPlaybackState playbackState,
      MpvPlayerInfo playerInfo,
      MpvPropertyAccessor properties) {
    return new MpvSubtitleController.Host() {
      @Override
      public boolean isInitialized() {
        return nativeState.isInitialized();
      }

      @Override
      public boolean hasActiveFile() {
        return playbackState.hasActiveFile(nativeState.isInitialized());
      }

      @Override
      public @Nullable String getStringOptionOrProperty(String name) {
        return properties.getString(name);
      }

      @Override
      public @Nullable Double getDoubleOptionOrProperty(String name) {
        return properties.getDouble(name);
      }

      @Override
      public void setStringOptionOrProperty(String name, String value) {
        properties.setStringOptionOrProperty(name, value);
      }

      @Override
      public void setDoubleOptionOrProperty(String name, double value) {
        properties.setDoubleOptionOrProperty(name, value);
      }

      @Override
      public @Nullable MediaItem currentMediaItem() {
        return playlist.current();
      }

      @Override
      public boolean isCurrentFileLoaded() {
        return playbackState.isCurrentFileLoaded();
      }

      @Override
      public boolean hasPlayerError() {
        return playerInfo.hasPlayerError();
      }
    };
  }

  static MpvSeekController.Host seekHost(
      MpvPlayer player,
      MpvNativeState nativeState,
      MpvPlayerInfo playerInfo,
      MpvPlaybackEventState playbackEventState,
      MpvPlaylist playlist,
      MpvPlaybackPropertyUpdater propertyUpdater) {
    return new MpvSeekController.Host() {
      @Override
      public boolean isInitialized() {
        return nativeState.isInitialized();
      }

      @Override
      public boolean hasActiveMpvFile() {
        return player.hasActiveMpvFile();
      }

      @Override
      public boolean isReleased() {
        return playerInfo.isReleased();
      }

      @Override
      public boolean isPausedForCache() {
        return playbackEventState.isPausedForCache();
      }

      @Override
      public boolean shouldPlay() {
        return playerInfo.shouldPlay();
      }

      @Override
      public @Nullable MediaItem currentMediaItem() {
        return playlist.current();
      }

      @Override
      public void loadCurrent(MediaItem item, long startPositionMs) {
        player.loadCurrent(item, startPositionMs);
      }

      @Override
      public boolean stopActiveFile() {
        return player.stopActiveFile();
      }

      @Override
      public boolean maybeLoadNextAfterEnd() {
        return player.maybeLoadNextAfterEnd();
      }

      @Override
      public void releaseAudioFocus() {
        player.releaseAudioFocus();
      }

      @Override
      public void updatePosition() {
        propertyUpdater.updatePosition(propertyUpdater.readPosition());
      }

      @Override
      public void setPauseProperty() {
        player.setPauseProperty();
      }

      @Override
      public void invalidatePlayerState() {
        player.invalidatePlayerState();
      }
    };
  }

  static MpvPlaybackEventHandler.Host playbackEventHost(
      MpvPlayer player, MpvPlayerInfo playerInfo) {
    return new MpvPlaybackEventHandler.Host() {
      @Override
      public void runOnPlayerLooper(Runnable runnable) {
        player.runOnPlayerLooper(runnable);
      }

      @Override
      public void runOnPlayerLooperAfterRelease(Runnable runnable) {
        player.runOnPlayerLooperAfterRelease(runnable);
      }

      @Override
      public void invalidatePlayerState() {
        player.invalidatePlayerState();
      }

      @Override
      public boolean hasActiveMpvFile() {
        return player.hasActiveMpvFile();
      }

      @Override
      public int getRepeatMode() {
        return playerInfo.getRepeatMode();
      }

      @Override
      public boolean isPlayWhenReady() {
        return playerInfo.isPlayWhenReady();
      }

      @Override
      public boolean maybeLoadNextAfterEnd() {
        return player.maybeLoadNextAfterEnd();
      }

      @Override
      public void releaseAudioFocus() {
        player.releaseAudioFocus();
      }

      @Override
      public void setPauseProperty() {
        player.setPauseProperty();
      }

      @Override
      public void setVolumeProperty() {
        player.setVolumeProperty();
      }

      @Override
      public void setPendingDiscontinuity(@Player.DiscontinuityReason int reason, long positionMs) {
        player.setPendingDiscontinuity(reason, positionMs);
      }

      @Override
      public void onNativeShutdown() {
        player.onShutdown();
      }

      @Override
      public void fail(PlaybackException error) {
        player.fail(error);
      }

      @Override
      public void setPlayWhenReadyFromNative(boolean playWhenReady) {
        if (playerInfo.isPlayWhenReady() == playWhenReady) {
          return;
        }
        playerInfo.setPlayWhenReady(
            playWhenReady,
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);
      }
    };
  }

  static MpvNativeLifecycle.Host nativeLifecycleHost(
      MpvPlayer player,
      MpvOptions options,
      MpvPlaybackProperties playbackProperties,
      MpvPropertyAccessor properties,
      MpvEffectController effectController,
      MpvSubtitleController subtitleController,
      MpvSurfaceController surfaceController,
      MpvTrackController trackController) {
    return new MpvNativeLifecycle.Host() {
      @Override
      public void applyPreInitOptions() {
        options.applyPreInit(properties::setStringOptionOrProperty);
      }

      @Override
      public void onInitialized() {
        options.applyAppOwned(properties::setStringOptionOrProperty);
        options.deferVideoOutputUntilLoad(
            properties.getString(OPT_VIDEO_OUTPUT), properties::setStringOptionOrProperty);
        playbackProperties.setHardwareDecode(
            options.onInitialized(
                player.isHardwareDecodeEnabled(), playbackProperties.getHardwareDecode()));
        subtitleController.applyOptionsIfInitialized();
        surfaceController.onInitialized();
        options.applyRuntimeDolbyVisionOutputMode(playbackProperties::setStringOptionOrProperty);
      }

      @Override
      public void onInitializationFailed(PlaybackException error) {
        player.fail(error);
      }

      @Override
      public void releaseAudioFocus() {
        player.releaseAudioFocus();
      }

      @Override
      public void onNativeSessionEnded() {
        options.onNativeSessionEnded();
        effectController.onNativeSessionEnded();
        subtitleController.onNativeSessionEnded();
        surfaceController.onNativeSessionEnded();
        trackController.onNativeSessionEnded();
      }

      @Override
      public void onNativeReleaseFailed(PlaybackException error) {
        player.fail(error);
      }

      @Override
      public void onShutdown() {
        player.handleUnexpectedNativeShutdown();
      }

      @Override
      public void runOnPlayerLooperAfterRelease(Runnable runnable) {
        player.runOnPlayerLooperAfterRelease(runnable);
      }

      @Override
      public void onNativeSessionAvailable() {
        player.onNativeSessionAvailable();
      }
    };
  }
}
