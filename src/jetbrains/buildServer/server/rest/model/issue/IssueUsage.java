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

package jetbrains.buildServer.server.rest.model.issue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.issueTracker.IssueEx;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.SVcsModification;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.2009
 */
public class IssueUsage {
  private jetbrains.buildServer.issueTracker.Issue myIssue;
  private SBuild myBuild;
  private ApiUrlBuilder myApiUrlBuilder;
  @Autowired private BeanFactory myFactory;

  public IssueUsage() {
  }

  public IssueUsage(jetbrains.buildServer.issueTracker.Issue issue,
                    SBuild build,
                    final ApiUrlBuilder apiUrlBuilder,
                    final BeanFactory myFactory) {
    myIssue = issue;
    myBuild = build;
    myApiUrlBuilder = apiUrlBuilder;
    myFactory.autowire(this);
  }

  @XmlElement
  public Issue getIssue() {
    return new Issue(myIssue);
  }

  @XmlElement(defaultValue = "")
  public Changes getChanges() {
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.issueUsage.useStoredVcsModification")) {
      final IssueEx issueEx; //todo: TeamCity API
      issueEx = (IssueEx)myIssue;
      if (issueEx != null) {
        final SVcsModification relatedModification = (SVcsModification)issueEx.getRelatedModification();
        if (relatedModification != null) {
          return new Changes(Collections.singletonList(relatedModification), myApiUrlBuilder, myFactory);
        }
      }
      return null;
    } else {
      // this is highly inefficient especially when serving /relatedIssues for a build with large number of changes
      final List<SVcsModification> relatedModifications = new ArrayList<SVcsModification>();
      final List<SVcsModification> vcsModifications = myBuild.getContainingChanges();
      for (SVcsModification vcsModification : vcsModifications) {
        if (vcsModification.getRelatedIssues().contains(myIssue)) {
          relatedModifications.add(vcsModification);
        }
      }
      return relatedModifications.isEmpty() ? null : new Changes(relatedModifications, myApiUrlBuilder, myFactory);
    }
  }
}
