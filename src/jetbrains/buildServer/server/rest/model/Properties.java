/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model;

import com.intellij.util.containers.SortedList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.CaseInsensitiveStringComparator;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 13.07.2009
 */
@XmlRootElement(name = "properties")
public class Properties {
  @XmlElement(name = "property")
  public List<Property> properties = new SortedList<Property>(new Comparator<Property>() {
    private final CaseInsensitiveStringComparator comp = new CaseInsensitiveStringComparator();

    public int compare(final Property o1, final Property o2) {
      return comp.compare(o1.name, o2.name);
    }
  });

  public Properties() {
  }

  public Properties(final Map<String, String> propertiesP) {
    for (Map.Entry<String, String> prop : propertiesP.entrySet()) {
      final String key = prop.getKey();
      if (!isPropertyToExclude(key)) {
        properties.add(new Property(key, prop.getValue()));
      }
    }
  }

  public static boolean isPropertyToExclude(@NotNull final String key) {
    //todo: openAPI (TeamCity) or should jetbrains.buildServer.agent.Constants.SECURE_PROPERTY_PREFIX be used here?
    return key.startsWith(SVcsRoot.SECURE_PROPERTY_PREFIX) && !TeamCityProperties.getBoolean("rest.listSecureProperties");
  }

  public Map<String, String> getMap() {
    final HashMap<String, String> result = new HashMap<String, String>(properties.size());
    for (Property property : properties) {
      result.put(property.name, property.value);
    }
    return result;
  }
}
