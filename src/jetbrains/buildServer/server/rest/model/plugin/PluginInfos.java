/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.server.rest.util.ValueWithDefault.decideDefault;

/**
 * Created by IntelliJ IDEA.
 * User: Yegor.Yarko
 * Date: 02.02.2010
 * Time: 21:00:13
 * To change this template use File | Settings | File Templates.
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "plugins")
@ModelBaseType(ObjectType.LIST)
public class PluginInfos {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "plugin")
  public List<jetbrains.buildServer.server.rest.model.plugin.PluginInfo> plugins;

  public PluginInfos() {
  }

  public PluginInfos(final Collection<ServerPluginInfo> pluginInfos, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    plugins = decideDefault(
      fields.isIncluded("plugin", true),
      () -> CollectionsUtil.convertCollection(
        pluginInfos,
        source -> new PluginInfo(
          source,
          fields.getNestedField("plugin", Fields.SHORT, Fields.LONG),
          beanContext
        )
      )
    );
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), pluginInfos.size());
  }
}
