package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
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
  @NotNull private final DataProvider myDataProvider;

  public BuildTypeFinder(@NotNull final DataProvider dataProvider){
    myDataProvider = dataProvider;
  }

  @NotNull
  public BuildTypeOrTemplate getBuildTypeOrTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    if (StringUtil.isEmpty(buildTypeLocator)) {
      throw new BadRequestException("Empty build type locator is not supported.");
    }
    assert buildTypeLocator != null;

    final Locator locator = new Locator(buildTypeLocator);
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
      return findBuildTypeByName(project, buildTypeLocator);
    }

    String internalId = locator.getSingleDimensionValue("internalId");
    if (!StringUtil.isEmpty(internalId)) {
      assert internalId != null;
      //todo: this assumes common namespace for build types and templates
      final Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByInternalId(internalId, template);
      if (buildType != null) {
        checkProjectFilter(project, buildType);
        locator.checkLocatorFullyProcessed();
        return buildType;
      }
      throw new NotFoundException("No " + getName(template) + " is found by internal id '" + internalId + "'.");
    }

    String id = locator.getSingleDimensionValue("id");
    if (!StringUtil.isEmpty(id)) {
      assert id != null;

      final Boolean template = locator.getSingleDimensionValueAsBoolean(TEMPLATE_DIMENSION_NAME);
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByExternalId(id, template);
      if (buildType != null) {
        checkProjectFilter(project, buildType);
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
          checkProjectFilter(project, buildType);
          locator.checkLocatorFullyProcessed();
          return buildType;
        }
        throw new NotFoundException("No " + getName(template) + " is found by id '" + id + "' in compatibility mode." +
                                    " Cannot be found by external or internal id '" + id + "'.");
      }
      throw new NotFoundException("No " + getName(template) + " is found by id '" + id + "'.");
    }

    String name = locator.getSingleDimensionValue("name");
    if (name != null) {
      locator.checkLocatorFullyProcessed();
      return findBuildTypeByName(project, name);
    }
    throw new BadRequestException("Build type locator '" + buildTypeLocator + "' is not supported.");
  }

  private void checkProjectFilter(final SProject project, final BuildTypeOrTemplate buildType) {
    if (project != null && !buildType.getProject().equals(project)) {
      throw new NotFoundException("Found " + LogUtil.describe(buildType) + " but it does not belong to project " + LogUtil.describe(project) + ".");
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
    throw new NotFoundException("No build type is found by locator '" + buildTypeLocator + "' (template is found instead).");
  }

  @NotNull
  public BuildTypeTemplate getBuildTemplate(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    final BuildTypeOrTemplate buildTypeOrTemplate = getBuildTypeOrTemplate(project, buildTypeLocator);
    if (buildTypeOrTemplate.isBuildType()){
      throw new BadRequestException("Could not find template by locator '" + buildTypeLocator + "'. Build type found instead.");
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
  private static BuildTypeOrTemplate getOwnBuildTypeOrTemplateByName(@NotNull final SProject project, @NotNull final String name) {
    final SBuildType buildType = project.findBuildTypeByName(name);
    if (buildType != null) {
      return new BuildTypeOrTemplate(buildType);

    }
    final BuildTypeTemplate buildTypeTemplate = project.findBuildTypeTemplateByName(name);
    if (buildTypeTemplate != null) {
      return new BuildTypeOrTemplate(buildTypeTemplate);
    }
    return null;
  }

  /**
   * @param project project to search build type in (subprojects are also searched). Can be 'null' to search in all the build types on the server.
   * @param name    name of the build type to search for.
   * @return build type with the name 'name'. If 'project' is not null, the search is performed only within 'project'.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException
   *          if several build types with the same name are found
   */
  @NotNull
  private BuildTypeOrTemplate findBuildTypeByName(@Nullable final SProject fixedProject, @NotNull final String name) {
    if (fixedProject != null) {
      BuildTypeOrTemplate result = getOwnBuildTypeOrTemplateByName(fixedProject, name);
      if (result != null){
        return result;
      }
      // try to find in subprojects if not found in the project directly
      result = findBuildTypeinProjects(name, fixedProject.getProjects());
      if (result != null){
        return result;
      }
      throw new NotFoundException("No build type or template is found by name '" + name + "' in project '" + fixedProject.getName() +"'.");
    }

    return findBuildTypeinProjects(name, myDataProvider.getServer().getProjectManager().getProjects());
  }

  private static BuildTypeOrTemplate findBuildTypeinProjects(final String name, final List<SProject> projects) {
    BuildTypeOrTemplate firstFound = null;
    for (SProject project : projects) {
      final BuildTypeOrTemplate found = getOwnBuildTypeOrTemplateByName(project, name);
      if (found != null) {
        if (firstFound != null) {
          throw new BadRequestException("Several matching build types/templates found for name '" + name + "'.");
        }
        firstFound = found;
      }
    }
    if (firstFound != null) {
      return firstFound;
    }
    throw new NotFoundException("No build type or template is found by name '" + name + "'.");
  }

  @Nullable
  private BuildTypeOrTemplate findBuildTypeOrTemplateByInternalId(@NotNull final String internalId, @Nullable final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      SBuildType buildType = myDataProvider.getServer().getProjectManager().findBuildTypeById(internalId);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }
    if (isTemplate == null || isTemplate) {
      final BuildTypeTemplate buildTypeTemplate = myDataProvider.getServer().getProjectManager().findBuildTypeTemplateById(internalId);
      if (buildTypeTemplate != null) {
        return new BuildTypeOrTemplate(buildTypeTemplate);
      }
    }
    return null;
  }

  @Nullable
  private BuildTypeOrTemplate findBuildTypeOrTemplateByExternalId(@NotNull final String internalId, @Nullable final Boolean isTemplate) {
    if (isTemplate == null || !isTemplate) {
      SBuildType buildType = myDataProvider.getServer().getProjectManager().findBuildTypeByExternalId(internalId);
      if (buildType != null) {
        return new BuildTypeOrTemplate(buildType);
      }
    }
    if (isTemplate == null || isTemplate) {
      final BuildTypeTemplate buildTypeTemplate = myDataProvider.getServer().getProjectManager().findBuildTypeTemplateByExternalId(
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
    final BuildTypeTemplate buildTypeTemplateByStrippedId =
      myDataProvider.getServer().getProjectManager().findBuildTypeTemplateById(templateId);
    if (buildTypeTemplateByStrippedId != null) {
      return new BuildTypeOrTemplate(buildTypeTemplateByStrippedId);
    }
    return null;
  }
}
