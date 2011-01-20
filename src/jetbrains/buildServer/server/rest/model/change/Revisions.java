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

package jetbrains.buildServer.server.rest.model.change;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.BuildRevision;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class Revisions {
  @XmlElement(name = "revision")
  public List<Revision> revisoins;

  public Revisions() {
  }

  public Revisions(final List<BuildRevision> buildRevisions, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    revisoins = new ArrayList<Revision>(buildRevisions.size());
    for (BuildRevision revision : buildRevisions) {
      revisoins.add(new Revision(revision, apiUrlBuilder));
    }
  }
}
