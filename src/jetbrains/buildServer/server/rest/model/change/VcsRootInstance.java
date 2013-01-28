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
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.RepositoryVersion;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.SingleVersionRepositoryStateAdapter;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRootInstanceEx;

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

  public VcsRootInstance() {
  }

  public VcsRootInstance(final jetbrains.buildServer.vcs.VcsRootInstance root,
                         final DataProvider dataProvider,
                         final ApiUrlBuilder apiUrlBuilder) {
    super(root, dataProvider, apiUrlBuilder);
    myRoot = root;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlElement(name = "vcs-root")
  public VcsRootRef getParent() {
    return new VcsRootRef(myRoot.getParent(), myApiUrlBuilder);
  }

  @XmlAttribute
  public String getLastVersion() {
    final RepositoryVersion currentRevision = myRoot.getLastUsedRevision();
    return currentRevision != null ? currentRevision.getDisplayVersion() : null;
  }

  @XmlAttribute
  public String getLastVersionInternal() {
    if (!TeamCityProperties.getBoolean("rest.internalMode")) {
      return null;
    }
    final RepositoryVersion currentRevision = myRoot.getLastUsedRevision();
    return currentRevision != null ? currentRevision.getVersion() : null;
  }

  public static String getFieldValue(final jetbrains.buildServer.vcs.VcsRootInstance rootInstance,
                                     final String field,
                                     final DataProvider dataProvider) {
    if ("lastVersion".equals(field)) {
      final RepositoryVersion currentRevision = rootInstance.getLastUsedRevision();
      return currentRevision != null ? currentRevision.getDisplayVersion() : null; //todo: current status code for this case is 204/not changed. Should be different
    } else if ("lastVersionInternal".equals(field)) {
      final RepositoryVersion currentRevision = rootInstance.getLastUsedRevision();
      return currentRevision != null ? currentRevision.getVersion() : null;
    } else if ("currentVersion".equals(field)) {
      final RepositoryVersion currentRevision;
      try {
        currentRevision = rootInstance.getCurrentRevision();
      } catch (VcsException e) {
        throw new OperationException("Error while getting current revision: ", e); //todo: use dedicated exception
      }
      return currentRevision.getDisplayVersion();
    } else if ("currentVersionInternal".equals(field)) {
      final RepositoryVersion currentRevision;
      try {
        currentRevision = rootInstance.getCurrentRevision();
      } catch (VcsException e) {
        throw new OperationException("Error while getting current revision: ", e); //todo: use dedicated exception
      }
      return currentRevision.getVersion();
    }
    try {
      return VcsRoot.getFieldValue(rootInstance.getParent(), field, dataProvider);
    } catch (NotFoundException e) {
      throw new NotFoundException("Field '" + field + "' is not supported. Supported are: lastVersion, lastVersionInternal and those of VCS root, see: " + e.getMessage());
    }
  }

  public static void setFieldValue(final jetbrains.buildServer.vcs.VcsRootInstance rootInstance,
                                   final String field,
                                   final String newValue,
                                   final DataProvider dataProvider) {
    if ("lastVersionInternal".equals(field)) {
      ((VcsRootInstanceEx)rootInstance).setLastUsedState(new SingleVersionRepositoryStateAdapter(newValue));
      return;
    }
    throw new NotFoundException("Setting of field '" + field + "' is not supported. Supported is: lastVersionInternal");
  }
}

