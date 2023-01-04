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

package jetbrains.buildServer.server.rest.util;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.identifiers.BuildTypeIdentifiersManager;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.vcs.VcsRootInstanceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 14.01.12
 */
public class BuildTypeOrTemplate implements Loggable {
  @Nullable final private SBuildType myBuildType;
  @Nullable final private BuildTypeTemplate myTemplate;
  @NotNull final private BuildTypeIdentity myBuildTypeIdentity;
  @Nullable private Boolean myInherited = null; //used in template lists only

  public BuildTypeOrTemplate(@NotNull SBuildType buildType) {
    myBuildType = buildType;
    myTemplate = null;
    myBuildTypeIdentity = buildType;
  }

  public BuildTypeOrTemplate(@NotNull BuildTypeTemplate template) {
    myTemplate = template;
    myBuildType = null;
    myBuildTypeIdentity = template;
  }

  @NotNull
  public BuildTypeSettings get(){
    //noinspection ConstantConditions
    return isBuildType() ? myBuildType : myTemplate;
  }

  public BuildTypeOrTemplate markInherited(final boolean inherited) {
    myInherited = inherited;
    return this;
  }

  @Nullable
  public Boolean isInherited() {
    return myInherited;
  }

  @NotNull
  public BuildTypeSettingsEx getSettingsEx(){
    //noinspection ConstantConditions
    return isBuildType() ? ((BuildTypeEx)myBuildType).getSettings() : ((BuildTypeTemplateEx)myTemplate).getSettings();
  }

  @NotNull
  public BuildTypeIdentity getIdentity(){
    //noinspection ConstantConditions
    return isBuildType() ? myBuildType : myTemplate;
  }

  @NotNull
  public String getId(){
    return myBuildTypeIdentity.getExternalId();
  }

  @NotNull
  public String getInternalId(){
    return myBuildTypeIdentity.getInternalId();
  }

  public String getName(){
    return myBuildTypeIdentity.getName();
  }

  @NotNull
  public SProject getProject() {
    return myBuildTypeIdentity.getProject();
  }

  public boolean isBuildType() {
    return myBuildType != null;
  }

  public boolean isTemplate() {
    return myTemplate != null;
  }

  @Nullable
  public SBuildType getBuildType() {
    return myBuildType;
  }

  @Nullable
  public BuildTypeTemplate getTemplate() {
    return myTemplate;
  }

  @Nullable
  public String getDescription() {
    //noinspection ConstantConditions
    return myBuildType!=null ? myBuildType.getDescription() : myTemplate.getDescription();
  }

  @Nullable
  public Boolean isPaused() {
    return myBuildType!=null ? myBuildType.isPaused() : null;
  }

  @NotNull
  public String getText() {
    return isBuildType() ? "Build type": "Template";
  }

  public void setName(@NotNull final String value) {
    if (myBuildType!=null){
      myBuildType.setName(value);
    }else{
      //noinspection ConstantConditions
      myTemplate.setName(value);
    }
  }

  public void setDescription(@Nullable final String value) {
    if (myBuildType != null) {
      myBuildType.setDescription(value);
    } else {
      //noinspection ConstantConditions
      myTemplate.setDescription(value);
    }
  }

  public void remove(@Nullable SUser user, String reason) {
    try {
      ConfigAction action = myBuildTypeIdentity.createConfigAction(user, reason);
      myBuildTypeIdentity.scheduleRemove(action);
    } catch (TemplateCannotBeRemovedException e) {
      throw new BadRequestException("Cannot remove template with id '" + getId() + "': " + e.getMessage(), e);
    }
  }

  public static void setTemplates(@NotNull final SBuildType buildType, @NotNull final List<BuildTypeOrTemplate> buildTypeOrTemplates, final boolean optimizeSetting) {
    List<BuildTypeTemplate> newTemplates = buildTypeOrTemplates.stream().filter(t -> t.isInherited() == null || !t.isInherited()).map(bt -> {
      BuildTypeTemplate result = bt.getTemplate();
      if (result == null) {
        throw new BadRequestException("Found build type when only templates are expected: " + jetbrains.buildServer.log.LogUtil.describe(bt.getBuildType()));
      }
      return result;
    }).collect(Collectors.toList());
    if (haveSameElements(buildType.getOwnTemplates(), newTemplates)) {
      //only order changes: reorder
      buildType.setTemplatesOrder(newTemplates.stream().map(t -> t.getId()).collect(Collectors.toList()));
      return;
    }
    try {
      buildType.setTemplates(newTemplates, optimizeSetting);
    } catch (CannotAttachToTemplateException e) {
      //cannot revert as detachFromAllTemplates inlines settings into the build configuration
      throw new BadRequestException("Error attaching to templates, settings might be in partly modified state: " + e.getMessage());
    } catch (Exception e) {
      throw new OperationException("Error attaching to templates, settings might be in partly modified state: " + e.toString());
    }
  }

  private static boolean haveSameElements(final List<? extends BuildTypeTemplate> t1, final List<BuildTypeTemplate> t2) {
    if (t1.size() != t2.size()) return false;
    Set<String> t1Ids = t1.stream().map(BuildTypeTemplate::getId).collect(Collectors.toSet());
    Set<String> t2Ids = t2.stream().map(BuildTypeTemplate::getId).collect(Collectors.toSet());
    Set<String> common = CollectionsUtil.intersect(t1Ids, t2Ids);
    return common.size() == t1.size();
  }

  public void setFieldValueAndPersist(@NotNull final String field, @Nullable final String value, @NotNull final ServiceLocator serviceLocator) {
    if ("id".equals(field)) {
      if (value != null){
        myBuildTypeIdentity.setExternalId(value);
      } else {
        throw new BadRequestException("Id cannot be empty");
      }
      return;
    }

    if ("uuid".equals(field)) {
      serviceLocator.getSingletonService(PermissionChecker.class).checkPermission(Permission.EDIT_PROJECT, get());
      if (value == null) {
        throw new BadRequestException("UUID cannot be empty");
      }
      BuildTypeOrTemplate existingByUuid = serviceLocator.getSingletonService(BuildTypeFinder.class).findBuildTypeOrTemplateByUuid(value, null);
      if (existingByUuid != null) {
        throw new BadRequestException("Build type with UUID '" + value + "' already exists");
      }
      serviceLocator.getSingletonService(BuildTypeIdentifiersManager.class).modifyConfigId(((BuildTypeIdentityEx)myBuildTypeIdentity).getEntityId(), value, null);
      myBuildTypeIdentity.schedulePersisting("UUID changed");
      return;
    }

    if ("name".equals(field)) {
      if (value != null){
        setName(value);
        myBuildTypeIdentity.schedulePersisting("Name changed");
      } else {
        throw new BadRequestException("Name cannot be empty");
      }
      return;
    }

    if ("description".equals(field)) {
      setDescription(value);
      myBuildTypeIdentity.schedulePersisting("Description changed");
      return;
    }

    if (myBuildType!=null){
      if ("paused".equals(field)){
        //TeamCity API: why not use current user by default?
        myBuildType.setPaused(Boolean.parseBoolean(value), serviceLocator.getSingletonService(UserFinder.class).getCurrentUser(),
                              TeamCityProperties.getProperty("rest.defaultActionComment"));
        myBuildType.schedulePersisting("Build configuration paused");
        return;
      }
    }

    throw new BadRequestException("Setting field '" + field + "' is not supported. Supported are: name, description, paused");
  }

  @Nullable
  public String getFieldValue(final String field, @NotNull final BeanContext beanContext) {
    // Fields should not require additional permissions apart from VIEW_PROJECT
    if ("id".equals(field)) {
      return getId();
    } else if ("internalId".equals(field)) {
      return getInternalId();
    } else if ("uuid".equals(field)) {
      beanContext.getSingletonService(PermissionChecker.class).checkPermission(Permission.EDIT_PROJECT, get());
      return ((BuildTypeIdentityEx)myBuildTypeIdentity).getEntityId().getConfigId();
    } else if ("description".equals(field)) {
      return getDescription();
    } else if ("name".equals(field)) {
      return getName();
    } else if ("fullName".equals(field)) {
      return myBuildTypeIdentity.getFullName();
    } else if ("projectName".equals(field)) {
      return get().getProject().getFullName();
    }
    if (myBuildType!=null){
      if ("paused".equals(field)){
        return String.valueOf(myBuildType.isPaused());
      } else if ("status".equals(field)){ //Experimental support
        return  myBuildType.getStatus().getText();
      }
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, name, description, paused, internalId.");
  }

  public boolean isEnabled(final String id) {
    //noinspection ConstantConditions
    return myBuildType != null ? myBuildType.isEnabled(id) : myTemplate.isEnabled(id);
  }

  @NotNull
  public List<VcsRootInstanceEntry> getVcsRootInstanceEntries(){
    //noinspection ConstantConditions
    return myBuildType != null ? myBuildType.getVcsRootInstanceEntries() : ((BuildTypeTemplateEx)myTemplate).getVcsRootInstanceEntries(); //TeamCity open API issue
  }

  @NotNull
  public String describe(final boolean verbose) {
      return isBuildType() ? LogUtil.describe(myBuildType) : LogUtil.describe(myTemplate);
  }

  public void persist(@NotNull String reason) {
    if (myBuildType != null) {
      myBuildType.schedulePersisting(reason);
    } else {
      assert myTemplate != null;
      myTemplate.schedulePersisting(reason);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final BuildTypeOrTemplate that = (BuildTypeOrTemplate)o;

    if (myBuildType != null ? !myBuildType.equals(that.myBuildType) : that.myBuildType != null) return false;
    if (myTemplate != null ? !myTemplate.equals(that.myTemplate) : that.myTemplate != null) return false;
    return myBuildTypeIdentity.equals(that.myBuildTypeIdentity);

  }

  @Override
  public int hashCode() {
    int result = myBuildType != null ? myBuildType.hashCode() : 0;
    result = 31 * result + (myTemplate != null ? myTemplate.hashCode() : 0);
    result = 31 * result + myBuildTypeIdentity.hashCode();
    return result;
  }

  private BuildTypeOrTemplate() {
    myBuildType = null;
    myTemplate = null;
    myBuildTypeIdentity = null;
  }

  public static class IdsOnly extends BuildTypeOrTemplate {
    @NotNull private final String myId;
    @NotNull private final String myInternalId;

    public IdsOnly(@NotNull final String id, @NotNull final String internalId) {
      myId = id;
      myInternalId = internalId;

    }

    @NotNull
    @Override
    public String getId() {
      return myId;
    }

    @NotNull
    @Override
    public String getInternalId() {
      return myInternalId;
    }
  }
}

