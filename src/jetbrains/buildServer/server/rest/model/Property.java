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

import com.google.common.base.Objects;
import java.text.ParseException;
import java.util.Collection;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.request.ParametersSubResource;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.parameters.ParameterDescriptionFactory;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.serverSide.parameters.types.PasswordType;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "property")
@XmlType(name = "property", propOrder = {"name", "value", "inherited",
  "type"})
public class Property {
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String value;
  @XmlAttribute
  public Boolean inherited;
  @XmlElement
  public ParameterType type;

  public Property() {
  }

  public Property(@NotNull final Parameter parameter, @Nullable final Boolean inherited, @NotNull final Fields fields, @NotNull final ServiceLocator serviceLocator) {
    name = !fields.isIncluded("name", true, true) ? null : parameter.getName();
    if (!isSecure(parameter, serviceLocator)) {
      value = !fields.isIncluded("value", true, true) ? null : parameter.getValue();
    }
    this.inherited = ValueWithDefault.decideDefault(fields.isIncluded("inherited"), inherited);
    final ControlDescription parameterSpec = parameter.getControlDescription();
    if (parameterSpec != null) {
      type = ValueWithDefault
        .decideDefault(fields.isIncluded("type", false, true), new ParameterType(parameterSpec, fields.getNestedField("type", Fields.NONE, Fields.LONG), serviceLocator));
    }
  }

  public static boolean isSecure(@NotNull final Parameter parameter, @NotNull final ServiceLocator serviceLocator) {
    if (serviceLocator.getSingletonService(PasswordType.class).isPassword(parameter.getControlDescription()) && !TeamCityProperties.getBoolean("rest.listSecureProperties")) {
      return true;
    }
    return isPropertyToExclude(parameter.getName());
  }

  public static boolean isPropertyToExclude(@NotNull final String key) {
    //TeamCity API question: or should jetbrains.buildServer.agent.Constants.SECURE_PROPERTY_PREFIX be used here?
    return key.startsWith(SVcsRoot.SECURE_PROPERTY_PREFIX) && !TeamCityProperties.getBoolean("rest.listSecureProperties");
  }

  @NotNull
  public Parameter getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    if (value == null) {
      throw new BadRequestException("Parameter value for the parameter '" + name + "' should be specified (can be \"\").");
    }
    final ParameterFactory parameterFactory = serviceLocator.getSingletonService(ParameterFactory.class);
    if (type == null || type.rawValue == null) {
      return parameterFactory.createSimpleParameter(name, value);
    }
    //workaround for https://youtrack.jetbrains.com/issue/TW-41041
    final ParameterDescriptionFactory parameterDescriptionFactory =
      serviceLocator.getSingletonService(jetbrains.buildServer.serverSide.parameters.ParameterDescriptionFactory.class);
    try {
      parameterDescriptionFactory.parseDescription(type.rawValue);
    } catch (ParseException e) {
      throw new BadRequestException("Wrong parameter type \"" + type.rawValue + "\" provided: " + e.getMessage());
    }

    return parameterFactory.createTypedParameter(name, value, type.rawValue);
  }

  public static Property createFrom(@Nullable final String parameterName,
                                    @NotNull final ParametersSubResource.EntityWithParameters entity,
                                    @NotNull final Fields fields,
                                    @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    final Parameter parameter = CollectionsUtil.findFirst(entity.getParametersCollection(), new Filter<Parameter>() {
      public boolean accept(@NotNull final Parameter data) {
        return parameterName.equals(data.getName());
      }
    });
    if (parameter == null) {
      throw new NotFoundException("No parameter with name '" + parameterName + "' is found.");
    }
    return new Property(parameter, entity.isInherited(parameterName), fields, serviceLocator);
  }

  @NotNull
  public Parameter addTo(@NotNull final InheritableUserParametersHolder entity, @NotNull final ServiceLocator serviceLocator){
    Collection<Parameter> original = entity.getOwnParametersCollection();
    try {
      return addToInternal(entity, serviceLocator);
    } catch (Exception e) {
      //restore
      BuildTypeUtil.removeAllParameters(entity);
      for (Parameter p : original) {
        entity.addParameter(p);
      }
      throw new BadRequestException("Cannot set parameters: " + e.toString(), e);
    }
  }

  @NotNull
  public Parameter addToInternal(final @NotNull UserParametersHolder entity, final @NotNull ServiceLocator serviceLocator) {
    Parameter fromPosted = getFromPosted(serviceLocator);

    if (inherited != null && inherited) {
      Parameter similar = entity.getParameter(name);
      if (similar != null && isSimilar(new Property(similar, inherited, Fields.LONG, serviceLocator))) return similar;
    }

    entity.addParameter(fromPosted);
    return fromPosted;
  }

  public boolean isSimilar(final Property that) {
    return that != null &&
           Objects.equal(name, that.name) &&
           Objects.equal(value, that.value) &&
           Objects.equal(type, that.type);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Property property = (Property)o;
    return Objects.equal(name, property.name) &&
           Objects.equal(value, property.value) &&
           Objects.equal(inherited, property.inherited) &&
           Objects.equal(type, property.type);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, value, inherited, type);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Property{");
    sb.append("name='").append(name).append('\'');
    sb.append(", value='").append(value).append('\'');
    sb.append(", inherited=").append(inherited);
    sb.append(", type=").append(type);
    sb.append('}');
    return sb.toString();
  }
}

