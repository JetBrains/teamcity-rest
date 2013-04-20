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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.2009
 */
@XmlRootElement(name = "issues-ref")
@XmlType(name = "issues-ref")
public class IssueUsagesRef {
  @NotNull private SBuild myBuild;
  @NotNull private ApiUrlBuilder myApiUrlBuilder;

  public IssueUsagesRef() {
  }

  public IssueUsagesRef(@NotNull final SBuild build,
                        @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myBuild = build;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getBuildIssuesHref(myBuild);
  }
}