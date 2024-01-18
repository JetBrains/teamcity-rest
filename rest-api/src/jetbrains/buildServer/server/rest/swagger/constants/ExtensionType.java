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

import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;
import java.util.Map;

public class ExtensionType {
  // reference to an abstract class which may be present in client implementation (to streamline code generation)
  public static final String X_BASE_TYPE = "x-object-type";

  // reference to a base entity (e.g. Agent for AgentFinder, AgentPool for AgentPools)
  public static final String X_BASE_ENTITY = "x-base-entity";

  // Swagger models do not have built-in description field or examples holder
  public static final String X_DESCRIPTION = "x-description";
  public static final String X_MODEL_EXAMPLES = "x-model-examples";

  // extension for the REST API documentation links
  public static final String X_HELP_ARTICLE_LINK = "x-help-article-link";
  public static final String X_HELP_ARTICLE_NAME = "x-help-article-name";

  // reference to subpackage of model class
  public static final String X_SUBPACKAGE = "x-subpackage";

  // swagger-codegen does not support inline boolean checks; thus this data is available as vendor extension on model level
  public static final String X_IS_DATA = "x-is-data";
  public static final String X_IS_LOCATOR = "x-is-locator";
  public static final String X_IS_LIST = "x-is-list";
  public static final String X_IS_PAGINATED = "x-is-paginated";

  // similar to above, param-level boolean data which can be used along with the "base" class implementation
  public static final String X_DEFINED_IN_BASE = "x-defined-in-base";
  public static final String X_IS_FIRST_CONTAINER_VAR = "x-is-first-container-var";

  public static final Map<String, String[]> paramToObjectType;

  static {
    paramToObjectType = new HashMap<>();
    paramToObjectType.put(ObjectType.DATA, new String[]{"href"});
    paramToObjectType.put(ObjectType.LIST, ArrayUtils.addAll(
        new String[]{"count"},
        paramToObjectType.get(ObjectType.DATA)
        )
    );
    paramToObjectType.put(ObjectType.PAGINATED, ArrayUtils.addAll(
        new String[]{"nextHref", "prevHref"},
        paramToObjectType.get(ObjectType.LIST)
        )
    );
  }
}