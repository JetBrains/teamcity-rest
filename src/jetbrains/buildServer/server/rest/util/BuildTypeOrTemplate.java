package jetbrains.buildServer.server.rest.util;

import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 14.01.12
 */
public class BuildTypeOrTemplate {
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
}

