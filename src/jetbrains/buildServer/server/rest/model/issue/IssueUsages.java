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

package jetbrains.buildServer.server.rest.model.issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.issueTracker.Issue;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.2009
 */
@XmlRootElement(name = "issuesUsages")
@ModelBaseType(ObjectType.LIST)
public class IssueUsages{
  @Nullable private Collection<Issue> myIssues;
  @NotNull private SBuild myBuild;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;

  public IssueUsages() {
  }

  public IssueUsages(@NotNull final SBuild build, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myBuild = build;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href", true), () -> myBeanContext.getApiUrlBuilder().getBuildIssuesHref(myBuild));
  }

  @XmlAttribute
  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count", false), new ValueWithDefault.Value<Integer>() {
      @Nullable
      public Integer get() {
        return getRelatedIssues().size();
      }
    });
  }

  @XmlElement(name = "issueUsage")
  public List<IssueUsage> getIssueUsages() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("issueUsage", false), new ValueWithDefault.Value<List<IssueUsage>>() {
             @Nullable
             public List<IssueUsage> get() {
               Collection<Issue> issues = getRelatedIssues();
               List<IssueUsage> result = new ArrayList<IssueUsage>(issues.size());
               for (Issue issue : issues) {
                 result.add(new IssueUsage(issue, myBuild, myFields.getNestedField("issueUsage", Fields.NONE, Fields.LONG), myBeanContext));
               }
               return result;
             }
           });
  }

  @NotNull
  public Collection<Issue> getRelatedIssues() {
    if (myIssues == null){
      myIssues = myBuild.getRelatedIssues();
    }
    return myIssues;
  }
}
