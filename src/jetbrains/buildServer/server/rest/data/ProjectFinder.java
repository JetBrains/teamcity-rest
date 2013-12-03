package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.BuildProject;
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

  public static boolean isSameOrParent(@NotNull final BuildProject parent, @NotNull final BuildProject project) {
    if (parent.getProjectId().equals(project.getProjectId())) return true;
    if (project.getParentProject() == null) return false;
    return isSameOrParent(parent, project.getParentProject());
  }

  public static String getLocator(final BuildProject project) {
    return Locator.createEmptyLocator().setDimension("id", project.getExternalId()).getStringRepresentation();
  }

  @NotNull
  public SProject getProject(@Nullable String projectLocator) {
    if (StringUtil.isEmpty(projectLocator)) {
      throw new BadRequestException("Empty project locator is not supported.");
    }

    final Locator locator = new Locator(projectLocator, "id", "name", "parentProject", "internalId", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's a name or internal id or external id
      SProject project = null;
      @SuppressWarnings("ConstantConditions") @NotNull final String singleValue = locator.getSingleValue();
      project = myProjectManager.findProjectByExternalId(singleValue);
      if (project != null) {
        return project;
      }
      final List<SProject> projectsByName = findProjectsByName(getRootProject(), singleValue);
      if (projectsByName.size() == 1) {
        project = projectsByName.get(0);
        if (project != null) {
          return project;
        }
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
    if (name != null) {
      final String parentProjectLocator = locator.getSingleDimensionValue("parentProject");
      @NotNull SProject parentProject = getRootProject();
      if (parentProjectLocator != null){
        parentProject = getProject(parentProjectLocator);
      }
      final SProject projectByName = getProjectByName(parentProject, name);
      locator.checkLocatorFullyProcessed();
      return projectByName;
    }
    locator.checkLocatorFullyProcessed();
    throw new BadRequestException("Project locator '" + projectLocator + "' is not supported.");
  }

  @NotNull
  public SProject getRootProject() {
    return myProjectManager.getRootProject();
  }

  @NotNull
  private SProject getProjectByName(@NotNull final SProject parentProject, @NotNull final String name) {
    final List<SProject> projectsByName = findProjectsByName(parentProject, name);
    if (projectsByName.size() == 0) {
      throw new NotFoundException("No project cannot be found by name '" + name + "'.");
    }
    if (projectsByName.size() > 1) {
      throw new NotFoundException(
        "Several projects are found by name '" + name + "': " + getPresentable(projectsByName) + ", specify 'parentProject' or 'id' to match exactly one.");
    }
    return projectsByName.get(0);
  }

  private String getPresentable(final List<SProject> projects) {
    final StringBuilder sb = new StringBuilder();
    if (projects.size() > 0) {
      sb.append("[");
      boolean firstItem = true;
      for (SProject project : projects) {
        if (firstItem){
          firstItem = false;
        }else{
          sb.append(", ");
        }
        sb.append(project.getExtendedFullName());
      }
      sb.append("]");
      return sb.toString();
    }
    return "<empty>";
  }

  @NotNull
  private List<SProject> findProjectsByName(@NotNull SProject parentProject, @NotNull final String name) {
    final ArrayList<SProject> result = new ArrayList<SProject>();
    for (SProject project : parentProject.getProjects()) {
      if (name.equals(project.getName())){
        result.add(project);
      }
    }
    return result;
  }

  @Nullable
  public SProject getProjectIfNotNull(@Nullable final String projectLocator) {
    return projectLocator == null ? null : getProject(projectLocator);
  }

  @Nullable
  public BuildProject findProjectByInternalId(final String projectInternalId) {
    return myProjectManager.findProjectById(projectInternalId);
  }
}
