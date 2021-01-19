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

package jetbrains.buildServer.server.rest.swagger.constants;

import jetbrains.buildServer.server.rest.data.AbstractFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.CommonLocatorDimension;

import java.util.HashMap;
import java.util.Map;

public class CommonLocatorDimensionsList {
  public static final String PROPERTY = "property";

  public static final Map<String, CommonLocatorDimension> dimensionHashMap = createMap();

  private static Map<String, CommonLocatorDimension> createMap() {
    Map<String, CommonLocatorDimension> dimensionHashMap = new HashMap<>();
    dimensionHashMap.put(
        PagerData.COUNT, new CommonLocatorDimension(
            PagerData.COUNT, LocatorDimensionDataType.INTEGER, "", "For paginated calls, how many entities to return per page."
        )
    );
    dimensionHashMap.put(
        PagerData.START, new CommonLocatorDimension(
            PagerData.START, LocatorDimensionDataType.INTEGER, "", "For paginated calls, from which entity to start rendering the page."
        )
    );
    dimensionHashMap.put(
        AbstractFinder.DIMENSION_LOOKUP_LIMIT, new CommonLocatorDimension(
            AbstractFinder.DIMENSION_LOOKUP_LIMIT, LocatorDimensionDataType.INTEGER, "", "Limit processing to the latest `<lookupLimit>` entities."
        )
    );
    dimensionHashMap.put(
        AbstractFinder.DIMENSION_ID, new CommonLocatorDimension(
            AbstractFinder.DIMENSION_ID, LocatorDimensionDataType.INTEGER, "", "Entity ID."
        )
    );
    dimensionHashMap.put(
        Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, new CommonLocatorDimension(
            Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, LocatorDimensionDataType.BOOLEAN, "", "Return a single value."
        )
    );
    dimensionHashMap.put(
        AbstractFinder.DIMENSION_ITEM, new CommonLocatorDimension(
            AbstractFinder.DIMENSION_ITEM, LocatorDimensionDataType.STRING, "item:(<locator1>),item:(<locator2>...)", "Supply multiple locators and return a union of the results."
        )
    );
    dimensionHashMap.put(
        PROPERTY, new CommonLocatorDimension(
            PROPERTY, LocatorDimensionDataType.STRING, "property:(name:<name>,value:<value>,matchType:<matchType>)", "Supported matchType values: \n" +
            "- generic: exists/not-exists/equals/does-not-equal/starts-with/contains/does-not-contain/ends-with/any; \n" +
            "- regular expressions: matches/does-not-match; \n" +
            "- numeric: more-than/no-more-than/less-than/no-less-than; \n" +
            "- version-specific: ver-more-than/ver-no-more-than/ver-less-than/ver-no-less-than."
        )
    );
    return dimensionHashMap;
  }
}
