package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
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
      // no dimensions found
      if (project != null) {
        // assume it's a name
        return findBuildTypeByName(project, buildTypeLocator);
      } else {
        //assume it's id
        return findBuildTypeOrTemplateByGeneralId(buildTypeLocator);
      }
    }

    String id = locator.getSingleDimensionValue("id");
    if (!StringUtil.isEmpty(id)) {
      assert id != null;
      BuildTypeOrTemplate buildType = findBuildTypeOrTemplateByGeneralId(id);
      if (project != null && !buildType.getProject().equals(project)) {
        throw new NotFoundException(buildType.getText() + " with id '" + id + "' does not belong to project " + project + ".");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return buildType;
    }

    String name = locator.getSingleDimensionValue("name");
    if (name != null) {
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      return findBuildTypeByName(project, name);
    }
    throw new BadRequestException("Build type locator '" + buildTypeLocator + "' is not supported.");
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
  private static BuildTypeOrTemplate getBuildTypeOrTemplateByName(@NotNull final SProject project, @NotNull final String name) {
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
   * @param project project to search build type in. Can be 'null' to search in all the build types on the server.
   * @param name    name of the build type to search for.
   * @return build type with the name 'name'. If 'project' is not null, the search is performed only within 'project'.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException
   *          if several build types with the same name are found
   */
  @NotNull
  private BuildTypeOrTemplate findBuildTypeByName(@Nullable final SProject fixedProject, @NotNull final String name) {
    if (fixedProject != null) {
      final BuildTypeOrTemplate result = getBuildTypeOrTemplateByName(fixedProject, name);
      if (result != null){
        return result;
      }
      throw new NotFoundException("No build type or template is found by name '" + name + "' in project '" + fixedProject.getName() +"'.");
    }

    final List<SProject> projects = myDataProvider.getServer().getProjectManager().getProjects();
    BuildTypeOrTemplate firstFound = null;
    for (SProject project : projects) {
      final BuildTypeOrTemplate found = getBuildTypeOrTemplateByName(project, name);
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

  @NotNull
  private BuildTypeOrTemplate findBuildTypeOrTemplateByGeneralId(@NotNull final String id) {
    if (!id.startsWith(TEMPLATE_ID_PREFIX)){
      SBuildType buildType = myDataProvider.getServer().getProjectManager().findBuildTypeById(id);
      if (buildType == null) {
        final BuildTypeTemplate buildTypeTemplate = myDataProvider.getServer().getProjectManager().findBuildTypeTemplateById(id);
        if (buildTypeTemplate == null){
          throw new NotFoundException("No build type nor template is found by id '" + id + "'.");
        }
        return new BuildTypeOrTemplate(buildTypeTemplate);
      }
      return new BuildTypeOrTemplate(buildType);

    }
    String templateId = id.substring(TEMPLATE_ID_PREFIX.length());
    final BuildTypeTemplate buildTypeTemplate = myDataProvider.getServer().getProjectManager().findBuildTypeTemplateById(templateId);
    if (buildTypeTemplate == null) {
      throw new NotFoundException("No build type template is found by id '" + templateId + "'.");
    }
    return new BuildTypeOrTemplate(buildTypeTemplate);
  }
}
