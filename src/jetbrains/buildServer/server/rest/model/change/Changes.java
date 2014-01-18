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

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.ChangeFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "changes")
public class Changes {
  @Nullable private List<SVcsModification> myModifications;
  @Nullable private PagerData myPagerData;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;

  public Changes() {
  }

  public Changes(@Nullable final List<SVcsModification> modifications,
                 @Nullable final PagerData pagerData,
                 @NotNull Fields fields,
                 @NotNull final BeanContext beanContext) {
    myModifications = modifications;
    myPagerData = pagerData;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlElement(name = "change")
  public List<ChangeRef> getChanges() {
    return myModifications == null
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("change", false), new ValueWithDefault.Value<List<ChangeRef>>() {
             @Nullable
             public List<ChangeRef> get() {
               List<ChangeRef> changes = new ArrayList<ChangeRef>(myModifications.size());
               for (SVcsModification root : myModifications) {
                 changes.add(new ChangeRef(root, myBeanContext.getApiUrlBuilder(), myBeanContext.getSingletonService(BeanFactory.class)));
               }
               return changes;
             }
           });
  }

  @XmlAttribute
  public Integer getCount() {
    return myModifications == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("count"), myModifications.size());
  }

  @XmlAttribute(required = false)
  public String getHref() {
    return myPagerData == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("href"), myPagerData.getHref());
  }

  @XmlAttribute(required = false)
  @Nullable
  public String getNextHref() {
    return myPagerData == null || myPagerData.getNextHref() == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("nextHref"), myBeanContext.getApiUrlBuilder()
      .transformRelativePath(myPagerData.getNextHref()));
  }

  @XmlAttribute(required = false)
  @Nullable
  public String getPrevHref() {
    return myPagerData == null || myPagerData.getPrevHref() == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("prevHref"), myBeanContext.getApiUrlBuilder()
      .transformRelativePath(myPagerData.getPrevHref()));
  }

  private List<ChangeRef> sumbittedChanges;
  public void setChanges(List<ChangeRef> sumbittedChanges) {
    this.sumbittedChanges = sumbittedChanges;
  }

  @NotNull
  public List<SVcsModification> getChangesFromPosted(@NotNull final ChangeFinder singletonService) {
    final ArrayList<SVcsModification> result = new ArrayList<SVcsModification>();
    if (sumbittedChanges != null){
      for (ChangeRef changeRef : sumbittedChanges) {
        result.add(changeRef.getChangeFromPosted(singletonService));
      }
    }
    return result;
  }
}