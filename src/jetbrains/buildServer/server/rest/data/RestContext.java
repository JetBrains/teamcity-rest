/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.function.Function;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.util.SimpleStringPool;
import jetbrains.buildServer.server.rest.util.StringPool;
import jetbrains.buildServer.util.FuncThrow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 07/02/2018
 */
public class RestContext {
  private final static ThreadLocal<RestContext> ourThreadLocalInstance = new ThreadLocal<>();

  private final Function<String, Object> myFunction;
  private final StringPool myStringPool = new SimpleStringPool();

  public RestContext(@NotNull Function<String, Object> function) {
    myFunction = function;
  }


  public static RestContext getThreadLocal() {
    return ourThreadLocalInstance.get();
  }

  @NotNull
  public static StringPool getThreadLocalStringPool() {
    RestContext ctx = ourThreadLocalInstance.get();
    if(ctx == null) {
      // noop StringPool
      return s -> s;
    }
    return ctx.myStringPool;
  }

  private static void setThreadLocal(@NotNull RestContext context) {
    ourThreadLocalInstance.set(context);
  }

  private static void removeThreadLocal() {
    ourThreadLocalInstance.remove();
  }

  public <T, E extends Throwable> T run(@NotNull FuncThrow<T, E> action) throws E {
    if (getThreadLocal() != null) {
      //if this will be necessary, need to implement nesting (maintain list of functions and add to the end, or remember, replace, restore on leaving the scope)
      throw new OperationException("Trying to override context when it is already set");
    }
    setThreadLocal(this);
    try {
      return action.apply();
    } finally {
      removeThreadLocal();
    }
  }

  @Nullable
  public Object getVar(@NotNull final String name) {
    if (!isValidName(name)) {
      throw new OperationException("Context variable has invalid name: '" + name + "'. Should be alphanumeric and start with a letter.");
    }
    return myFunction.apply(name);
  }

  private boolean isValidName(@NotNull final String name) {
    if (name.isEmpty()) return false;
    return Character.isLetter(name.charAt(0)) && name.chars().allMatch(ch -> Character.isLetter(ch) || Character.isDigit(ch));
  }
}
