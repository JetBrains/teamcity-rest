/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "vcs-root-instance-ref")
@XmlType(name = "vcs-root-instance-ref", propOrder = {"id", "vcsRootId", "vcsRootInternalId", "name", "href"})
public class VcsRootInstanceRef {
  @XmlAttribute
  public String id;
  @XmlAttribute(name = "vcs-root-id")
  public String vcsRootId;
  @XmlAttribute(name = "vcsRootInternalId")
  public String vcsRootInternalId;
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String href;

  public VcsRootInstanceRef() {
  }

  public VcsRootInstanceRef(jetbrains.buildServer.vcs.VcsRootInstance root, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this.id = String.valueOf(root.getId());
    this.vcsRootId = root.getParent().getExternalId();
    this.vcsRootInternalId = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? String.valueOf(root.getParentId()) : null;
    this.name = root.getName();
    this.href = apiUrlBuilder.getHref(root);
  }
}
