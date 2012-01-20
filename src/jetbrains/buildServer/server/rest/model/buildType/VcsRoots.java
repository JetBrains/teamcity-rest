/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.change.VcsRootRef;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-roots")
@XmlType(name = "vcs-roots")
public class VcsRoots {
  @XmlElement(name = "vcs-root")
  public List<VcsRootRef> vcsRoots;

  public VcsRoots() {
  }

  public VcsRoots(final Collection<jetbrains.buildServer.vcs.VcsRoot> serverVcsRoots, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    vcsRoots = new ArrayList<VcsRootRef>(serverVcsRoots.size());
    for (jetbrains.buildServer.vcs.VcsRoot root : serverVcsRoots) {
      vcsRoots.add(new VcsRootRef(root, apiUrlBuilder));
    }
  }
}
