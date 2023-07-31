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
import javax.xml.bind.annotation.XmlAttribute;
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

  private Integer myId;
  private String myName;
  private Boolean myIsCloud;
  private Environment myEnvironment;
  private Properties myAvailableParameters;
  private Properties myConfigurationParameters;
  private Properties myBuildParameters;
  private Properties mySystemParameters;
  private Properties myEnvironmentParameters;

  public AgentType() {
    myServiceLocator = null;
    myApiUrlBuilder = null;
  }

  public AgentType(@NotNull SAgentType agentType, @NotNull Fields fields, @NotNull ServiceLocator serviceLocator, @NotNull ApiUrlBuilder apiUrlBuilder) {
    myServiceLocator = serviceLocator;
    myApiUrlBuilder = apiUrlBuilder;

    myId = ValueWithDefault.decideDefault(
      fields.isIncluded("id", true, true),
      () -> agentType.getAgentTypeId()
    );
    myName = ValueWithDefault.decideDefault(
      fields.isIncluded("name", true, true),
      () -> agentType.getDetails().getName()
    );
    myIsCloud = ValueWithDefault.decideDefault(
      fields.isIncluded("isCloud", true, true),
      () -> agentType.isCloud()
    );
    myEnvironment = ValueWithDefault.decideDefault(
      fields.isIncluded("environment", false),
      () -> new Environment(agentType, fields.getNestedField("environment"))
    );

    myAvailableParameters = ValueWithDefault.decideDefault(
      fields.isIncluded("availableParameters", false),
      () -> resolveProperties(agentType.getAvailableParameters(), fields.getNestedField("availableParameters"))
    );
    myConfigurationParameters = ValueWithDefault.decideDefault(
      fields.isIncluded("configurationParameters", false),
      () -> resolveProperties(agentType.getConfigurationParameters(), fields.getNestedField("configurationParameters"))
    );
    myBuildParameters = ValueWithDefault.decideDefault(
      fields.isIncluded("buildParameters", false),
      () -> resolveProperties(agentType.getBuildParameters(), fields.getNestedField("buildParameters"))
    );
    mySystemParameters = ValueWithDefault.decideDefault(
      fields.isIncluded("systemParameters", false),
      () -> resolveSystemParameters(agentType, fields.getNestedField("systemParameters"))
    );
    myEnvironmentParameters = ValueWithDefault.decideDefault(
      fields.isIncluded("environmentParameters", false),
      () -> resolveEnvironmentParameters(agentType, fields.getNestedField("environmentParameters"))
    );
  }

  @XmlAttribute(name = "id")
  public Integer getId() {
    return myId;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myName;
  }

  @XmlAttribute(name = "isCloud")
  public Boolean getCloud() {
    return myIsCloud;
  }

  @XmlElement(name = "environment")
  public Environment getEnvironment() {
    return myEnvironment;
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

  @NotNull
  private Properties resolveEnvironmentParameters(@NotNull SAgentType agentType, @NotNull Fields fields) {
    Map<String, String> environmentParameters = agentType.getAvailableParameters()
                                                         .entrySet()
                                                         .stream()
                                                         .filter(entry -> SimpleParameter.isEnvironmentVariable(entry.getKey()))
                                                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return resolveProperties(environmentParameters, fields);
  }

  @NotNull
  private Properties resolveSystemParameters(@NotNull SAgentType agentType, @NotNull Fields fields) {
    Map<String, String> systemParameters = agentType.getAvailableParameters()
                                                    .entrySet()
                                                    .stream()
                                                    .filter(entry -> SimpleParameter.isSystemProperty(entry.getKey()))
                                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return resolveProperties(systemParameters, fields);
  }

  @NotNull
  private Properties resolveProperties(Map<String, String> parameters, Fields fields) {
    return new Properties(parameters, null, fields, new BeanContext(myServiceLocator, myApiUrlBuilder));
  }
}
