/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "changes")
public class Changes {
  @Autowired private BeanFactory myFactory;

  private List<SVcsModification> myModifications;
  private PagerData myPagerData;
  private ApiUrlBuilder myApiUrlBuilder;

  public Changes() {
  }

  public Changes(final List<SVcsModification> modifications, final ApiUrlBuilder apiUrlBuilder, final BeanFactory myFactory) {
    myModifications = modifications;
    myApiUrlBuilder = apiUrlBuilder;
    myPagerData = new PagerData();
    myFactory.autowire(this);
  }

  public Changes(@NotNull final List<SVcsModification> modifications,
                 @NotNull final PagerData pagerData,
                 final ApiUrlBuilder apiUrlBuilder, final BeanFactory myFactory) {
    myModifications = modifications;
    myPagerData = pagerData;
    myApiUrlBuilder = apiUrlBuilder;
    myFactory.autowire(this);
  }

  @XmlElement(name = "change")
  public List<ChangeRef> getChanges() {
    List<ChangeRef>changes = new ArrayList<ChangeRef>(myModifications.size());
    for (SVcsModification root : myModifications) {
      changes.add(new ChangeRef(root, myApiUrlBuilder, myFactory));
    }
    return changes;
  }

  @XmlAttribute
  public long getCount() {
    return myModifications.size();
  }

  @XmlAttribute(required = false)
  @Nullable
  public String getNextHref() {
    return myPagerData.getNextHref() != null ? myApiUrlBuilder.transformRelativePath(myPagerData.getNextHref()) : null;
  }

  @XmlAttribute(required = false)
  @Nullable
  public String getPrevHref() {
    return myPagerData.getPrevHref() != null ? myApiUrlBuilder.transformRelativePath(myPagerData.getPrevHref()) : null;
  }
}