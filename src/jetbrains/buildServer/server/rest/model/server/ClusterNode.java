/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Entity for cluster node information.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "cluster-node")
@XmlType
public class ClusterNode {

  @XmlElement(name = "id")
  public String id;

  @XmlElement(name = "url")
  public String url;

  @XmlElement(name = "online")
  public Boolean online;

  @XmlElement(name = "description")
  public String description;

  public ClusterNode() {
  }

  public ClusterNode(final String id, final String url, final Boolean online, final String description) {
    this.id = id;
    this.url = url;
    this.online = online;
    this.description = description;
  }
}
