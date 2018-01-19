/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.TeamCityProperties;

/**
 * @author Yegor.Yarko
 *         Date: 04.08.2009
 */
public class Constants {
  public static final int DEFAULT_PAGE_ITEMS_COUNT = 100;

  public static final String API_URL = "/app/rest";

  public static final String BIND_PATH_PROPERTY_NAME = "api.path";
  public static final String ORIGINAL_REQUEST_URI_HEADER_NAME = "original-request-uri";

  public static final String EXTERNAL_APPLICATION_WADL_NAME = "/application.wadl"; //name that user requests will use
  public static final String JERSEY_APPLICATION_WADL_NAME = "/application.wadl";

  public static int getDefaultPageItemsCount(){
    return TeamCityProperties.getInteger("rest.defaultPageSize", DEFAULT_PAGE_ITEMS_COUNT);
  }
}
