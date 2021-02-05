/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.data.ChangeFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "changes")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = ObjectType.LIST)
@ModelBaseType(ObjectType.PAGINATED)
public class Changes implements DefaultValueAware {
  @Nullable private CachingValue<List<SVcsModification>> myModifications;
  @Nullable private PagerData myPagerData;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;

  public static final String CHANGE = "change";
  public static final String COUNT = "count";

  private List<Change> myChanges;
  private Integer myCount;

  public Changes() {
  }

  public Changes(@Nullable final List<SVcsModification> modifications,
                 @Nullable final PagerData pagerData,
                 @NotNull Fields fields,
                 @NotNull final BeanContext beanContext) {
    this(pagerData, fields, beanContext, modifications == null ? null : CachingValue.simple(modifications));
  }

  public Changes(@Nullable final PagerData pagerData,
                 @NotNull Fields fields,
                 @NotNull final BeanContext beanContext,
                 @Nullable final CachingValue<List<SVcsModification>> modifications) {
    myModifications = modifications;
    myPagerData = pagerData;
    myFields = fields;
    myBeanContext = beanContext;

    if (myModifications != null) {
      myChanges = ValueWithDefault.decideDefault(myFields.isIncluded(CHANGE, myModifications.isCached(), false, null),
                                                 () -> myModifications.get().stream().map(root -> new Change(root, myFields.getNestedField(CHANGE), myBeanContext))
                                                                 .collect(Collectors.toList()));

      //for performance reasons: include count only when changes are to be calculated
      myCount = ValueWithDefault.decideIncludeByDefault(myFields.isIncluded(COUNT, myModifications.isCached(), myModifications.isCached(), myModifications.isCached()),
                                                     () -> myModifications.get().size());
    }
  }

  @XmlElement(name = CHANGE)
  public List<Change> getChanges() {
    return myChanges;
  }

  @XmlAttribute
  public Integer getCount() {
    return myCount;
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