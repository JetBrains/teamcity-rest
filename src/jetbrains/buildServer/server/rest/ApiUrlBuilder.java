/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 14.11.2009
 */
//temporary class for single prefix only
public class ApiUrlBuilder extends ApiUrlBuilderWithContext {

  public ApiUrlBuilder(@NotNull final RequestPathTransformInfo requestPathTransformInfo) {
    super(new PathTransformer() {
      public String transform(final String path) {
        //todo: error reporting if none or more then one
        final String singlePrefix = requestPathTransformInfo.getOriginalPathPrefixes().iterator().next();
        final String newPathPrefix = requestPathTransformInfo.getNewPathPrefix();
        if (!path.startsWith(newPathPrefix)) {
          throw new IllegalArgumentException("Path in new form: '" + path + "' does not contain new prefix: '" + newPathPrefix + "'");
        }
        return singlePrefix + path.substring(newPathPrefix.length());
      }
    });
  }
}
