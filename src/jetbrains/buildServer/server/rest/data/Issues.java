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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.issueTracker.Issue;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
@XmlRootElement(name = "issues")
public class Issues {
  @XmlElement(name = "issue")
  public List<jetbrains.buildServer.server.rest.data.Issue> issues;

  public Issues() {
  }

  public Issues(Collection<Issue> buildIssues) {
    issues = new ArrayList<jetbrains.buildServer.server.rest.data.Issue>(buildIssues.size());
    for (Issue buildIssue : buildIssues) {
      issues.add(new jetbrains.buildServer.server.rest.data.Issue(buildIssue));
    }
  }
}