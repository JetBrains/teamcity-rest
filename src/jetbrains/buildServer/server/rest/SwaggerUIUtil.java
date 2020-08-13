/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import java.io.InputStream;
import java.net.URL;

public class SwaggerUIUtil {

  public static final String INDEX = "index.html";
  public static final String RESOURCE_PATH = "main/resources/swagger/";

  public static InputStream getFileFromResources(String path) {
    String fullPath = RESOURCE_PATH + path;
    ClassLoader classLoader = SwaggerUIUtil.class.getClassLoader();

    URL resource = classLoader.getResource(fullPath);
    if (resource == null) {
      throw new IllegalArgumentException(String.format("File %s was not found", fullPath));
    } else {
      InputStream stream = classLoader.getResourceAsStream(fullPath);
      return stream;
    }

  }
}
