/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.parameters.EntityWithModifiableParameters;
import jetbrains.buildServer.server.rest.data.parameters.MapBackedEntityWithModifiableParameters;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Items;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.VcsRootInstanceRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.*;
import jetbrains.vcs.api.VcsSettings;
import jetbrains.vcs.api.services.tc.MappingGeneratorService;
import jetbrains.vcs.api.services.tc.VcsMappingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.serverSide.impl.projectSources.SmallPatchCache.LOG;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root")
@XmlType(name = "vcs-root", propOrder = { "id", "internalId", "uuid", "name","vcsName", "modificationCheckInterval", "href",
  "project", "properties", "vcsRootInstances" , "repositoryIdStrings"})  //todo: add webUrl
@SuppressWarnings("PublicField")
@ModelDescription(
    value = "Represents a VCS root.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/vcs-root.html",
    externalArticleName = "VCS Root"
)
public class VcsRoot {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public String internalId;

  @XmlAttribute
  public String uuid;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String vcsName;

  @XmlAttribute
  public Integer modificationCheckInterval;

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

  @XmlElement
  public Items repositoryIdStrings;

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
    internalId =  ValueWithDefault.decideDefault(fields.isIncluded("internalId", includeInternalId, includeInternalId), String.valueOf(root.getId()));

    final PermissionChecker permissionChecker = beanContext.getServiceLocator().findSingletonService(PermissionChecker.class);
    assert permissionChecker != null;
    uuid = ValueWithDefault.decideDefault(fields.isIncluded("uuid", false, false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        final SProject projectOfTheRoot = getProjectByRoot(root);
        if (projectOfTheRoot != null && permissionChecker.isPermissionGranted(Permission.EDIT_PROJECT, projectOfTheRoot.getProjectId())) {
          return ((SVcsRootEx)root).getEntityId().getConfigId();
        }
        return null;
      }
    });

    name = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name"), root.getName());

    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), () -> beanContext.getApiUrlBuilder().getHref(root));

    vcsName = ValueWithDefault.decideDefault(fields.isIncluded("vcsName", false), root.getVcsName());
    final String ownerProjectId = root.getScope().getOwnerProjectId();
    final SProject projectById = beanContext.getSingletonService(ProjectFinder.class).findProjectByInternalId(ownerProjectId);
    if (projectById != null) {
      project = ValueWithDefault.decideDefault(fields.isIncluded("project", false), () -> new Project(projectById, fields.getNestedField("project"), beanContext));
    } else {
      project = ValueWithDefault.decideDefault(fields.isIncluded("project", false), () -> new Project(null, ownerProjectId, fields.getNestedField("project"), beanContext));
    }

    if (!shouldRestrictSettingsViewing(root, permissionChecker)) {
      properties = ValueWithDefault.decideDefault(fields.isIncluded("properties", false),
                                                  () -> new Properties(root.getProperties(), null, fields.getNestedField("properties", Fields.NONE, Fields.LONG), beanContext));
      modificationCheckInterval = ValueWithDefault.decideDefault(fields.isIncluded("modificationCheckInterval", false),
                                                                 () -> root.isUseDefaultModificationCheckInterval() ? null : root.getModificationCheckInterval());
      vcsRootInstances = ValueWithDefault.decideDefault(fields.isIncluded("vcsRootInstances", false), new ValueWithDefault.Value<VcsRootInstances>() {
        @Nullable
        public VcsRootInstances get() {
          return new VcsRootInstances(new CachingValue<Collection<VcsRootInstance>>() {
            @NotNull
            @Override
            protected Collection<VcsRootInstance> doGet() {
              return beanContext.getSingletonService(VcsRootInstanceFinder.class).getItems(VcsRootInstanceFinder.getLocatorByVcsRoot(root)).myEntries;
            }
          }, new PagerData(VcsRootInstanceRequest.getVcsRootInstancesHref(root)), fields.getNestedField("vcsRootInstances"), beanContext);
        }
      });
      repositoryIdStrings = ValueWithDefault.decideDefault(fields.isIncluded("repositoryIdStrings", false, false), new ValueWithDefault.Value<Items>() {
        @Nullable
        @Override
        public Items get() {
          ArrayList<String> result = new ArrayList<>();
          try {
            Collection<VcsMappingElement> vcsMappingElements = VcsRoot.getRepositoryMappings(root, beanContext.getSingletonService(VcsManager.class));
            for (VcsMappingElement vcsMappingElement : vcsMappingElements) {
              result.add(vcsMappingElement.getTo());
            }
            return new Items(result);
          } catch (Exception e) {
            LOG.debug("Error while retrieving mapping for VCS root " + LogUtil.describe(root) + ", skipping " + "repositoryIdStrings" + " in root details", e);
            //ignore
          }
          return null;
        }
      });
    } else {
      properties = null;
      modificationCheckInterval = null;
      vcsRootInstances = null;
      repositoryIdStrings = null;
    }
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
  public static EntityWithModifiableParameters getEntityWithParameters(@NotNull final SVcsRoot root) {
    return new MapBackedEntityWithModifiableParameters(new MapBackedEntityWithModifiableParameters.PropProxy() {
      @Override
      public Map<String, String> get() {
        return root.getProperties();
      }

      @Override
      public void set(final Map<String, String> params) {
        root.setProperties(params);
      }
    });
  }

  public static String getFieldValue(final SVcsRoot vcsRoot, final String field, final DataProvider dataProvider) {
    //assuming only users with VIEW_SETTINGS permissions get here
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
    } else if ("projectId".equals(field) || "project".equals(field)) { //this should correspond to the setting part to be able to return result from it
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
          newInterval = Integer.parseInt(newValue);
        } catch (Exception e) {
          throw new BadRequestException(
            "Field 'modificationCheckInterval' should be an integer value. Error during parsing: " + e.getMessage());
        }
        vcsRoot.setModificationCheckInterval(newInterval); //todo (TeamCity) open API can set negative value which gets applied
      }
      return;
    } else if ("defaultModificationCheckIntervalInUse".equals(field)){
      boolean newUseDefault = Boolean.parseBoolean(newValue);
      if (newUseDefault) {
        vcsRoot.restoreDefaultModificationCheckInterval();
        return;
      }
      throw new BadRequestException("Setting field 'defaultModificationCheckIntervalInUse' to false is not supported, set modificationCheckInterval instead.");
    } else if ("projectId".equals(field) || "project".equals(field)) { //project locator is actually supported, "projectId" is preserved for compatibility with previous versions
      SProject targetProject = projectFinder.getItem(newValue);
      vcsRoot.moveToProject(targetProject);
      return;
    }

    throw new NotFoundException("Setting field '" + field + "' is not supported. Supported are: id, name, projectId, modificationCheckInterval");
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
    if (id != null) locatorText = Locator.getStringLocator(AbstractFinder.DIMENSION_ID, id);
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
    return vcsRootFinder.getItem(locatorText);
  }

  public static boolean shouldRestrictSettingsViewing(final @NotNull SVcsRoot root, final @NotNull PermissionChecker permissionChecker) {
    //see also jetbrains.buildServer.server.rest.data.VcsRootFinder.checkPermission
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.vcsRoot.checkPermissions")) {
      final SProject project = VcsRoot.getProjectByRoot(root);
      return !permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project != null ? project.getProjectId() : null);
    }
    return false;
  }
}

