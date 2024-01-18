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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Collection;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildCustomizationSettings;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.parameters.EntityWithParameters;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "buildCustomization")
@ModelDescription("Represents build customization settings of a trigger")
public class BuildTriggerCustomization implements DefaultValueAware {

  private boolean myDefault;

  @XmlAttribute
  public Boolean enforceCleanCheckout;

  @XmlAttribute
  public Boolean enforceCleanCheckoutForDependencies;

  @XmlElement
  public Properties parameters;

  public BuildTriggerCustomization() {
  }

  public BuildTriggerCustomization(@NotNull BuildTriggerDescriptor triggerDescriptor,
                                   @NotNull final Fields fields,
                                   @NotNull final BeanContext beanContext) {
    BuildCustomizationSettings buildCustomizationSettings = triggerDescriptor.getBuildCustomizationSettings();
    enforceCleanCheckout = ValueWithDefault.decideDefault(fields.isIncluded("enforceCleanCheckout"), buildCustomizationSettings.isEnforceCleanCheckout());
    enforceCleanCheckoutForDependencies = ValueWithDefault.decideDefault(fields.isIncluded("enforceCleanCheckoutForDependencies"), buildCustomizationSettings.isEnforceCleanCheckoutForDependencies());
    parameters = ValueWithDefault.decideDefault(fields.isIncluded("parameters"),
                                                new Properties(createParameters(buildCustomizationSettings), null, null, fields.getNestedField("parameters", Fields.NONE, Fields.LONG), beanContext));
    myDefault = ValueWithDefault.isAllDefault(enforceCleanCheckout, enforceCleanCheckoutForDependencies, parameters);
  }

  @NotNull
  public BuildCustomizationSettings toBuildCustomizationSettings(ServiceLocator serviceLocator) {
    BuildCustomizationSettings.Builder customizationSettings = new BuildCustomizationSettings.Builder();
    if (Boolean.TRUE.equals(enforceCleanCheckout)) {
      customizationSettings.enforceCleanCheckout();
    }
    if (Boolean.TRUE.equals(enforceCleanCheckoutForDependencies)) {
      customizationSettings.enforceCleanCheckoutForDependencies();
    }
    if (parameters != null && parameters.getProperties() != null) {
      for (Property parameter : parameters.getProperties()) {
        customizationSettings.withBuildParameter(parameter.getFromPosted(serviceLocator));
      }
    }
    return customizationSettings.build();
  }

  private static EntityWithParameters createParameters(@NotNull BuildCustomizationSettings buildCustomizationSettings) {
    return new EntityWithParameters() {
      @NotNull
      @Override
      public Collection<Parameter> getParametersCollection(@Nullable Locator locator) {
        return buildCustomizationSettings.getParameters();
      }

      @Nullable
      @Override
      public Parameter getParameter(@NotNull String paramName) {
        return buildCustomizationSettings.getParameters().stream().filter(p -> paramName.equals(p.getName())).findAny().orElse(null);
      }
    };
  };

  @Override
  public boolean isDefault() {
    return myDefault;
  }
}