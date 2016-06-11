/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.InheritableUserParametersHolder;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.UserParametersHolder;
import jetbrains.buildServer.util.CaseInsensitiveStringComparator;
import org.jetbrains.annotations.Contract;
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
  public List<Property> properties;

  public Properties() {
  }

  //todo: review all null usages for href to include due URL
  public Properties(@Nullable final Map<String, String> properties, @Nullable String href, @NotNull final Fields fields, @NotNull final ServiceLocator serviceLocator) {
    this(properties, null, href, fields, serviceLocator);
  }

  public Properties(@Nullable final Map<String, String> parameters,
                    @Nullable final Map<String, String> ownParameters,
                    @Nullable String href,
                    @NotNull final Fields fields,
                    @NotNull final ServiceLocator serviceLocator) {
    this(convertToSimpleParameters(parameters), convertToSimpleParameters(ownParameters), href, null, fields, serviceLocator);
  }

  public Properties(@Nullable final Collection<Parameter> parameters,
                    @Nullable final Collection<Parameter> ownParameters,
                    @Nullable String href,
                    @NotNull final Fields fields,
                    @NotNull final ServiceLocator serviceLocator) {
    this(parameters, ownParameters, href, null, fields, serviceLocator);
  }

  public Properties(@Nullable final Collection<Parameter> parameters,
                    @Nullable final Collection<Parameter> ownParameters,
                    @Nullable String href,
                    @Nullable Locator propertiesLocator,
                    @NotNull final Fields fields,
                    @NotNull final ServiceLocator serviceLocator) {
    if (parameters == null) {
      this.count = null;
      this.properties = null;
    } else {
      if (fields.isIncluded(PROPERTY, false, true)) {
        this.properties = getEmptyProperties();
        final Fields propertyFields = fields.getNestedField(PROPERTY, Fields.NONE, Fields.LONG);
        final ParameterCondition parameterCondition = getParameterCondition(propertiesLocator != null ? propertiesLocator.getStringRepresentation() : fields.getLocator());
        Set<String> ownParameterNames = new HashSet<>();
        if (ownParameters != null) {
          for (Parameter parameter : ownParameters) {
            ownParameterNames.add(parameter.getName());
          }
        }
        for (Parameter parameter : parameters) {
          Boolean inherited = ownParameters == null ? null : !ownParameterNames.contains(parameter.getName());
          if (parameterCondition == null || parameterCondition.parameterMatches(parameter, inherited)) {
            this.properties.add(new Property(parameter, inherited, propertyFields, serviceLocator));
          }
        }
        this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), this.properties.size());  //count of the properties included
      } else {
        this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), parameters.size()); //actual count when no properties are included
      }
    }
    this.href = ValueWithDefault.decideDefault(fields.isIncluded("href"), href);
  }

  @NotNull
  private static SortedList<Property> getEmptyProperties() {
    return new SortedList<Property>(new Comparator<Property>() {
      private final CaseInsensitiveStringComparator comp = new CaseInsensitiveStringComparator();

      public int compare(final Property o1, final Property o2) {
        return comp.compare(o1.name, o2.name);
      }
    });
  }

  /**
   * Ignores any errors in the syntax: they will be logged but null will be returned as in the current usages it is already too late to report errors
   */
  @Nullable
  public static ParameterCondition getParameterCondition(@Nullable final String propertiesLocator) {
    if (propertiesLocator != null) {
      return ParameterCondition.create(propertiesLocator);
    }
    return null;
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public static List<Parameter> convertToSimpleParameters(@Nullable final Map<String, String> parametersMap) {
    if (parametersMap == null ) return null;
    ArrayList<Parameter> result = new ArrayList<>(parametersMap.size());
    for (Map.Entry<String, String> source : parametersMap.entrySet()) {
      if (source.getValue() == null)
        continue;
      result.add(new SimpleParameter(source.getKey(), source.getValue()));
    }
    return result;
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
      boolean actualOwn =  property.inherited == null || !property.inherited;
      if (FilterUtil.isIncludedByBooleanFilter(ownOnly, actualOwn)){
        property.isValid();//todo  check for unused type, inherited.
        result.put(property.name, property.value);
      }
    }
    return result;
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, href, properties);
  }

  public boolean setTo(@NotNull final InheritableUserParametersHolder holder, @NotNull final ServiceLocator serviceLocator) {
    return setTo(holder, holder.getOwnParametersCollection(), serviceLocator);
  }

  public boolean setTo(@NotNull final UserParametersHolder holder,
                       @Nullable final Collection<Parameter> ownParameters,
                       @NotNull final ServiceLocator serviceLocator) {
    Collection<Parameter> original = ownParameters != null ? ownParameters : holder.getParametersCollection();
    try {
      BuildTypeUtil.removeAllParameters(holder);
      if (properties != null) {
        for (Property entity : properties) {
          entity.addTo(holder, getNames(ownParameters), serviceLocator);
        }
      }
      return true;
    } catch (Exception e) {
      //restore all parameters if setting one has failed
      BuildTypeUtil.removeAllParameters(holder);
      for (Parameter p : original) {
        holder.addParameter(p);
      }
      throw new BadRequestException("Cannot set parameters: " + e.toString(), e);
    }
  }

  @Nullable
  private Set<String> getNames(@Nullable final Collection<Parameter> parameters) {
    if (parameters == null) return null;
    final HashSet<String> result = new HashSet<>(parameters.size());
    for (Parameter parameter : parameters) {
      result.add(parameter.getName());
    }
    return result;
  }

  public boolean isSimilar(final Properties that) {
    return that != null &&
          (count == null || that.count == null || com.google.common.base.Objects.equal(count, that.count)) &&
          (properties == null || that.properties == null || com.google.common.base.Objects.equal(properties, that.properties));
  }
}
