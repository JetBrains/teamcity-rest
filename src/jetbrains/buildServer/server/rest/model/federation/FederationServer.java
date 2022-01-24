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

package jetbrains.buildServer.server.rest.model.federation;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.federation.TeamCityServer;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;

@XmlRootElement(name = "federationServer")
@XmlType(propOrder = {"url", "name"})
public class FederationServer {

  @XmlAttribute public String url;
  @XmlAttribute public String name;

  public FederationServer() {
  }

  public FederationServer(final TeamCityServer source, final Fields fields) {
    url = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("url"), source.getUrl());
    name = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name"), source.getName());
  }

  public String getUrl() {
    return url;
  }

  public String getName() {
    return name;
  }
}
