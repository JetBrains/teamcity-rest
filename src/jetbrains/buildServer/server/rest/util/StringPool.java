/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public interface StringPool {
  /**
   * Ask the pool to reuse given string, it's up to implementation to decide if it will do that.
   *
   * <p>
   * Implementation must guarantee <code>reuse(a).equals(reuse(b))</code> for <code>a.equals(b)</code>.
   * Implementation does NOT have to guarantee <code>reuse(a) == reuse(b)</code> for <code>a.equals(b) && a != b</code>.
   * </p>
   * @param value
   * @return
   */
  @Contract("null -> null, !null -> !null")
  public String reuse(@Nullable String value);
}
