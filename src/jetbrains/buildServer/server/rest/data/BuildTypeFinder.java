package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class BuildTypeFinder {
  private static final Logger LOG = Logger.getInstance(BuildTypeFinder.class.getName());

  public static final String TEMPLATE_ID_PREFIX = "template:";
  public static final String TEMPLATE_DIMENSION_NAME = "template";
  public static final String DIMENSION_ID = "id";
  public static final String DIMENSION_INTERNAL_ID = "internalId";
  public static final String DIMENSION_PROJECT = "project";
  public static final String DIMENSION_NAME = "name";
  private ProjectFinder myProjectFinder;
  private ProjectManager myProjectManager;

  public BuildTypeFinder(@NotNull final ProjectManager projectManager, @NotNull final ProjectFinder projectFinder){
    myProjectManager = projectManager;
    myProjectFinder = projectFinder;
  }

  @NotNull
  public BuildTypeOrTemplate getBuildTypeOrTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    if (StringUtil.isEmpty(buildTypeLocator)) {
      throw new BadRequestException("Empty build type locator is not supported.");
    }
    assert buildTypeLocator != null;

    final Locator locator = new Locator(buildTypeLocator, DIMENSION_ID, DIMENSION_INTERNAL_ID, DIMENSION_PROJECT, DIMENSION_NAME, TEMPLATE_DIMENSION_NAME, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's an internal id, external id or name
      final String value = locator.getSingleValue();
      assert value != null;
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByInternalId(value, null);
      if (buildType != null){
        checkProjectFilter(project, buildType);
        return buildType;
      }

      buildType = findBuildTypeOrTemplateByExternalId(value, null);
      if (buildType != null){
        checkProjectFilter(project, buildType);
        return buildType;
      }

      // assume it's a name
      final BuildTypeOrTemplate buildTypeByName = findBuildTypeByName(project, value, null);
      if (buildTypeByName != null){
        return buildTypeByName;
      }
      throw new NotFoundException("No build type or template is found by id, internal id or name '" + value + "'.");
    }

    SProject actualProject = project;
    String projectLocator = locator.getSingleDimensionValue(DIMENSION_PROJECT);
    if (projectLocator != null) {
      actualProject = myProjectFinder.getProject(projectLocator);
      if (project != null && !actualProject.equals(project)) {
        throw new BadRequestException(
          "Project in the locator 'project' " + LogUtil.describe(actualProject) + " and context project " + LogUtil.describe(project) + " are different.");
      }
    }

    String internalId = locator.getSingleDimensionValue(DIMENSION_INTERNAL_ID);
    if (!StringUtil.isEmpty(internalId)) {
      assert internalId != null;
      //todo: this assumes common namespace for build types and templates
      final Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByInternalId(internalId, template);
      if (buildType != null) {
        checkProjectFilter(actualProject, buildType);
        locator.checkLocatorFullyProcessed();
        return buildType;
      }
      throw new NotFoundException("No " + getName(template) + " is found by internal id '" + internalId + "'.");
    }

    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (!StringUtil.isEmpty(id)) {
      assert id != null;

      final Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByExternalId(id, template);
      if (buildType != null) {
        checkProjectFilter(actualProject, buildType);
        locator.checkLocatorFullyProcessed();
        return buildType;
      }

      // support pre-8.0 style of template ids
      final BuildTypeOrTemplate templateByOldIdWithPrefix = findTemplateByOldIdWithPrefix(id);
      if (templateByOldIdWithPrefix != null){
        return templateByOldIdWithPrefix;
      }

      if (TeamCityProperties.getBoolean(APIController.REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL)) {
        buildType = findBuildTypeOrTemplateByInternalId(id, template);
        if (buildType != null) {
          checkProjectFilter(actualProject, buildType);
          locator.checkLocatorFullyProcessed();
          return buildType;
        }
        throw new NotFoundException("No " + getName(template) + " is found by id '" + id + "' in compatibility mode." +
                                    " Cannot be found by external or internal id '" + id + "'.");
      }
      throw new NotFoundException("No " + getName(template) + " is found by id '" + id + "'.");
    }

    String name = locator.getSingleDimensionValue(DIMENSION_NAME);
    if (name != null) {
      final Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
      locator.checkLocatorFullyProcessed();
      final BuildTypeOrTemplate buildTypeByName = findBuildTypeByName(actualProject, name, template);
      if (buildTypeByName != null){
        return buildTypeByName;
      }
      throw new NotFoundException(
        "No " + getName(template) + " is found by name '" + name + "'" + (actualProject != null ? " in project '" + LogUtil.describe(actualProject) + "'" : "") + ".");
    }

    locator.checkLocatorFullyProcessed();
    throw new BadRequestException("Build type locator '" + buildTypeLocator + "' is not supported.");
  }

  private void checkProjectFilter(final SProject project, final BuildTypeOrTemplate buildType) {
    if (project != null && !buildType.getProject().equals(project)) {
      throw new BadRequestException("Found " + LogUtil.describe(buildType) + " but it does not belong to project " + LogUtil.describe(project) + ".");
    }
  }

  private String getName(final Boolean template) {
    if (template == null) {
      return "build type nor template";
    }
    return template ? "template" : "build type";
  }

  @NotNull
  public SBuildType getBuildType(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator);
    if (buildTypeOrTemplate.isBuildType()){
      return buildTypeOrTemplate.getBuildType();
    }
    throw new NotFoundException("No build type is found by locator '" + buildTypeLocator + "'. Template is found instead.");
  }

  @NotNull
  public BuildTypeTemplate getBuildTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator);
    if (buildTypeOrTemplate.isBuildType()){
      throw new BadRequestException("No build type template by locator '" + buildTypeLocator + "'. Build type is found instead.");
    }
    return buildTypeOrTemplate.getTemplate();
  }

  @Nullable
  public SBuildType getBuildTypeIfNotNull(@Nullable final String buildTypeLocator) {
    return buildTypeLocator == null ? null : getBuildType(null, buildTypeLocator);
  }

  @Nullable
  public SBuildType deriveBuildTypeFromLocator(@Nullable SBuildType contextBuildType, @Nullable final String buildTypeLocator) {
    if (buildTypeLocator != null) {
      final SBuildType buildTypeFromLocator = getBuildType(null, buildTypeLocator);
      if (contextBuildType == null) {
        return buildTypeFromLocator;
      } else if (!contextBuildType.getBuildTypeId().equals(buildTypeFromLocator.getBuildTypeId())) {
        throw new BadRequestException("Explicit build type (" + contextBuildType.getBuildTypeId() +
                                      ") does not match build type in 'buildType' locator (" + buildTypeLocator + ").");
      }
    }
    return contextBuildType;
  }


  @Nullable
  private static BuildTypeOrTemplate getOwnBuildTypeOrTemplateByName(@NotNull final SProject project, @NotNull final String name, final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      final SBuildType buildType = project.findBuildTypeByName(name);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
      if (isTemplate != null) return null;
    }
    final BuildTypeTemplate buildTypeTemplate = project.findBuildTypeTemplateByName(name);
    if (buildTypeTemplate != null) {
      return new BuildTypeOrTemplate(buildTypeTemplate);
    }
    return null;
  }

  /**
   *
   * @param project project to search build type in (subprojects are also searched). Can be 'null' to search in all the build types on the server.
   * @param name    name of the build type to search for.
   * @param isTemplate null to search for both build types and temapltes or true/false
   * @return build type with the name 'name'. If 'project' is not null, the search is performed only within 'project'.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException if several build types with the same name are found
   */
  @Nullable
  private BuildTypeOrTemplate findBuildTypeByName(@Nullable final SProject project, @NotNull final String name, final Boolean isTemplate) {
    if (project != null) {
      BuildTypeOrTemplate result = getOwnBuildTypeOrTemplateByName(project, name, isTemplate);
      if (result != null){
        return result;
      }
      // try to find in subprojects if not found in the project directly
      return findBuildTypeinProjects(name, project.getProjects(), isTemplate);
    }

    return findBuildTypeinProjects(name, myProjectManager.getProjects(), isTemplate);
  }

  @Nullable
  private static BuildTypeOrTemplate findBuildTypeinProjects(final String name, final List<SProject> projects, final Boolean isTemplate) {
    BuildTypeOrTemplate firstFound = null;
    for (SProject project : projects) {
      final BuildTypeOrTemplate found = getOwnBuildTypeOrTemplateByName(project, name, isTemplate);
      if (found != null) {
        if (firstFound != null) {
          throw new BadRequestException("Several matching build types/templates found for name '" + name + "'.");
        }
        firstFound = found;
      }
    }
    return firstFound;
  }

  @Nullable
  private BuildTypeOrTemplate findBuildTypeOrTemplateByInternalId(@NotNull final String internalId, @Nullable final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      SBuildType buildType = myProjectManager.findBuildTypeById(internalId);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }
    if (isTemplate == null || isTemplate) {
      final BuildTypeTemplate buildTypeTemplate = myProjectManager.findBuildTypeTemplateById(internalId);
      if (buildTypeTemplate != null) {
        return new BuildTypeOrTemplate(buildTypeTemplate);
      }
    }
    return null;
  }

  @Nullable
  private BuildTypeOrTemplate findBuildTypeOrTemplateByExternalId(@NotNull final String internalId, @Nullable final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      SBuildType buildType = myProjectManager.findBuildTypeByExternalId(internalId);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }
    if (isTemplate == null || isTemplate) {
      final BuildTypeTemplate buildTypeTemplate = myProjectManager.findBuildTypeTemplateByExternalId(
        internalId);
      if (buildTypeTemplate != null) {
        return new BuildTypeOrTemplate(buildTypeTemplate);
      }
    }
    return null;
  }

  @Nullable
  private BuildTypeOrTemplate findTemplateByOldIdWithPrefix(@NotNull final String idWithPrefix) {
    if (!idWithPrefix.startsWith(TEMPLATE_ID_PREFIX)) {
      return null;
    }

    String templateId = idWithPrefix.substring(TEMPLATE_ID_PREFIX.length());
    final BuildTypeTemplate buildTypeTemplateByStrippedId = myProjectManager.findBuildTypeTemplateById(templateId);
    if (buildTypeTemplateByStrippedId != null) {
      return new BuildTypeOrTemplate(buildTypeTemplateByStrippedId);
    }
    return null;
  }
}
