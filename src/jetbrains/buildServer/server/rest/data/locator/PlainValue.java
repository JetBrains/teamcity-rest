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


/**
 * Represents a value without internal structure important for the locator definition, i.e. integers, strings, enums, etc.
 * Intended use is for various counts and exact matches, e.g. pager `count` or `uuid`.
 */
public interface PlainValue extends Syntax {
  static PlainValue string() {
    return new PlainValue() {
      @Override
      public String getFormat() {
        return "String value";
      }
    };
  }

  static PlainValue int64() {
    return new PlainValue() {
      @Override
      public String getFormat() {
        return "int64 value";
      }
    };
  }
}
