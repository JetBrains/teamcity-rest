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

package jetbrains.buildServer.server.rest.model;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.serverSide.CopyOptions;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("PublicField")
public class CopyOptionsDescription {
  @XmlAttribute @Nullable public Boolean copyAllAssociatedSettings;

  @XmlElement @Nullable public Properties projectsIdsMap;
  @XmlElement @Nullable public Properties buildTypesIdsMap;
  @XmlElement @Nullable public Properties vcsRootsIdsMap;

  public CopyOptionsDescription() {
  }

  public CopyOptionsDescription(@Nullable final Boolean copyAllAssociatedSettings,
                                @Nullable final Map<String, String> projectsIdsMap,
                                @Nullable final Map<String, String> buildTypesIdsMap,
                                @Nullable final Map<String, String> vcsRootsIdsMap) {
    this.copyAllAssociatedSettings = copyAllAssociatedSettings;
    if (projectsIdsMap!= null) this.projectsIdsMap = new Properties(projectsIdsMap);
    if (buildTypesIdsMap!= null) this.buildTypesIdsMap = new Properties(buildTypesIdsMap);
    if (vcsRootsIdsMap!= null) this.vcsRootsIdsMap = new Properties(vcsRootsIdsMap);
  }

  public CopyOptions getCopyOptions() {
    final CopyOptions result = new CopyOptions();
    if (toBoolean(copyAllAssociatedSettings)) {
      //todo: need to use some API to set all necessary options. e.g. see TW-16948, TW-16934
      result.addOption(CopyOptions.Option.COPY_AGENT_POOL_ASSOCIATIONS);
      result.addOption(CopyOptions.Option.COPY_AGENT_RESTRICTIONS);
      result.addOption(CopyOptions.Option.COPY_MUTED_TESTS);
      result.addOption(CopyOptions.Option.COPY_USER_NOTIFICATION_RULES);
      result.addOption(CopyOptions.Option.COPY_USER_ROLES);
    }
    if (projectsIdsMap!= null) result.addProjectExternalIdMapping(projectsIdsMap.getMap());
    if (buildTypesIdsMap!= null) result.addBuildTypeAndTemplateExternalIdMapping(buildTypesIdsMap.getMap());
    if (vcsRootsIdsMap!= null) result.addVcsRootExternalIdMapping(vcsRootsIdsMap.getMap());

    return result;
  }

  private static boolean toBoolean(final Boolean value) {
    return (value == null) ? false: value;
  }
}