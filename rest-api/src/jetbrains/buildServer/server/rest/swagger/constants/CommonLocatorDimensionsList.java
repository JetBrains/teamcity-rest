/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.swagger.constants;

import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.CommonLocatorDimension;

import java.util.HashMap;
import java.util.Map;

public class CommonLocatorDimensionsList {
  public static final String PROPERTY = "property";
  public static final String CURRENT = "current";

  public static final Map<String, CommonLocatorDimension> dimensionHashMap = createMap();

  private static Map<String, CommonLocatorDimension> createMap() {
    Map<String, CommonLocatorDimension> dimensionHashMap = new HashMap<>();
    dimensionHashMap.put(
        PagerData.COUNT, new CommonLocatorDimension(
            PagerData.COUNT, LocatorDimensionDataType.INTEGER, "", "For paginated calls, how many entities to return per page.", "", false
        )
    );
    dimensionHashMap.put(
        PagerData.START, new CommonLocatorDimension(
            PagerData.START, LocatorDimensionDataType.INTEGER, "", "For paginated calls, from which entity to start rendering the page.", "", false
        )
    );
    dimensionHashMap.put(
        AbstractFinder.DIMENSION_LOOKUP_LIMIT, new CommonLocatorDimension(
            AbstractFinder.DIMENSION_LOOKUP_LIMIT, LocatorDimensionDataType.INTEGER, "", "Limit processing to the latest `<lookupLimit>` entities.", "", false
        )
    );
    dimensionHashMap.put(
        AbstractFinder.DIMENSION_ID, new CommonLocatorDimension(
            AbstractFinder.DIMENSION_ID, LocatorDimensionDataType.STRING, "", "Entity ID.", "", false
        )
    );
    dimensionHashMap.put(
        AbstractFinder.DIMENSION_ITEM, new CommonLocatorDimension(
            AbstractFinder.DIMENSION_ITEM, LocatorDimensionDataType.STRING, "item:(<locator1>),item:(<locator2>...)", "Supply multiple locators and return a union of the results.", "", false
        )
    );
    dimensionHashMap.put(
        PROPERTY, new CommonLocatorDimension(
            PROPERTY, LocatorDimensionDataType.STRING, "property:(name:<name>,value:<value>,matchType:<matchType>)", "",
            "exists,not-exists,equals,does-not-equal,starts-with,contains,does-not-contain,ends-with,any,matches,does-not-match,more-than,no-more-than,less-than,no-less-than,ver-more-than,ver-no-more-than,ver-less-than,ver-no-less-than",
            false
        )
    );
    dimensionHashMap.put(
      CURRENT, new CommonLocatorDimension(
        CURRENT, LocatorDimensionDataType.STRING, "current", "Return user that has issued this request.", "", false
      )
    );
    return dimensionHashMap;
  }
}