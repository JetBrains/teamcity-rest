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

import java.text.SimpleDateFormat;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.serverSide.SBuild;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "build")
public class Build {
  @XmlAttribute
  public long id;
  @XmlAttribute
  public String number;
  @XmlAttribute
  public String status;
  @XmlElement
  public BuildTypeRef buildType;

  //todo: investigate common date formats approach in REST
  @XmlElement
  public String startDate;
  @XmlElement
  public String finishDate;

  public Build() {
  }

  public Build(SBuild build) {
    id = build.getBuildId();
    number = build.getBuildNumber();
    status = build.getStatusDescriptor().getStatus().getText();
    startDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getStartDate());
    finishDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getFinishDate());
    buildType = new BuildTypeRef(build.getBuildType());
  }
}

