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

package jetbrains.buildServer.server.rest.model;

import com.google.common.base.Objects;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.parameters.EntityWithModifiableParameters;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ControlDescription;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.parameters.ParameterDescriptionFactory;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.StringUtil;
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
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a name-value-type relation."))
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
    value = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("value", true, true), () -> getParameterValue(parameter, serviceLocator));
    this.inherited = ValueWithDefault.decideDefault(fields.isIncluded("inherited"), inherited);
    final ControlDescription parameterSpec = parameter.getControlDescription();
    if (parameterSpec != null) {
      type = ValueWithDefault
        .decideDefault(fields.isIncluded("type", false, true), new ParameterType(parameterSpec, fields.getNestedField("type", Fields.NONE, Fields.LONG), serviceLocator));
    }
  }

  /**
   * @return value of a regular parameter, secure value for the secure parameter, null if the value is secure and cannot be seen by the current user
   */
  @Nullable
  public static String getParameterValue(final Parameter parameter, final ServiceLocator serviceLocator) {
    if (!isSecure(parameter, serviceLocator)) {
      return parameter.getValue();
    }

    String secureValue = getSecureValue(parameter, serviceLocator);
    if (getAllowedValues().contains(secureValue) || includeSecureProperties(serviceLocator)) {
      return secureValue;
    }
    return null;
  }

  public static boolean isSecure(@NotNull final Parameter parameter, @NotNull final ServiceLocator serviceLocator) {
    if (serviceLocator.getSingletonService(ParameterFactory.class).isSecureParameter(parameter.getControlDescription())) {
      return true;
    }
    return isPropertyToExclude(parameter.getName(), parameter.getValue(), serviceLocator);
  }

  public static boolean isPropertyToExclude(@NotNull final String key, @Nullable final String value, final @NotNull ServiceLocator serviceLocator) {
    //TeamCity API question: or should jetbrains.buildServer.agent.Constants.SECURE_PROPERTY_PREFIX be used here?
    if (key.startsWith(SVcsRoot.SECURE_PROPERTY_PREFIX)) {
      return !getAllowedValues().contains(value);
    }
    return false;
  }

  @Nullable
  private static String getSecureValue(final @NotNull Parameter parameter, final @NotNull ServiceLocator serviceLocator) {
    final ParameterFactory parameterFactory = serviceLocator.getSingletonService(ParameterFactory.class);
    return parameterFactory.getRawValue(parameter);
  }

  public static boolean includeSecureProperties(final @NotNull ServiceLocator serviceLocator) {
    //noinspection ConstantConditions
    return TeamCityProperties.getBoolean("rest.listSecureProperties") &&
           serviceLocator.findSingletonService(PermissionChecker.class).isPermissionGranted(Permission.EDIT_PROJECT, null);
  }

  @NotNull
  private static List<String> getAllowedValues() {
    String valuesLocatorText = TeamCityProperties.getPropertyOrNull("rest.listSecureProperties.valuesLocator", "password:()"); //allow empty values by default
    try {
      if (!StringUtil.isEmpty(valuesLocatorText)) {
        Locator valuesLocator = new Locator(valuesLocatorText, "password", "enabled");
        Boolean enabled = valuesLocator.getSingleDimensionValueAsBoolean("enabled", true);
        if (enabled != null && !enabled) {
          return Collections.emptyList();
        }
        List<String> values = valuesLocator.getDimensionValue("password");
        valuesLocator.checkLocatorFullyProcessed();
        return values;
      }
    } catch (LocatorProcessException e) {
      throw new InvalidStateException("Wrong '" + "rest.listSecureProperties.valuesLocator" +
                                      "' server internal property value, remove it or use format 'password:(),password:(sample)', error: " + e.getMessage());
    }
    return Collections.emptyList();
  }

  @NotNull
  public Parameter getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    isValid();
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

  public void isValid() {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    if (value == null) {
      throw new BadRequestException("Parameter value for the parameter '" + name + "' should be specified (can be \"\").");
    }
  }

  public static Property createFrom(@Nullable final String parameterName,
                                    @NotNull final ParametersPersistableEntity entity,
                                    @NotNull final Fields fields,
                                    @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    final Parameter parameter = entity.getParameter(parameterName);
    if (parameter == null) {
      throw new NotFoundException("No parameter with name '" + parameterName + "' is found.");
    }
    return new Property(parameter, entity.isInherited(parameterName), fields, serviceLocator);
  }


  @NotNull
  public Parameter addTo(@NotNull final EntityWithModifiableParameters entity, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    Parameter originalParameter = entity.getParameter(name);
    Parameter originalOwnParameter = entity.getOwnParameter(name);

    if (inherited != null && inherited) {
      if (originalParameter != null && isSimilar(originalParameter, serviceLocator)) return originalParameter;
    }

    Parameter fromPosted = getFromPosted(serviceLocator);
    try {
      entity.addParameter(fromPosted);
      return fromPosted;
    } catch (Exception e) {
      //restore
      if (originalOwnParameter != null) {
        entity.addParameter(originalOwnParameter);
      } else if (entity.getParameter(name) != null) {
        entity.removeParameter(name);
      }
      throw new BadRequestException("Cannot set parameter '" + name + "' to value '" + fromPosted.getValue() + "': " + e.toString(), e);
    }
  }

  public boolean isSimilar(final Parameter parameter, final ServiceLocator serviceLocator) {
    if (parameter == null) {
      return false;
    }
    if (!Objects.equal(name, parameter.getName())) {
      return false;
    }
    ControlDescription controlDescription = parameter.getControlDescription();
    if (inherited == null || !inherited) {
      if (!Objects.equal(type, controlDescription == null ? null : new ParameterType(controlDescription, Fields.LONG, serviceLocator))) {
        return false;
      }
      if (!isSecure(parameter, serviceLocator)) {
        if (!Objects.equal(value, parameter.getValue())) {
          return false;
        }
      } else {
        if (!Objects.equal(value, getSecureValue(parameter, serviceLocator))) {
          return false;
        }
      }
    } else {
      // allow to omit type and value for inherited parameters
      if (type != null && !Objects.equal(type, controlDescription == null ? null : new ParameterType(controlDescription, Fields.LONG, serviceLocator))) {
        return false;
      }
      if (value != null) {
        if (!isSecure(parameter, serviceLocator)) {
          if (!Objects.equal(value, parameter.getValue())) {
            return false;
          }
        } else {
          if (!Objects.equal(value, getSecureValue(parameter, serviceLocator))) {
            return false;
          }
        }
      }
    }
    return true;
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

