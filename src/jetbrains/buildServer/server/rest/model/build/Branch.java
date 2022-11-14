/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.12
 */
@XmlRootElement(name = "branch")
@XmlType(name = "branch", propOrder = {"name", "internalName", "default", "unspecified", "active", "lastActivity",
  "groupFlag", /*experimental, temporary*/
  "builds"})
@ModelDescription(
    value = "Represents a branch on which this build has been started.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/working-with-feature-branches.html",
    externalArticleName = "Feature Branches"
)
public class Branch {
  private BranchData myBranch;
  private Fields myFields;
  private BeanContext myBeanContext;

  public Branch() {
  }

  public Branch(@NotNull BranchData branch, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myBranch = branch;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlAttribute(name = "name")
  public String getName(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("name"), () -> myBranch.getDisplayName());
  }

  /**
   * experimental support only
   * @return same as "name", but always "<default>" for the default branch: this is faster than calculating true name value
   */
  @XmlAttribute(name = "internalName")
  public String getInternalName(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("internalName", false, false), () -> myBranch.getName());
  }

  @XmlAttribute(name = "default")
  public Boolean isDefault(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("default"), () -> myBranch.isDefaultBranch());
  }

  @XmlAttribute(name = "unspecified")
  public Boolean isUnspecified(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("unspecified"), () -> myBranch.isUnspecifiedBranch());
  }

  @XmlAttribute(name = "active")
  public Boolean isActive() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("active", false), () -> myBranch.isActive());
  }

  @XmlAttribute(name = "lastActivity")
  public String getLastActivity() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("lastActivity", false), () -> Util.formatTime(myBranch.getActivityTimestamp()));
  }

  @XmlAttribute(name = "groupFlag")
  public Boolean getGroupFlag() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("groupFlag", false, false), () -> myBranch.isGroup());
  }

  /**
   * Experimental support only
   */
  @XmlElement(name = "builds")
  public Builds getBuilds() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("builds", false),
                                          () -> {
                                            String buildsHref = null;
                                            PagedSearchResult<BuildPromotion> builds = null;
                                            final Fields buildsFields = myFields.getNestedField("builds");
                                            final String buildsLocator = buildsFields.getLocator();
                                            if (buildsLocator != null) {
                                              builds = myBranch.getBuilds(buildsFields.getLocator());
                                              buildsHref = BuildRequest.getBuildsHref(myBranch, buildsLocator);
                                            } else {
                                              buildsHref = BuildRequest.getBuildsHref(myBranch, null);
                                            }
                                            if (builds == null && buildsHref == null) return null;
                                            return Builds.createFromBuildPromotions(builds == null ? null : builds.myEntries, buildsHref == null ? null : new PagerDataImpl(buildsHref), buildsFields, myBeanContext);
                                          });
  }
}
