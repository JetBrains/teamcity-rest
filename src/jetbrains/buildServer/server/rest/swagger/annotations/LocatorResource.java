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

package jetbrains.buildServer.server.rest.swagger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is put on the "Finders" (See implementations of <code>{@link jetbrains.buildServer.server.rest.data.Finder}</code>)
 * <p/>
 * This annotation is used to generate Swagger documentation.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LocatorResource {
  /**
   * The resource name
   */
  String value();

  /**
   * List of extra dimensions, which are not listed in the Finder using {@link LocatorDimension} annotation
   */
  String[] extraDimensions() default {};

  /**
   * Name of the base entity, which is handled by this Finder.
   * <br/>
   * Example: "Project" for "ProjectFinder", "VcsRoot" for "VcsRootFinder", etc.
   */
  String baseEntity();

  /**
   * Examples of string value for this locator.
   * <p/>
   * Format for examples:
   * <pre>
   *   `locator example in backticks` - description for this example
   * </pre>
   * <p/>
   * Examples of exaples:
   * <li><pre>`name:MyProject` - find a project with name `MyProject`.</pre></li>
   * <li><pre>`state:taken` - find investigations which are currently in work.</pre></li>
   */
  String[] examples() default {};
}
