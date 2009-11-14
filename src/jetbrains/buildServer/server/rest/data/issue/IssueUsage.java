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

package jetbrains.buildServer.server.rest.data.issue;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.change.Changes;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.vcs.SVcsModification;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.2009
 */
public class IssueUsage {
  private jetbrains.buildServer.issueTracker.Issue myIssue;
  private SBuild myBuild;
  private ApiUrlBuilder myApiUrlBuilder;

  public IssueUsage() {
  }

  public IssueUsage(jetbrains.buildServer.issueTracker.Issue issue, SBuild build, final ApiUrlBuilder apiUrlBuilder) {
    myIssue = issue;
    myBuild = build;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlElement
  public Issue getIssue() {
    return new Issue(myIssue);
  }

  @XmlElement(defaultValue = "")
  public Changes getChanges() {
    final List<SVcsModification> relatedModifications = new ArrayList<SVcsModification>();
    final List<SVcsModification> vcsModifications = myBuild.getContainingChanges();
    for (SVcsModification vcsModification : vcsModifications) {
      if (vcsModification.getRelatedIssues().contains(myIssue)) {
        relatedModifications.add(vcsModification);
      }
    }
    return relatedModifications.isEmpty() ? null : new Changes(relatedModifications, myApiUrlBuilder);
  }
}
