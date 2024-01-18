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

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ArtifactInfo;
import org.jetbrains.annotations.NotNull;

public class ArtifactDownloadInfo {
  private ArtifactInfo myInfo;
  private Fields myFields;

  public ArtifactDownloadInfo() {}

  public ArtifactDownloadInfo(@NotNull ArtifactInfo info, @NotNull Fields fields) {
    myInfo = info;
    myFields = fields;
  }

  @XmlAttribute(name="path")
  public String getArtifactPath() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("path"), myInfo.getArtifactPath());
  }

  @XmlAttribute(name="downloadTimestamp")
  public String getDownloadTimestamp() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("downloadTimestamp"), Util.formatTime(myInfo.getDownloadTimestamp()));
  }
}