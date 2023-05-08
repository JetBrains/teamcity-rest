/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.agent;


import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "agentType")
@ModelDescription(
  value = "Represents an agent type."
)
public class AgentType {
  private final ServiceLocator myServiceLocator;
  private final ApiUrlBuilder myApiUrlBuilder;

  private Properties myAvailableParameters;
  private Properties myConfigurationParameters;
  private Properties myBuildParameters;
  private Properties mySystemParameters;
  private Properties myEnvironmentParameters;

  public AgentType() {
    myServiceLocator = null;
    myApiUrlBuilder = null;
  }

  public AgentType(SAgentType agentType, Fields fields, ServiceLocator serviceLocator, ApiUrlBuilder apiUrlBuilder) {
    myServiceLocator = serviceLocator;
    myApiUrlBuilder = apiUrlBuilder;
    myAvailableParameters = ValueWithDefault.decideDefault(fields.isIncluded("availableParameters", false), () -> {
      return getProperties(agentType.getAvailableParameters(), fields.getNestedField("availableParameters"));
    });
    myConfigurationParameters = ValueWithDefault.decideDefault(fields.isIncluded("configurationParameters", false), () -> {
      return getProperties(agentType.getConfigurationParameters(), fields.getNestedField("configurationParameters"));
    });
    myBuildParameters = ValueWithDefault.decideDefault(fields.isIncluded("buildParameters", false), () -> {
      return getProperties(agentType.getBuildParameters(), fields.getNestedField("buildParameters"));
    });
    mySystemParameters = ValueWithDefault.decideDefault(fields.isIncluded("systemParameters", false), () -> {
      Map<String, String> systemParameters = agentType.getAvailableParameters()
                                                      .entrySet()
                                                      .stream()
                                                      .filter(entry -> SimpleParameter.isSystemProperty(entry.getKey()))
                                                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      return getProperties(systemParameters, fields.getNestedField("buildParameters"));
    });
    myEnvironmentParameters = ValueWithDefault.decideDefault(fields.isIncluded("systemParameters", false), () -> {
      Map<String, String> environmentParameters = agentType.getAvailableParameters()
                                                      .entrySet()
                                                      .stream()
                                                      .filter(entry -> SimpleParameter.isEnvironmentVariable(entry.getKey()))
                                                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      return getProperties(environmentParameters, fields.getNestedField("buildParameters"));
    });
  }

  @NotNull
  private Properties getProperties(Map<String, String> parameters, Fields fields) {
    return new Properties(parameters, null, fields, new BeanContext(myServiceLocator, myApiUrlBuilder));
  }

  @XmlElement
  public Properties getAvailableParameters() {
    return myAvailableParameters;
  }

  public void setAvailableParameters(Properties availableParameters) {
    myAvailableParameters = availableParameters;
  }

  @XmlElement
  public Properties getConfigurationParameters() {
    return myConfigurationParameters;
  }

  public void setConfigurationParameters(Properties configurationParameters) {
    myConfigurationParameters = configurationParameters;
  }

  @XmlElement
  public Properties getBuildParameters() {
    return myBuildParameters;
  }

  public void setBuildParameters(Properties buildParameters) {
    myBuildParameters = buildParameters;
  }

  @XmlElement
  public Properties getSystemParameters() {
    return mySystemParameters;
  }

  public void setSystemParameters(Properties systemParameters) {
    mySystemParameters = systemParameters;
  }

  @XmlElement
  public Properties getEnvironmentParameters() {
    return myEnvironmentParameters;
  }

  public void setEnvironmentParameters(Properties environmentParameters) {
    myEnvironmentParameters = environmentParameters;
  }
}
