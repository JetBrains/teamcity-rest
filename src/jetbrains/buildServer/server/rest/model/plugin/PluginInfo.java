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

package jetbrains.buildServer.server.rest.model.plugin;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.2009
 */
@XmlRootElement(name = "plugin")
@XmlType(propOrder = {"name", "displayName", "version", "loadPath",
  "parameters"})
public class PluginInfo {
  private ServerPluginInfo myPluginInfo;
  private Fields myFields;
  private ServiceLocator myServiceLocator;

  public PluginInfo() {
  }

  public PluginInfo(final ServerPluginInfo pluginInfo, @NotNull Fields fields, @NotNull final ServiceLocator serviceLocator) {
    myPluginInfo = pluginInfo;
    myFields = fields;
    myServiceLocator = serviceLocator;
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("name"), myPluginInfo.getPluginName());
  }

  @XmlAttribute
  public String getDisplayName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("displayName"), myPluginInfo.getPluginXml().getInfo().getDisplayName());
  }

  @XmlAttribute
  public String getVersion() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("version"), myPluginInfo.getPluginVersion());
  }

  @XmlAttribute
  public String getLoadPath() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("loadPath"), myPluginInfo.getPluginRoot().getAbsolutePath());
  }

  @XmlElement
  public Properties getParameters() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("parameters"),
                                          new Properties(myPluginInfo.getPluginXml().getInfo().getParameters(),
                                                         null,
                                                         myFields.getNestedField("parameters", Fields.NONE, Fields.LONG), myServiceLocator));
  }
}
