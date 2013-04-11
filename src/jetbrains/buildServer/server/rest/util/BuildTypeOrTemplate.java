package jetbrains.buildServer.server.rest.util;

import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 14.01.12
 */
public class BuildTypeOrTemplate implements Loggable {
  final private SBuildType myBuildType;
  final private BuildTypeTemplate myTemplate;
  final private  boolean hasBuildType;

  public BuildTypeOrTemplate(@NotNull SBuildType buildType) {
    myBuildType = buildType;
    myTemplate = null;
    hasBuildType = true;
  }

  public BuildTypeOrTemplate(@NotNull BuildTypeTemplate template) {
    myTemplate = template;
    myBuildType = null;
    hasBuildType = false;
  }

  @NotNull
  public BuildTypeSettings get(){
    return hasBuildType ? myBuildType : myTemplate;
  }

  @NotNull
  public String getId(){
    return hasBuildType ? myBuildType.getExternalId() : myTemplate.getExternalId();
  }

  @NotNull
  public String getInternalId(){
    return hasBuildType ? myBuildType.getBuildTypeId() : myTemplate.getId();
  }

  public String getName(){
    return hasBuildType ? myBuildType.getName() : myTemplate.getName();
  }

  @NotNull
  public SProject getProject() {
    return hasBuildType ? myBuildType.getProject() : myTemplate.getParentProject();
  }

  public boolean isBuildType() {
    return hasBuildType;
  }

  public boolean isTemplate() {
    return !hasBuildType;
  }

  public SBuildType getBuildType() {
    return myBuildType;
  }

  public BuildTypeTemplate getTemplate() {
    return myTemplate;
  }

  @Nullable
  public String getDescription() {
    return hasBuildType ? myBuildType.getDescription() : null;
  }

  @Nullable
  public Boolean isPaused() {
    return hasBuildType ? myBuildType.isPaused() : null;
  }

  @NotNull
  public String getText() {
    return hasBuildType ? "Build type": "Template";
  }

  public void setName(final String value) {
    if (hasBuildType){
      myBuildType.setName(value);
    }else{
      myTemplate.setName(value);
    }
  }

  public void setDescription(final String value) {
    if (hasBuildType){
      myBuildType.setDescription(value);
    }else{
      throw new BadRequestException("Template does not have description field");
    }
  }

  public void setFieldValue(final String field, final String value, @NotNull final DataProvider dataProvider) {
    if ("name".equals(field)) {
      setName(value);
      return;
    } else if ("description".equals(field)) {
      setDescription(value);
      return;
    }
    if (isBuildType()){
      if ("paused".equals(field)){
        myBuildType.setPaused(Boolean.valueOf(value), dataProvider.getCurrentUser(), TeamCityProperties.getProperty("rest.defaultActionComment"));
        //todo (TeamCity) why not use current user by default?
        return;
      }
    }
    
    throw new BadRequestException("Setting field '" + field + "' is not supported. Supported are: name, description, paused");
  }

  @Nullable
  public String getFieldValue(final String field) {
    if ("id".equals(field)) {
      return getId();
    } else if ("internalId".equals(field)) {
      return getInternalId();
    } else if ("description".equals(field)) {
      return getDescription();
    } else if ("name".equals(field)) {
      return getName();
    }
    if (isBuildType()){
      if ("paused".equals(field)){
        return String.valueOf(myBuildType.isPaused());
      }
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, name, description, paused");
  }

  public boolean isEnabled(final String id) {
    return hasBuildType ? myBuildType.isEnabled(id) : myTemplate.isEnabled(id);
  }

  @NotNull
  public String describe(final boolean verbose) {
      return hasBuildType ? LogUtil.describe(myBuildType) : LogUtil.describe(myTemplate);
  }
}

