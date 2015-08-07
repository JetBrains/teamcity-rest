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

package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.RepositoryVersion;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.SingleVersionRepositoryStateAdapter;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRootInstanceEx;
import jetbrains.buildServer.vcs.VcsRootStatus;
import jetbrains.buildServer.vcs.impl.RepositoryStateManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root-instance")
@XmlType(name = "vcs-root-instance", propOrder = {"id", "name","vcsName", "status", "lastChecked", "lastVersion", "lastVersionInternal", "href",
  "parent", "properties"})
@SuppressWarnings("PublicField")
public class VcsRootInstance {
  private jetbrains.buildServer.vcs.VcsRootInstance myRoot;
  private ApiUrlBuilder myApiUrlBuilder;
  private final boolean canViewSettings;

  @XmlAttribute
  public String id;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String vcsName;

  @XmlAttribute
  public String status;

  @XmlAttribute
  public String lastChecked;

  @XmlAttribute
  public String href;

  /**
   * Used only when creating new VCS roots
   * @deprecated Specify project element instead
   */
  @XmlAttribute
  public String projectLocator;


  public VcsRootInstance() {
    canViewSettings = true;
  }

  public VcsRootInstance(final jetbrains.buildServer.vcs.VcsRootInstance root,
                         final DataProvider dataProvider,
                         final ApiUrlBuilder apiUrlBuilder) {
    id = String.valueOf(root.getId());
    myRoot = root;
    myApiUrlBuilder = apiUrlBuilder;

    name = root.getName();
    vcsName = root.getVcsName();

    final VcsRootStatus vcsRootStatus = ((VcsRootInstanceEx)myRoot).getStatus();
    status = vcsRootStatus.getType().toString();
    canViewSettings = !VcsRoot.shouldRestrictSettingsViewing(root.getParent(), dataProvider);

    lastChecked = check(Util.formatTime(vcsRootStatus.getTimestamp()));
    href = apiUrlBuilder.getHref(root);
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

  @XmlElement(name = "vcs-root")
  public VcsRootRef getParent() {
    return new VcsRootRef(myRoot.getParent(), myApiUrlBuilder);
  }

  @XmlElement
  public Properties getProperties(){
    return check(new Properties(myRoot.getProperties()));
  }

  public static String getFieldValue(final jetbrains.buildServer.vcs.VcsRootInstance rootInstance,
                                     final String field,
                                     final DataProvider dataProvider) {
    if ("id".equals(field)) {
       return String.valueOf(rootInstance.getId());
     } else if ("name".equals(field)) {
       return rootInstance.getName();
     } else if ("vcsName".equals(field)) {
       return rootInstance.getVcsName();
     } else if ("projectInternalId".equals(field)) { //Not documented, do we actually need this?
       return rootInstance.getParent().getScope().getOwnerProjectId();
     } else if ("projectId".equals(field)) { //Not documented
       return rootInstance.getParent().getProject().getExternalId();
     } else if ("repositoryMappings".equals(field)) { //Not documented
       try {
         return String.valueOf(VcsRoot.getRepositoryMappings(rootInstance, dataProvider.getVcsManager()));
       } catch (VcsException e) {
         throw new InvalidStateException("Error retrieving mapping", e);
       }
    } else if ("lastVersion".equals(field)) {
      final RepositoryVersion currentRevision = rootInstance.getLastUsedRevision();
      return currentRevision != null ? currentRevision.getDisplayVersion() : null; //todo: current status code for this case is 204/not changed. Should be different
    } else if ("lastVersionInternal".equals(field)) {
      final RepositoryVersion currentRevision = rootInstance.getLastUsedRevision();
      return currentRevision != null ? currentRevision.getVersion() : null;
    } else if ("currentVersion".equals(field)) {
      try {
        return rootInstance.getCurrentRevision().getDisplayVersion();
      } catch (VcsException e) {
        throw new InvalidStateException("Error while getting current revision: ", e);
      }
    } else if ("currentVersionInternal".equals(field)) {
      try {
        return  rootInstance.getCurrentRevision().getVersion();
      } catch (VcsException e) {
        throw new InvalidStateException("Error while getting current revision: ", e);
      }
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, name, vcsName, lastVersion, lastVersionInternal, currentVersion, currentVersionInternal.");
  }

  public static void setFieldValue(final jetbrains.buildServer.vcs.VcsRootInstance rootInstance,
                                   final String field,
                                   final String newValue,
                                   final DataProvider dataProvider) {
    if ("lastVersionInternal".equals(field)) {
      dataProvider.getBean(RepositoryStateManager.class).setRepositoryState(rootInstance, new SingleVersionRepositoryStateAdapter(newValue));
      return;
    }
    throw new NotFoundException("Setting of field '" + field + "' is not supported. Supported is: lastVersionInternal");
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

