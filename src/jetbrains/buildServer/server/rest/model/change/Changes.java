/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "changes")
public class Changes implements DefaultValueAware {
  @Nullable private CachingValue<List<SVcsModification>> myModifications;
  @Nullable private PagerData myPagerData;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;

  public static final String CHANGE = "change";
  public static final String COUNT = "count";

  public Changes() {
  }

  public Changes(@Nullable final List<SVcsModification> modifications,
                 @Nullable final PagerData pagerData,
                 @NotNull Fields fields,
                 @NotNull final BeanContext beanContext) {
    myModifications = modifications == null ? null : new CachingValue<List<SVcsModification>>() {
      @Override
      protected List<SVcsModification> doGet() {
        return modifications;
      }
    };
    myPagerData = pagerData;
    myFields = fields;
    myBeanContext = beanContext;
  }

  public Changes(@Nullable final PagerData pagerData,
                 @NotNull Fields fields,
                 @NotNull final BeanContext beanContext,
                 @Nullable final CachingValue<List<SVcsModification>> modifications) {
    myModifications = modifications;
    myPagerData = pagerData;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlElement(name = CHANGE)
  public List<Change> getChanges() {
    return myModifications == null
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded(CHANGE, false), new ValueWithDefault.Value<List<Change>>() {
             @Nullable
             public List<Change> get() {
               List<Change> changes = new ArrayList<Change>(myModifications.get().size());
               for (SVcsModification root : myModifications.get()) {
                 changes.add(new Change(root, myFields.getNestedField(CHANGE), myBeanContext));
               }
               return changes;
             }
           });
  }

  @XmlAttribute
  public Integer getCount() {
    if (myModifications == null) return null;

    final Boolean countRequested = myFields.isIncluded(COUNT);
    if (countRequested != null) {
      if (countRequested) {
        return myModifications.get().size();
      } else {
        return null;
      }
    }

    //for performance reasons: include count only when changes are to be calculated
    final Boolean changesAreCalculated = myFields.isIncluded(CHANGE, false);
    if (changesAreCalculated == null || changesAreCalculated) {
      return myModifications.get().size();
    }
    return null;
  }

  @XmlAttribute(required = false)
  public String getHref() {
    return myPagerData == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("href"),
                                                                       myBeanContext.getApiUrlBuilder().transformRelativePath(myPagerData.getHref()));
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

  private List<Change> sumbittedChanges;
  public void setChanges(List<Change> sumbittedChanges) {
    this.sumbittedChanges = sumbittedChanges;
  }

  @NotNull
  public List<SVcsModification> getChangesFromPosted(@NotNull final ChangeFinder singletonService) {
    final ArrayList<SVcsModification> result = new ArrayList<SVcsModification>();
    if (sumbittedChanges != null){
      for (Change change : sumbittedChanges) {
        result.add(change.getChangeFromPosted(singletonService));
      }
    }
    return result;
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(myModifications == null ? null : myModifications.get().size(), myPagerData);
  }
}