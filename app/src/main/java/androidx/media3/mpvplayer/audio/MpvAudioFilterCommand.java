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
package androidx.media3.mpvplayer.audio;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.Nullable;
import java.util.Objects;

public final class MpvAudioFilterCommand {

  private final String label;
  private final String command;
  private final String argument;
  private final String target;

  public MpvAudioFilterCommand(String label, String command, String argument, String target) {
    this.label = checkNotNull(label);
    this.command = checkNotNull(command);
    this.argument = checkNotNull(argument);
    this.target = checkNotNull(target);
  }

  public String getLabel() {
    return label;
  }

  public String getCommand() {
    return command;
  }

  public String getArgument() {
    return argument;
  }

  public String getTarget() {
    return target;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof MpvAudioFilterCommand)) {
      return false;
    }
    MpvAudioFilterCommand other = (MpvAudioFilterCommand) object;
    return label.equals(other.label)
        && command.equals(other.command)
        && argument.equals(other.argument)
        && target.equals(other.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, command, argument, target);
  }
}
