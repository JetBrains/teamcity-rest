/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.issueTracker.Issue;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.2009
 */
@XmlRootElement(name = "issuesUsages")
public class IssueUsages extends IssueUsagesRef{
  @Nullable private Collection<Issue> myIssues;
  @NotNull private SBuild myBuild;
  @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Autowired @NotNull private BeanFactory myFactory;

  public IssueUsages() {
  }

  public IssueUsages(@NotNull final SBuild build, final boolean includeAllInline, @NotNull final ApiUrlBuilder apiUrlBuilder, @NotNull final BeanFactory myFactory) {
    super(build, apiUrlBuilder);
    myBuild = build;
    if (includeAllInline) {
      myIssues = myBuild.getRelatedIssues();
    }
    myApiUrlBuilder = apiUrlBuilder;
    myFactory.autowire(this);
  }

  @XmlAttribute
  public Long getCount() {
    return myIssues != null ? (long)myIssues.size() : null;
  }

  @XmlElement(name = "issueUsage")
  public List<IssueUsage> getIssueUsages() {
    if (myIssues == null) return null;

    List<IssueUsage> result = new ArrayList<IssueUsage>(myIssues.size());
    for (Issue issue : myIssues) {
      result.add(new IssueUsage(issue, myBuild, myApiUrlBuilder, myFactory));
    }
    return result;
  }
}
