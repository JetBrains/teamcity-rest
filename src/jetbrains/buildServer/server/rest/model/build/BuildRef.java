/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlType(propOrder = {"webUrl", "href", "buildTypeId", "status", "percentageComplete", "running", "number", "id"})
@XmlRootElement(name = "build")
public class BuildRef {
  protected SBuild myBuild;
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;

  public BuildRef() {
  }

  public BuildRef(@NotNull final SBuild build, @NotNull final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuild = build;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
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
  public String getStatus() {
    return myBuild.getStatusDescriptor().getStatus().getText();
  }

  @XmlAttribute
  public String getBuildTypeId() {
    return myBuild.getBuildTypeId();
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuild);
  }

  @XmlAttribute
  public String getWebUrl() {
    return myDataProvider.getBuildUrl(myBuild);
  }

  @XmlAttribute
  public Boolean getRunning() {
    if (myBuild.isFinished()) {
      return null;
    }else{
      return true;
    }
  }

  @XmlAttribute
  public Integer getPercentageComplete() {
    if (myBuild.isFinished()) {
      return null;
    }
    SRunningBuild runningBuild = (SRunningBuild)myBuild;
    return runningBuild.getCompletedPercent();
  }
}
