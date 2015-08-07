/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.serverSide.RepositoryVersion;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root-instance")
@XmlType(name = "vcs-root-instance", propOrder = {"lastVersion", "lastVersionInternal",
  "parent"})
@SuppressWarnings("PublicField")
public class VcsRootInstance extends VcsRoot {
  private jetbrains.buildServer.vcs.VcsRootInstance myRoot;
  private ApiUrlBuilder myApiUrlBuilder;
  private final boolean canViewSettings;

  public VcsRootInstance() {
    canViewSettings = true;
  }

  public VcsRootInstance(final jetbrains.buildServer.vcs.VcsRootInstance root,
                         final DataProvider dataProvider,
                         final ApiUrlBuilder apiUrlBuilder) {
    super((jetbrains.buildServer.vcs.VcsRootInstance)root, dataProvider, apiUrlBuilder);
    myRoot = root;
    myApiUrlBuilder = apiUrlBuilder;

    canViewSettings = !VcsRoot.shouldRestrictSettingsViewing(root.getParent(), dataProvider);
  }

  @XmlElement(name = "vcs-root")
  public VcsRootRef getParent() {
    return new VcsRootRef(myRoot.getParent(), myApiUrlBuilder);
  }

  @XmlAttribute
  public String getLastVersion() {
    final RepositoryVersion currentRevision = myRoot.getLastUsedRevision();
    return check(currentRevision != null ? currentRevision.getDisplayVersion() : null);
  }

  @XmlAttribute
  public String getLastVersionInternal() {
    if (!TeamCityProperties.getBoolean("rest.internalMode")) {
      return null;
    }
    final RepositoryVersion currentRevision = myRoot.getLastUsedRevision();
    return check(currentRevision != null ? currentRevision.getVersion() : null);
  }

  @Nullable
  private <T> T check(@Nullable T t) {
    if (canViewSettings) {
      return t;
    } else {
      return null;
    }
  }
}

