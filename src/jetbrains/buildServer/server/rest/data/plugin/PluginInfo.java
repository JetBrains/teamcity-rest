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

package jetbrains.buildServer.server.rest.data.plugin;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.Properties;
import jetbrains.buildServer.web.plugins.bean.ServerPluginInfo;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.2009
 */
@XmlRootElement(name = "plugin")
public class PluginInfo {
  ServerPluginInfo myPluginInfo;

  public PluginInfo() {
  }

  public PluginInfo(final ServerPluginInfo pluginInfo) {
    myPluginInfo = pluginInfo;
  }

  @XmlAttribute
  public String getName() {
    return myPluginInfo.getPluginName();
  }

  @XmlAttribute
  public String getVersion() {
    return myPluginInfo.getPluginVersion();
  }

  @XmlElement
  public Properties getParameters() {
    return new Properties(myPluginInfo.getPluginXml().getInfo().getParameters());
  }
}
