/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.SortedList;
import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.FilterUtil;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.util.CaseInsensitiveStringComparator;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 13.07.2009
 */
@XmlRootElement(name = "properties")
public class Properties  implements DefaultValueAware {
  private static final Logger LOG = Logger.getInstance(Properties.class.getName());

  protected static final String PROPERTY = "property";
  @XmlAttribute
  public Integer count;

  @XmlAttribute(required = false)
  @Nullable
  public String href;

  @XmlElement(name = PROPERTY)
  public List<Property> properties = new SortedList<Property>(new Comparator<Property>() {
    private final CaseInsensitiveStringComparator comp = new CaseInsensitiveStringComparator();

    public int compare(final Property o1, final Property o2) {
      return comp.compare(o1.name, o2.name);
    }
  });

  public Properties() {
  }

  //todo: review all null usages for href to include due URL
  public Properties(@Nullable final Map<String, String> properties, @Nullable String href, @NotNull final Fields fields) {
    if (properties == null) {
      this.count = null;
      this.properties = null;
    } else {
      this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), properties.size());
      if (fields.isIncluded(PROPERTY, false, true)){
        final Fields propertyFields = fields.getNestedField(PROPERTY, Fields.NONE, Fields.LONG);
        final ParameterCondition parameterCondition = getParameterCondition(fields);
        for (java.util.Map.Entry<String, String> prop : properties.entrySet()) {
          if (parameterCondition == null || parameterCondition.parameterMatches(new SimpleParameter(prop.getKey(), prop.getValue() != null ? prop.getValue() : ""))) {
            this.properties.add(new Property(prop.getKey(), prop.getValue(), propertyFields));
          }
        }
      }
    }
    this.href = ValueWithDefault.decideDefault(fields.isIncluded("href"), href);
  }

  public Properties(@Nullable final Collection<Parameter> parameters,
                    @Nullable final Collection<Parameter> ownParameters,
                    @Nullable String href,
                    @NotNull final Fields fields,
                    @NotNull final ServiceLocator serviceLocator) {
    if (parameters == null) {
      this.count = null;
      this.properties = null;
    } else {
      this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), parameters.size());
      if (fields.isIncluded(PROPERTY, false, true)) {
        final Fields propertyFields = fields.getNestedField(PROPERTY, Fields.NONE, Fields.LONG);
        final ParameterCondition parameterCondition = getParameterCondition(fields);
        for (Parameter parameter : parameters) {
          if (parameterCondition == null || parameterCondition.parameterMatches(parameter)) {
            this.properties.add(new Property(parameter, ownParameters != null && ownParameters.contains(parameter), propertyFields, serviceLocator));
          }
        }
      }
    }
    this.href = ValueWithDefault.decideDefault(fields.isIncluded("href"), href);
  }

  /**
   * Ignores any errors in the syntax: they will be logged but null will be returned as in the current usages it is already too late to report errors
   */
  @Nullable
  public static ParameterCondition getParameterCondition(@NotNull final Fields fields) {
    final String propertiesLocator = fields.getLocator();
    if (propertiesLocator != null) {
      try {
        return ParameterCondition.create(propertiesLocator);
      } catch (RuntimeException e) {
        // ignore
        LOG.debug("Encountered and ignored error while processing fields '" + fields.getFieldsSpec() + "': " + e.toString());
      }
    }
    return null;
  }

  public static List<Parameter> convertToSimpleParameters(final Map<String, String> parametersMap) {
    return CollectionsUtil.convertCollection(parametersMap.entrySet(), new Converter<Parameter, Map.Entry<String, String>>() {
      public Parameter createFrom(@NotNull final Map.Entry<String, String> source) {
        return new SimpleParameter(source.getKey(), source.getValue());
      }
    });
  }

  @NotNull
  public Map<String, String> getMap() {
    return getMap(null);
  }

  @NotNull
  public Map<String, String> getMap(final Boolean ownOnly) {
    if (properties == null) {
      return new HashMap<String, String>();
    }
    final HashMap<String, String> result = new HashMap<String, String>(properties.size());
    for (Property property : properties) {
      boolean actualOwn =  property.own != null && property.own;
      if (FilterUtil.isIncludedByBooleanFilter(ownOnly, actualOwn)){
        result.put(property.name, property.value);
      }
    }
    return result;
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, href, properties);
  }

  @NotNull
  public List<Parameter> getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (properties == null) {
      return Collections.emptyList();
    }
    final ArrayList<Parameter> result = new ArrayList<Parameter>(properties.size());
    for (Property parameter : properties) {
      result.add(parameter.getFromPosted(serviceLocator));
    }
    return result;
  }
}
