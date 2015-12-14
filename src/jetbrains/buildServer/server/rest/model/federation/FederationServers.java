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

package jetbrains.buildServer.server.rest.model.federation;


import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.federation.TeamCityServer;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "servers")
public class FederationServers {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "server")
  public List<FederationServer> servers;

  public FederationServers() {
  }

  public FederationServers(final Iterable<TeamCityServer> servers) {
    this.servers = CollectionsUtil.convertCollection(servers, new Converter<FederationServer, TeamCityServer>() {
      public FederationServer createFrom(@NotNull final TeamCityServer source) {
        return new FederationServer(source);
      }
    });
  }
}
