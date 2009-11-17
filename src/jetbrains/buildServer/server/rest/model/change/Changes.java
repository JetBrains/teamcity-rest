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

package jetbrains.buildServer.server.rest.model.change;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "changes")
public class Changes {
  @XmlElement(name = "change")
  public List<ChangeRef> changes;

  @XmlAttribute
  public long count;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;
  private ApiUrlBuilder myApiUrlBuilder;

  public Changes() {
  }

  public Changes(final List<SVcsModification> modifications, final ApiUrlBuilder apiUrlBuilder) {
    myApiUrlBuilder = apiUrlBuilder;
    init(modifications, new PagerData());
  }

  public Changes(@NotNull final List<SVcsModification> modifications,
                 @NotNull final PagerData pagerData,
                 final ApiUrlBuilder apiUrlBuilder) {
    myApiUrlBuilder = apiUrlBuilder;
    init(modifications, pagerData);
  }

  private void init(final List<SVcsModification> modifications, final PagerData pagerData) {
    changes = new ArrayList<ChangeRef>(modifications.size());
    for (SVcsModification root : modifications) {
      changes.add(new ChangeRef(root, myApiUrlBuilder));
    }
    nextHref = pagerData.getNextHref() != null ? myApiUrlBuilder.transformRelativePath(pagerData.getNextHref()) : null;
    prevHref = pagerData.getPrevHref() != null ? myApiUrlBuilder.transformRelativePath(pagerData.getPrevHref()) : null;
    count = modifications.size();
  }
}