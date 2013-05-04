package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class ProjectFinder {
  private static final Logger LOG = Logger.getInstance(ProjectFinder.class.getName());

  @NotNull private final ProjectManager myProjectManager;

  public ProjectFinder(@NotNull final ProjectManager projectManager){
    myProjectManager = projectManager;
  }

  @NotNull
  public SProject getProject(@Nullable String projectLocator) {
    if (StringUtil.isEmpty(projectLocator)) {
      throw new BadRequestException("Empty project locator is not supported.");
    }

    final Locator locator = new Locator(projectLocator);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's a name or internal id or external id
      SProject project=null;
      final String singleValue = locator.getSingleValue();
      project = myProjectManager.findProjectByExternalId(singleValue);
      if (project != null) {
        return project;
      }
      project = myProjectManager.findProjectByName(singleValue);
      if (project != null) {
        return project;
      }
      project = myProjectManager.findProjectById(singleValue);
      if (project != null) {
        return project;
      }
      throw new NotFoundException("No project found by name or internal/external id '" + singleValue + "'.");
    }

    String id = locator.getSingleDimensionValue("id");
    if (id != null) {
      SProject project = myProjectManager.findProjectByExternalId(id);
      if (project == null) {
        if (TeamCityProperties.getBoolean(APIController.REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL)){
          project = myProjectManager.findProjectById(id);
          if (project == null) {
            throw new NotFoundException("No project found by locator '" + projectLocator +
                                        "' in compatibility mode. Project cannot be found by external or internal id '" + id + "'.");
          }
        }else{
          throw new NotFoundException("No project found by locator '" + projectLocator + "'. Project cannot be found by external id '" + id + "'.");
        }
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      locator.checkLocatorFullyProcessed();
      return project;
    }

    String internalId = locator.getSingleDimensionValue("internalId");
    if (internalId != null) {
      SProject project = myProjectManager.findProjectById(internalId);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + projectLocator + "'. Project cannot be found by internal id '" + internalId + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'internalId' dimension and others. Others are ignored.");
      }
      locator.checkLocatorFullyProcessed();
      return project;
    }

    String name = locator.getSingleDimensionValue("name");
    //todo: support parent project locator here
    if (name != null) {
      SProject project = myProjectManager.findProjectByName(name);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + projectLocator + "'. Project cannot be found by name '" + name + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      locator.checkLocatorFullyProcessed();
      return project;
    }
    locator.checkLocatorFullyProcessed();
    throw new BadRequestException("Project locator '" + projectLocator + "' is not supported.");
  }

  @Nullable
  public SProject getProjectIfNotNull(@Nullable final String projectLocator) {
    return projectLocator == null ? null : getProject(projectLocator);
  }
}
