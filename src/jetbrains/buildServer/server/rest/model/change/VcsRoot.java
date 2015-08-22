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
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.UserParametersHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
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
@XmlType(name = "vcs-root", propOrder = { "id", "name","vcsName", "shared", "status", "lastChecked",
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
      project = new ProjectRef(dataProvider.getProjectById(root.getScope().getOwnerProjectId()), apiUrlBuilder);
    }
    if (!shouldRestrictSettingsViewing(root, dataProvider)) {
    properties = new Properties(root.getProperties());
    }
    final VcsRootStatus rootStatus = dataProvider.getVcsManager().getStatus(root);
    status = rootStatus.getType().toString();
    lastChecked = Util.formatTime(rootStatus.getTimestamp());
    /*
    final RepositoryVersion revision = ((VcsRootInstance)root).getLastUsedRevision();
    currentVersion = revision != null ? revision.getDisplayVersion() : null; //todo: consider using smth like "NONE" ?
    */
  }

  public VcsRoot(final jetbrains.buildServer.vcs.VcsRootInstance rootInst, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    id = rootInst.getId();
    name = rootInst.getName();
    vcsName = rootInst.getVcsName();
    SVcsRoot parent = rootInst.getParent();
    shared = parent.getScope().isGlobal();
    if (!shared){
      project = new ProjectRef(dataProvider.getProjectById(parent.getScope().getOwnerProjectId()), apiUrlBuilder);
    }
    if (!shouldRestrictSettingsViewing(parent, dataProvider)) {
    properties = new Properties(rootInst.getProperties());
    }
    final VcsRootStatus rootStatus = dataProvider.getVcsManager().getStatus(parent);
    status = rootStatus.getType().toString();
    lastChecked = Util.formatTime(rootStatus.getTimestamp());
  }

  @NotNull
  public static UserParametersHolder getUserParametersHolder(@NotNull final SVcsRoot root, @NotNull final VcsManager vcsManager) {
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

      @Nullable
      public String getParameterValue(@NotNull final String paramName) {
        return getParameters().get(paramName);
      }
    };
  }

  private static void updateVCSRoot(final SVcsRoot root,
                                    @Nullable final Map<String, String> newProperties,
                                    @Nullable final String newName,
                                    final VcsManager vcsManager) {
    vcsManager.updateVcsRoot(root.getId(),
                             root.getVcsName(),
                             newName != null ? newName : root.getName(),
                             newProperties != null ? newProperties : root.getProperties());
  }

  public static String getFieldValue(final SVcsRoot vcsRoot, final String field) {
    if ("if".equals(field)) {
      return String.valueOf(vcsRoot.getId());
    } else if ("name".equals(field)) {
      return vcsRoot.getName();
    } else if ("vcsName".equals(field)) {
      return vcsRoot.getVcsName();
    } else if ("shared".equals(field)) {
      return String.valueOf(vcsRoot.getScope().isGlobal());
    } else if ("projectId".equals(field)) {
      if (vcsRoot.getScope().isGlobal()){
        return "";
      }
      return  vcsRoot.getScope().getOwnerProjectId();

    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, name, vcsName, shared, projectId");
  }

  public static void setFieldValue(final SVcsRoot vcsRoot, final String field, final String newValue, final DataProvider dataProvider) {
    if ("name".equals(field)) {
      updateVCSRoot(vcsRoot, null, newValue, dataProvider.getVcsManager());
      return;
    } else if ("shared".equals(field)) {
      boolean newShared = Boolean.valueOf(newValue);
      if (newShared){
        dataProvider.getVcsManager().setVcsRootScope(vcsRoot.getId(), VcsRootScope.globalScope());
        return;
      }
      throw new BadRequestException("Setting field 'shared' to false is not supported, set projectId instead.");
    }else if ("projectId".equals(field)) {
        dataProvider.getVcsManager().setVcsRootScope(vcsRoot.getId(), VcsRootScope.projectScope(dataProvider.getProject(newValue, true)));
        return;
    }

    throw new BadRequestException("Setting field '" + field + "' is not supported. Supported are: name, shared, projectId");
  }

  public static boolean shouldRestrictSettingsViewing(final @NotNull SVcsRoot root, final @NotNull DataProvider permissionChecker) {
    //see also jetbrains.buildServer.server.rest.data.VcsRootFinder.checkPermission
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.vcsRoot.checkPermissions")) {
      return !permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, root.getProject().getProjectId());
    }
    return false;
  }
}

