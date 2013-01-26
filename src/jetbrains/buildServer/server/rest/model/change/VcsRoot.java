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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.UserParametersHolder;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootScope;
import jetbrains.buildServer.vcs.VcsRootStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root")
@XmlType(name = "vcs-root", propOrder = { "id", "name","vcsName", "shared", "modificationCheckInterval", "status", "lastChecked",
  "project", "properties"})
@SuppressWarnings("PublicField")
public class VcsRoot {
  @XmlAttribute
  public Long id;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String vcsName;

  @XmlAttribute
  public Boolean shared;


  @XmlAttribute
  public String projectLocator; // used only when creating new VCS roots


  @XmlAttribute
  public  Integer  modificationCheckInterval;

  @XmlAttribute
  public  String status;

  @XmlAttribute
  public  String lastChecked;


  @XmlElement
  public Properties properties;

  @XmlElement(name = "project")
  public ProjectRef project;


  /*
  @XmlAttribute
  private String currentVersion;
  */

  public VcsRoot() {
  }

  public VcsRoot(final SVcsRoot root, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    id = root.getId();
    name = root.getName();
    vcsName = root.getVcsName();
    shared = root.getScope().isGlobal();
    if (!shared){
      project = new ProjectRef(dataProvider.getProjectByInternalId(root.getScope().getOwnerProjectId()), apiUrlBuilder);
    }
    properties = new Properties(root.getProperties());
    modificationCheckInterval = root.isUseDefaultModificationCheckInterval() ? null : root.getModificationCheckInterval();
    final VcsRootStatus rootStatus = dataProvider.getVcsManager().getStatus(root);
    status = rootStatus.getType().toString();
    lastChecked = Util.formatTime(rootStatus.getTimestamp());
    /*
    final RepositoryVersion revision = ((VcsRootInstance)root).getLastUsedRevision();
    currentVersion = revision != null ? revision.getDisplayVersion() : null; //todo: consider using smth like "NONE" ?
    */
  }

  @NotNull
  public static UserParametersHolder getUserParametersHolder(@NotNull final SVcsRoot root, @NotNull final VcsManager vcsManager) {
    //todo (TeamCity) open API: make VCS root UserParametersHolder
    return new UserParametersHolder() {
      public void addParameter(@NotNull final Parameter param) {
        final Map<String, String> newProperties = new HashMap<String, String>(root.getProperties());
        newProperties.put(param.getName(), param.getValue());
        updateVCSRoot(root, newProperties, null, vcsManager);
      }

      public void removeParameter(@NotNull final String paramName) {
        final Map<String, String> newProperties = new HashMap<String, String>(root.getProperties());
        newProperties.remove(paramName);
        updateVCSRoot(root, newProperties, null, vcsManager);
      }

      @NotNull
      public Collection<Parameter> getParametersCollection() {
        final ArrayList<Parameter> result = new ArrayList<Parameter>();
        for (Map.Entry<String, String> item : getParameters().entrySet()) {
          result.add(new SimpleParameter(item.getKey(), item.getValue()));
        }
        return result;
      }

      @NotNull
      public Map<String, String> getParameters() {
        return root.getProperties();
      }
    };
  }

  public static void updateVCSRoot(final SVcsRoot root,
                                    @Nullable final Map<String, String> newProperties,
                                    @Nullable final String newName,
                                    final VcsManager vcsManager) {
    vcsManager.updateVcsRoot(root.getId(),
                             root.getVcsName(),
                             newName != null ? newName : root.getName(),
                             newProperties != null ? newProperties : root.getProperties());
  }

  public static String getFieldValue(final SVcsRoot vcsRoot, final String field, final DataProvider dataProvider) {
    if ("if".equals(field)) {
      return String.valueOf(vcsRoot.getId());
    } else if ("name".equals(field)) {
      return vcsRoot.getName();
    } else if ("vcsName".equals(field)) {
      return vcsRoot.getVcsName();
    } else if ("shared".equals(field)) {
      return String.valueOf(vcsRoot.getScope().isGlobal());
    } else if ("projectInternalId".equals(field)) { //Not documented, do we actually need this?
      if (vcsRoot.getScope().isGlobal()) {
        return "";
      }
      return vcsRoot.getScope().getOwnerProjectId();
    }  else if ("projectId".equals(field)) { //todo: do we actually need this?
      if (vcsRoot.getScope().isGlobal()) {
        return "";
      }
      return dataProvider.getProjectByInternalId(vcsRoot.getScope().getOwnerProjectId()).getExternalId();
    } else if ("modificationCheckInterval".equals(field)) {
      return String.valueOf(vcsRoot.getModificationCheckInterval());
    } else if ("defaultModificationCheckIntervalInUse".equals(field)) { //Not documented
      return String.valueOf(vcsRoot.isUseDefaultModificationCheckInterval());
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, name, vcsName, shared, projectId, modificationCheckInterval");
  }

  public static void setFieldValue(final SVcsRoot vcsRoot, final String field, final String newValue, final DataProvider dataProvider) {
    if ("name".equals(field)) {
      updateVCSRoot(vcsRoot, null, newValue, dataProvider.getVcsManager());
      return;
    } else if ("shared".equals(field)) {
      boolean newShared = Boolean.valueOf(newValue);
      if (newShared) {
        dataProvider.getVcsManager().setVcsRootScope(vcsRoot.getId(), VcsRootScope.globalScope());
        return;
      }
      throw new BadRequestException("Setting field 'shared' to false is not supported, set projectId instead.");
    } else if ("modificationCheckInterval".equals(field)) {
      if ("".equals(newValue)) {
        vcsRoot.restoreDefaultModificationCheckInterval();
      } else {
        int newInterval = 0;
        try {
          newInterval = Integer.valueOf(newValue);
        } catch (NumberFormatException e) {
          throw new BadRequestException(
            "Field 'modificationCheckInterval' should be an integer value. Error during parsing: " + e.getMessage());
        }
        vcsRoot.setModificationCheckInterval(newInterval); //todo (TeamCity) open API can set negative value which gets applied
      }
      dataProvider.getVcsManager().persistVcsRoots();  //todo: (TeamCity) open API need to call persist or not ???
      return;
    } else if ("defaultModificationCheckIntervalInUse".equals(field)){
      boolean newUseDefault = Boolean.valueOf(newValue);
      if (newUseDefault) {
        vcsRoot.restoreDefaultModificationCheckInterval();
        dataProvider.getVcsManager().persistVcsRoots();  //todo: (TeamCity) open API need to call persist or not ???
        return;
      }
      throw new BadRequestException("Setting field 'defaultModificationCheckIntervalInUse' to false is not supported, set modificationCheckInterval instead.");
    } else if ("projectId".equals(field) || "project".equals(field)) { //project locator is acually supported, "projectId" is preserved for compatibility with previous versions
      dataProvider.getVcsManager().setVcsRootScope(vcsRoot.getId(), VcsRootScope.projectScope(dataProvider.getProject(newValue)));
      return;
    }

    throw new BadRequestException("Setting field '" + field + "' is not supported. Supported are: name, shared, project, modificationCheckInterval");
  }
}

