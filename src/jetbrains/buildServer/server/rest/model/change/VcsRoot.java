/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.VcsRootInstanceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.vcs.api.VcsSettings;
import jetbrains.vcs.api.services.tc.MappingGeneratorService;
import jetbrains.vcs.api.services.tc.VcsMappingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root")
@XmlType(name = "vcs-root", propOrder = { "id", "internalId", "uuid", "name","vcsName", "modificationCheckInterval", "status", "lastChecked", "href",
  "project", "properties", "vcsRootInstances"})  //todo: add webUrl
@SuppressWarnings("PublicField")
public class VcsRoot {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public Long internalId;

  @XmlAttribute
  public String uuid;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String vcsName;

  @XmlAttribute
  public Integer modificationCheckInterval;

  @XmlAttribute
  public String status;

  @XmlAttribute
  public String lastChecked;

  @XmlAttribute
  public String href;


  @XmlElement
  public Properties properties;

  /**
   * Used only when creating new VCS roots
   * @deprecated Specify project element instead
   */
  @Deprecated
  @XmlAttribute
  public String projectLocator;

  @XmlElement(name = "project")
  public Project project;

  @XmlElement
  public VcsRootInstances vcsRootInstances;

  /**
   * This is used only when posting a link
   */
  @XmlAttribute public String locator;


  /*
  @XmlAttribute
  private String currentVersion;
  */

  public VcsRoot() {
  }

  public VcsRoot(@NotNull final SVcsRoot root, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), root.getExternalId());
    final boolean includeInternalId = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    internalId =  ValueWithDefault.decideDefault(fields.isIncluded("internalId", includeInternalId, includeInternalId), root.getId());

    uuid = ValueWithDefault.decideDefault(fields.isIncluded("uuid", false, false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        final SProject projectOfTheRoot = getProjectByRoot(root);
        if (projectOfTheRoot != null && beanContext.getSingletonService(PermissionChecker.class).isPermissionGranted(Permission.EDIT_PROJECT, projectOfTheRoot.getProjectId())) {
          return ((SVcsRootEx)root).getEntityId().getConfigId();
        }
        return null;
      }
    });

    name = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name"), root.getName());

    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().getHref(root));

    vcsName = ValueWithDefault.decideDefault(fields.isIncluded("vcsName", false), root.getVcsName());
    final String ownerProjectId = root.getScope().getOwnerProjectId();
    final SProject projectById = beanContext.getSingletonService(ProjectManager.class).findProjectById(ownerProjectId);
    if (projectById != null) {
      project = ValueWithDefault.decideDefault(fields.isIncluded("project", false), new Project(projectById, fields.getNestedField("project"), beanContext));
    } else {
      project = ValueWithDefault.decideDefault(fields.isIncluded("project", false), new Project(null, ownerProjectId, beanContext.getApiUrlBuilder()));
    }

    properties = ValueWithDefault.decideDefault(fields.isIncluded("properties", false),
                                                new Properties(root.getProperties(), null, fields.getNestedField("properties", Fields.NONE, Fields.LONG)));
    modificationCheckInterval = ValueWithDefault.decideDefault(fields.isIncluded("modificationCheckInterval", false),
                                                               root.isUseDefaultModificationCheckInterval() ? null : root.getModificationCheckInterval());
    final VcsRootStatus rootStatus = beanContext.getSingletonService(VcsManager.class).getStatus(root);

    status = ValueWithDefault.decideDefault(fields.isIncluded("status", false), rootStatus.getType().toString());
    lastChecked = ValueWithDefault.decideDefault(fields.isIncluded("lastChecked", false), Util.formatTime(rootStatus.getTimestamp()));
    vcsRootInstances = ValueWithDefault.decideDefault(fields.isIncluded("vcsRootInstances", false), new ValueWithDefault.Value<VcsRootInstances>() {
      @Nullable
      public VcsRootInstances get() {
        return new VcsRootInstances(new CachingValue<Collection<VcsRootInstance>>() {
          @NotNull
          @Override
          protected Collection<VcsRootInstance> doGet() {
            return beanContext.getSingletonService(VcsRootFinder.class).getVcsRootInstances(VcsRootFinder.createVcsRootInstanceLocator(VcsRootFinder.getVcsRootInstancesLocatorText(root))).myEntries;
          }
        }, new PagerData(VcsRootInstanceRequest.getVcsRootInstancesHref(root)), fields.getNestedField("vcsRootInstances"), beanContext);
      }
    });
  }

  @Nullable
  public static SProject getProjectByRoot(@NotNull final SVcsRoot root) {
    try {
      return root.getProject();
    } catch (UnsupportedOperationException e) {
      //TeamCity API issue: NotNull method can throw UnsupportedOperationException if the VCS root is deleted
      return null;
    }
  }

  @NotNull
  public static UserParametersHolder getUserParametersHolder(@NotNull final SVcsRoot root) {
    //todo (TeamCity) open API: make VCS root UserParametersHolder
    return new UserParametersHolder() {
      public void addParameter(@NotNull final Parameter param) {
        final Map<String, String> newProperties = new HashMap<String, String>(root.getProperties());
        newProperties.put(param.getName(), param.getValue());
        root.setProperties(newProperties);
      }

      public void removeParameter(@NotNull final String paramName) {
        final Map<String, String> newProperties = new HashMap<String, String>(root.getProperties());
        newProperties.remove(paramName);
        root.setProperties(newProperties);
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

  public static String getFieldValue(final SVcsRoot vcsRoot, final String field, final DataProvider dataProvider) {
    if ("id".equals(field)) {
      return vcsRoot.getExternalId();
    } else if ("internalId".equals(field)) {
      return String.valueOf(vcsRoot.getId());
    } else if ("name".equals(field)) {
      return vcsRoot.getName();
    } else if ("vcsName".equals(field)) {
      return vcsRoot.getVcsName();
    } else if ("projectInternalId".equals(field)) { //Not documented, do we actually need this?
      return vcsRoot.getScope().getOwnerProjectId();
    } else if ("projectId".equals(field)) { //todo: do we actually need this?
      return dataProvider.getProjectByInternalId(vcsRoot.getScope().getOwnerProjectId()).getExternalId();
    } else if ("modificationCheckInterval".equals(field)) {
      return String.valueOf(vcsRoot.getModificationCheckInterval());
    } else if ("defaultModificationCheckIntervalInUse".equals(field)) { //Not documented
      return String.valueOf(vcsRoot.isUseDefaultModificationCheckInterval());
    } else if ("repositoryMappings".equals(field)) { //Not documented
      try {
        return String.valueOf(getRepositoryMappings(vcsRoot, dataProvider.getVcsManager()));
      } catch (VcsException e) {
        throw new InvalidStateException("Error retrieving mapping", e);
      }
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, name, vcsName, projectId, modificationCheckInterval");
  }

  public static void setFieldValue(@NotNull final SVcsRoot vcsRoot,
                                   @Nullable final String field,
                                   @Nullable final String newValue,
                                   @NotNull final DataProvider dataProvider,
                                   @NotNull final ProjectFinder projectFinder) {
    if ("id".equals(field)) {
      if (newValue != null){
        vcsRoot.setExternalId(newValue);
      }else{
        throw new BadRequestException("Id cannot be empty");
      }
      return;
    } if ("name".equals(field)) {
      if (newValue != null){
        vcsRoot.setName(newValue);
      }else{
        throw new BadRequestException("Name cannot be empty");
      }
      return;
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
      return;
    } else if ("defaultModificationCheckIntervalInUse".equals(field)){
      boolean newUseDefault = Boolean.valueOf(newValue);
      if (newUseDefault) {
        vcsRoot.restoreDefaultModificationCheckInterval();
        return;
      }
      throw new BadRequestException("Setting field 'defaultModificationCheckIntervalInUse' to false is not supported, set modificationCheckInterval instead.");
    } else if ("projectId".equals(field) || "project".equals(field)) { //project locator is acually supported, "projectId" is preserved for compatibility with previous versions
      SProject targetProject = projectFinder.getProject(newValue);
      vcsRoot.moveToProject(targetProject);
      return;
    }

    throw new NotFoundException("Setting field '" + field + "' is not supported. Supported are: name, project, modificationCheckInterval");
  }

  public static Collection<VcsMappingElement> getRepositoryMappings(@NotNull final jetbrains.buildServer.vcs.VcsRoot root, @NotNull final VcsManager vcsManager) throws VcsException {
    final VcsSettings vcsSettings = new VcsSettings(root, "");
    final MappingGeneratorService mappingGenerator = vcsManager.getVcsService(vcsSettings, MappingGeneratorService.class);

    if (mappingGenerator == null) {
      return Collections.emptyList();
    }
    return mappingGenerator.generateMapping();
  }

  @NotNull
  public SVcsRoot getVcsRoot(@NotNull VcsRootFinder vcsRootFinder) {
    String locatorText = "";
//    if (internalId != null) locatorText = "internalId:" + internalId;
    if (id != null) locatorText += (!locatorText.isEmpty() ? "," : "") + "id:" + id; //todo: link to dimension in finder
    if (locatorText.isEmpty()) {
      locatorText = locator;
    } else {
      if (locator != null) {
        throw new BadRequestException("Both 'locator' and 'id' attributes are specified. Only one should be present.");
      }
    }
    if (StringUtil.isEmpty(locatorText)){
      throw new BadRequestException("No VCS root specified. Either 'id' or 'locator' attribute should be present.");
    }
    return vcsRootFinder.getVcsRoot(locatorText);
  }
}

