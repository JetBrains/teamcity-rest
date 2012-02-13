/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlType(name = "revision")
public class Revision {
  @XmlAttribute(name = "version")
  public String displayRevision;
  @XmlAttribute(name = "internalVersion")
  public String internalRevision;

  @XmlElement(name = "vcs-root-instance")
  public VcsRootInstanceRef vcsRoot;

  public Revision() {
  }

  public Revision(BuildRevision revision, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    displayRevision = revision.getRevisionDisplayName();
    vcsRoot = new VcsRootInstanceRef(revision.getRoot(), apiUrlBuilder);
    internalRevision = TeamCityProperties.getBoolean("rest.internalMode") ? revision.getRevision() : null;
  }
}
