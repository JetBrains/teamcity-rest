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

package jetbrains.buildServer.server.rest.data.locator.definition;


import java.lang.invoke.MethodHandles;
import jetbrains.buildServer.server.rest.data.finder.syntax.CommonLocatorDimensions;
import jetbrains.buildServer.server.rest.data.locator.Dimension;

/**
 * Marks a class with dimension definitions for a finder. Provides definitions for common dimensions 'or', 'and', 'not', 'item', 'unique', 'lookupLimit'.
 * <br/>
 * @implNote
 *   Dimension definitions use a trick to look up the class implementing this interface.<br/>
 *   <code>(Class<? extends LocatorDefinition>) MethodHandles.lookup().lookupClass()</code> is a replacement for <code>this.getClass()</code> which can not be used in static context.
 *
 */
public interface FinderLocatorDefinition extends PageableLocatorDefinition {
  Dimension UNIQUE = CommonLocatorDimensions.UNIQUE;
  Dimension LOOKUP_LIMIT = CommonLocatorDimensions.LOOKUP_LIMIT;

  @SuppressWarnings("unchecked")
  Dimension LOGICAL_OR = CommonLocatorDimensions.LOGICAL_OR(() -> (Class<? extends LocatorDefinition>) MethodHandles.lookup().lookupClass());
  @SuppressWarnings("unchecked")
  Dimension LOGICAL_AND = CommonLocatorDimensions.LOGICAL_AND(() -> (Class<? extends LocatorDefinition>) MethodHandles.lookup().lookupClass());
  @SuppressWarnings("unchecked")
  Dimension LOGICAL_NOT = CommonLocatorDimensions.LOGICAL_NOT(() -> (Class<? extends LocatorDefinition>) MethodHandles.lookup().lookupClass());
  @SuppressWarnings("unchecked")
  Dimension ITEM = CommonLocatorDimensions.ITEM(() -> (Class<? extends LocatorDefinition>) MethodHandles.lookup().lookupClass());
}
