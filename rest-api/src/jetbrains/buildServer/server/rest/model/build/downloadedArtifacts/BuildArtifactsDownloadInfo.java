/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build.downloadedArtifacts;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ArtifactInfo;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "downloadInfo")
@ModelBaseType(ObjectType.LIST)
@ModelDescription("List of metadata on artifacts downloaded from [or provided by] a build.")
public class BuildArtifactsDownloadInfo {
  private Fields myFields;
  private BeanContext myBeanContext;
  private SBuild myBuild;
  private List<ArtifactInfo> myArtifacts;

  public BuildArtifactsDownloadInfo() { }

  public BuildArtifactsDownloadInfo(@NotNull SBuild build, @NotNull List<ArtifactInfo> buildArtifacts, @NotNull Fields fields, @NotNull BeanContext context) {
    myFields = fields;
    myBeanContext = context;
    myBuild = build;
    myArtifacts = buildArtifacts;
  }

  @XmlElement(name = "build")
  public Build getBuild() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("build"),
      () -> new Build(myBuild, myFields.getNestedField("build"), myBeanContext)
    );
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count"), myArtifacts.size());
  }

  @XmlElement(name = "artifactInfo")
  public List<ArtifactDownloadInfo> getArtifactInfo() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("artifactInfo"),
      () -> {
        List<ArtifactDownloadInfo> result = new ArrayList<>(myArtifacts.size());
        Fields inner = myFields.getNestedField("artifactInfo");
        for(ArtifactInfo info : myArtifacts) {
          result.add(new ArtifactDownloadInfo(info, inner));
        }

        return result;
      }
    );
  }
}