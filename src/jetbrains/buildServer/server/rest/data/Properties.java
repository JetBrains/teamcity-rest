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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.users.PropertyKey;

/**
 * @author Yegor.Yarko
 *         Date: 13.07.2009
 */
@XmlRootElement(name = "properties")
public class Properties {
  @XmlElement(name = "property")
  public List<Property> properties;

  public Properties() {
  }

  public Properties(final Map<String, String> propertiesP) {
    properties = new ArrayList<Property>(propertiesP.size());
    for (Map.Entry<String, String> prop : propertiesP.entrySet()) {
      properties.add(new Property(prop.getKey(), prop.getValue()));
    }
  }

  public void init(final Map<PropertyKey, String> propertiesP) {
    properties = new ArrayList<Property>(propertiesP.size());
    for (Map.Entry<PropertyKey, String> prop : propertiesP.entrySet()) {
      properties.add(new Property(prop.getKey().getKey(), prop.getValue()));
    }
  }
}
