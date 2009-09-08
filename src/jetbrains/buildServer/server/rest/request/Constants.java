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

package jetbrains.buildServer.server.rest.request;

/**
 * @author Yegor.Yarko
 *         Date: 04.08.2009
 */
public class Constants {
  static final String DEFAULT_PAGE_ITEMS_COUNT = "100";
  private static final String URL_PREFIX = "/httpAuth";
  public static final String API_URL_SUFFIX = "/api";
  public static final String API_URL = URL_PREFIX + API_URL_SUFFIX;
}
