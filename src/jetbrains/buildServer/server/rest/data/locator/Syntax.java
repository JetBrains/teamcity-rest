/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.data.locator;


import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public interface Syntax {
  default String getNotes() {
    return null;
  }

  default String getId() {
    return getClass().getSimpleName();
  }

  static TODO TODO(@NotNull String msg) {
    return new TODO(msg);
  }

  class TODO implements Syntax, Supplier<TODO> {
    private static final AtomicLong INSTANCE_COUNTER = new AtomicLong();

    private final String myNotes;
    private final long myId = INSTANCE_COUNTER.getAndIncrement();

    public TODO(@NotNull String notes) {
      myNotes = notes;
    }

    @Override
    public String getNotes() {
      return myNotes;
    }

    @Override
    public String getId() {
      return "TODO #" + myId;
    }

    @Override
    public TODO get() {
      return this;
    }
  }
}
