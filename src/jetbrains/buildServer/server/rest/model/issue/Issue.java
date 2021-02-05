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

import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
@ModelDescription("Represents an issue related to the specific change.")
public class Issue {
  @NotNull protected jetbrains.buildServer.issueTracker.Issue myIssue;

  public Issue() {
  }

  public Issue(@NotNull final jetbrains.buildServer.issueTracker.Issue issue) {
    myIssue = issue;
  }

  @XmlAttribute
  public String getId() {
    return myIssue.getId();
  }

  @XmlAttribute
  public String getUrl() {
    return myIssue.getUrl();
  }
}
