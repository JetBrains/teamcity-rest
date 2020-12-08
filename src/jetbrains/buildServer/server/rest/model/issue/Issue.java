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

import javax.xml.bind.annotation.XmlAttribute;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents an issue related to the specific change."))
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
