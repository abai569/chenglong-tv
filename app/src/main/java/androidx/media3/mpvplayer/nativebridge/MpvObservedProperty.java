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

import androidx.annotation.Nullable;
import is.xyz.mpv.MPVLib;

final class MpvObservedProperty {

  final String name;
  private final int format;
  @Nullable private final InvalidatedHandler invalidatedHandler;
  @Nullable private final LongHandler longHandler;
  @Nullable private final BooleanHandler booleanHandler;
  @Nullable private final DoubleHandler doubleHandler;

  private MpvObservedProperty(
      String name,
      int format,
      @Nullable InvalidatedHandler invalidatedHandler,
      @Nullable LongHandler longHandler,
      @Nullable BooleanHandler booleanHandler,
      @Nullable DoubleHandler doubleHandler) {
    this.name = name;
    this.format = format;
    this.invalidatedHandler = invalidatedHandler;
    this.longHandler = longHandler;
    this.booleanHandler = booleanHandler;
    this.doubleHandler = doubleHandler;
  }

  static MpvObservedProperty invalidatedProperty(
      String name, InvalidatedHandler invalidatedHandler) {
    return new MpvObservedProperty(
        name, MPVLib.MpvFormat.MPV_FORMAT_NONE, invalidatedHandler, null, null, null);
  }

  static MpvObservedProperty invalidatedProperty(String name) {
    return new MpvObservedProperty(
        name, MPVLib.MpvFormat.MPV_FORMAT_NONE, null, null, null, null);
  }

  static MpvObservedProperty longProperty(
      String name, @Nullable InvalidatedHandler invalidatedHandler, LongHandler longHandler) {
    return new MpvObservedProperty(
        name, MPVLib.MpvFormat.MPV_FORMAT_INT64, invalidatedHandler, longHandler, null, null);
  }

  static MpvObservedProperty invalidatingLongProperty(
      String name, InvalidatedAction invalidatedAction, LongAction longAction) {
    return longProperty(
        name,
        host -> {
          invalidatedAction.run(host);
          return true;
        },
        (host, value) -> {
          longAction.run(host, value);
          return true;
        });
  }

  static MpvObservedProperty doubleProperty(String name, DoubleHandler doubleHandler) {
    return doubleProperty(name, null, doubleHandler);
  }

  static MpvObservedProperty doubleProperty(
      String name, @Nullable InvalidatedHandler invalidatedHandler, DoubleHandler doubleHandler) {
    return new MpvObservedProperty(
        name, MPVLib.MpvFormat.MPV_FORMAT_DOUBLE, invalidatedHandler, null, null, doubleHandler);
  }

  static MpvObservedProperty invalidatingDoubleProperty(String name, DoubleAction doubleAction) {
    return doubleProperty(
        name,
        (host, value) -> {
          doubleAction.run(host, value);
          return true;
        });
  }

  static MpvObservedProperty invalidatingDoubleProperty(
      String name, InvalidatedAction invalidatedAction, DoubleAction doubleAction) {
    return doubleProperty(
        name,
        host -> {
          invalidatedAction.run(host);
          return true;
        },
        (host, value) -> {
          doubleAction.run(host, value);
          return true;
        });
  }

  static MpvObservedProperty booleanProperty(String name, BooleanHandler booleanHandler) {
    return booleanProperty(name, null, booleanHandler);
  }

  static MpvObservedProperty booleanProperty(
      String name, @Nullable InvalidatedHandler invalidatedHandler, BooleanHandler booleanHandler) {
    return new MpvObservedProperty(
        name, MPVLib.MpvFormat.MPV_FORMAT_FLAG, invalidatedHandler, null, booleanHandler, null);
  }

  static MpvObservedProperty invalidatingBooleanProperty(String name, BooleanAction booleanAction) {
    return booleanProperty(
        name,
        (host, value) -> {
          booleanAction.run(host, value);
          return true;
        });
  }

  static MpvObservedProperty invalidatingBooleanProperty(
      String name, InvalidatedAction invalidatedAction, BooleanAction booleanAction) {
    return booleanProperty(
        name,
        host -> {
          invalidatedAction.run(host);
          return true;
        },
        (host, value) -> {
          booleanAction.run(host, value);
          return true;
        });
  }

  boolean observe(MpvNativeClient client) {
    return client.observeProperty(name, format);
  }

  boolean onInvalidated(MpvEventAdapter.PropertyEventHost host) {
    return invalidatedHandler == null || invalidatedHandler.handle(host);
  }

  boolean onLong(MpvEventAdapter.PropertyEventHost host, long value) {
    return longHandler == null || longHandler.handle(host, value);
  }

  boolean onBoolean(MpvEventAdapter.PropertyEventHost host, boolean value) {
    return booleanHandler == null || booleanHandler.handle(host, value);
  }

  boolean onDouble(MpvEventAdapter.PropertyEventHost host, double value) {
    return doubleHandler == null || doubleHandler.handle(host, value);
  }

  interface InvalidatedHandler {

    boolean handle(MpvEventAdapter.PropertyEventHost host);
  }

  interface InvalidatedAction {

    void run(MpvEventAdapter.PropertyEventHost host);
  }

  interface LongHandler {

    boolean handle(MpvEventAdapter.PropertyEventHost host, long value);
  }

  interface LongAction {

    void run(MpvEventAdapter.PropertyEventHost host, long value);
  }

  interface BooleanHandler {

    boolean handle(MpvEventAdapter.PropertyEventHost host, boolean value);
  }

  interface BooleanAction {

    void run(MpvEventAdapter.PropertyEventHost host, boolean value);
  }

  interface DoubleHandler {

    boolean handle(MpvEventAdapter.PropertyEventHost host, double value);
  }

  interface DoubleAction {

    void run(MpvEventAdapter.PropertyEventHost host, double value);
  }
}
