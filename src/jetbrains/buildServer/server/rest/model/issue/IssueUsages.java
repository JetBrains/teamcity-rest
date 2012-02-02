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

package jetbrains.buildServer.server.rest.model.issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.issueTracker.Issue;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.2009
 */
@XmlRootElement(name = "issuesUsages")
public class IssueUsages {
  @NotNull private Collection<Issue> myIssues;
  private SBuild myBuild;
  private ApiUrlBuilder myApiUrlBuilder;
  @Autowired private BeanFactory myFactory;

  public IssueUsages() {
  }

  public IssueUsages(@NotNull final Collection<Issue> issues, final SBuild build, final ApiUrlBuilder apiUrlBuilder, final BeanFactory myFactory) {
    myIssues = issues;
    myBuild = build;
    myApiUrlBuilder = apiUrlBuilder;
    myFactory.autowire(this);
  }

  @XmlElement(name = "issueUsage")
  public List<IssueUsage> getIssueUsages() {
    List<IssueUsage> result = new ArrayList<IssueUsage>(myIssues.size());
    for (Issue issue : myIssues) {
      result.add(new IssueUsage(issue, myBuild, myApiUrlBuilder, myFactory));
    }
    return result;
  }
}
