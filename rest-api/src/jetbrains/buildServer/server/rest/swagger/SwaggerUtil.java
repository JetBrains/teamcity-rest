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

package jetbrains.buildServer.server.rest.swagger;

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import java.util.*;

public class SwaggerUtil {
  private static final Logger LOG = Logger.getInstance(SwaggerUtil.class.getName());

  static void doAnalyzeSwaggerDefinitionReferences(final Swagger swagger) {
    final HashSet<String> usedReferences = new HashSet<String>();

    // Collect usages in paths
    for (Path path : swagger.getPaths().values()) {
      for (Operation operation : path.getOperations()) {
        final List<Parameter> parameters = operation.getParameters();
        for (Parameter parameter : parameters) {
          if (parameter instanceof BodyParameter) {
            final BodyParameter bp = (BodyParameter)parameter;
            final Model schema = bp.getSchema();
            if (schema instanceof RefModel) {
              RefModel rm = (RefModel)schema;
              final String ref = rm.getSimpleRef();
              usedReferences.add(ref);
            }
          }
        }
        final Map<String, Response> responses = operation.getResponses();
        for (Response response : responses.values()) {
          final Property schema = response.getSchema();
          if (schema instanceof RefProperty) {
            RefProperty rp = (RefProperty)schema;
            final String ref = rp.getSimpleRef();
            usedReferences.add(ref);
          }
        }
      }
    }

    final Map<String, Model> definitions = swagger.getDefinitions();

    final ArrayDeque<String> queue = new ArrayDeque<String>();

    queue.addAll(usedReferences);

    while (!queue.isEmpty()) {
      final String name = queue.pop();
      final Model model = definitions.get(name);
      if (model == null) {
        LOG.warn("Swagger definition '" + name + "' referenced but not found.");
        continue;
      }
      final Map<String, Property> properties = model.getProperties();
      if (properties == null) continue;
      for (Property property : properties.values()) {
        final String ref = getPropertySimpleRef(property);
        if (ref != null) {
          if (usedReferences.add(ref)) {
            queue.add(ref);
          }
        }
      }
    }

    final int used = usedReferences.size();
    final int total = definitions.size();
    LOG.info("Swagger definitions stats: Total=" + total + " Used=" + used);
    if (used != total) {
      final LinkedHashSet<String> unused = new LinkedHashSet<String>(definitions.keySet());
      unused.removeAll(usedReferences);
      if (unused.size() > 30) {
        LOG.warn("Too much unused definitions. Enable debug logs to see them");
        LOG.debug("Unused definitions: " + unused);
      } else {
        LOG.info("Unused definitions: " + unused);
      }
    }
  }

  static <K extends Comparable<? super K>, V> Map<K, V> getOrderedMap(Map<K, V> input) {
    if (input == null) return null;
    Map<K, V> sorted = new LinkedHashMap<K, V>();
    List<K> keys = new ArrayList<K>();
    keys.addAll(input.keySet());
    Collections.sort(keys);

    for (K key : keys) {
      sorted.put(key, input.get(key));
    }
    return sorted;
  }

  private static String getPropertySimpleRef(Property property) {
    if (property instanceof RefProperty) {
      RefProperty rp = (RefProperty)property;
      return rp.getSimpleRef();
    } else if (property instanceof ArrayProperty) {
      final ArrayProperty ap = (ArrayProperty)property;
      final Property items = ap.getItems();
      return getPropertySimpleRef(items);
    } else if (property instanceof MapProperty) {
      final MapProperty mp = (MapProperty)property;
      final Property items = mp.getAdditionalProperties();
      return getPropertySimpleRef(items);
    }
    return null;
  }
}