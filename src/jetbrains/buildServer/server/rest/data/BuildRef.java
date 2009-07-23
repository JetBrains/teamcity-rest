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

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.server.rest.BuildRequest;
import jetbrains.buildServer.serverSide.SBuild;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class BuildRef {
  protected SBuild myBuild;

  public BuildRef() {
  }

  public BuildRef(SBuild build) {
    myBuild = build;
  }

  @XmlAttribute
  public long getId() {
    return myBuild.getBuildId();
  }

  @XmlAttribute
  public String getNumber() {
    return myBuild.getBuildNumber();
  }

  @XmlAttribute
  public String getHref() {
    return BuildRequest.getBuildHref(myBuild);
  }

  @XmlAttribute
  public String getBuildTypeName() {
    return myBuild.getBuildTypeName();
  }
}
