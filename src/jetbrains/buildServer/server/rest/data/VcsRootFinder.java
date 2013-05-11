package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class VcsRootFinder{
  private static final Logger LOG = Logger.getInstance(VcsRootFinder.class.getName());
  @NotNull private final VcsManager myVcsManager;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectManager myProjectManager;

  public VcsRootFinder(@NotNull VcsManager vcsManager,
                       @NotNull ProjectFinder projectFinder,
                       @NotNull BuildTypeFinder buildTypeFinder,
                       @NotNull ProjectManager projectManager) {
    myVcsManager = vcsManager;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectManager = projectManager;
  }

  @NotNull
  public SVcsRoot getVcsRoot(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty VCS root locator is not supported.");
    }
    final Locator locator = createVcsRootLocator(locatorText);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's an internal id or external id
      return getVcsRootByExternalOrInternalId(locator.getSingleValue());
    }

    locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
    final PagedSearchResult<SVcsRoot> vcsRoots = getVcsRoots(locator);
    if (vcsRoots.myEntries.size() == 0) {
      throw new NotFoundException("No VCS roots are found by locator '" + locatorText + "'.");
    }
    assert vcsRoots.myEntries.size()== 1;
    return vcsRoots.myEntries.get(0);
  }

  @NotNull
  private SVcsRoot getVcsRootByExternalOrInternalId(final String id) {
    assert id != null;
    SVcsRoot vcsRoot = myProjectManager.findVcsRootByExternalId(id);
    if (vcsRoot != null){
      return vcsRoot;
    }
    try {
      vcsRoot = myProjectManager.findVcsRootById(Long.parseLong(id));
      if (vcsRoot != null){
        return vcsRoot;
      }
    } catch (NumberFormatException e) {
      //ignore
    }
    throw new NotFoundException("No VCS root found by internal or external id '" + id + "'.");
  }

  @NotNull
  public static Locator createVcsRootLocator(@Nullable final String locatorText){
    final Locator result =
      new Locator(locatorText, "id", "name", "type", "project", VcsRootsFilter.REPOSITORY_ID_STRING, "internalId", "count", "start", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    return result;
  }

  @NotNull
  public static Locator createVcsRootInstanceLocator(@Nullable final String locatorText){
    final Locator result =
      new Locator(locatorText, "id", "name", "type", "project", "buildType", VcsRootInstancesFilter.VCS_ROOT_DIMENSION, VcsRootInstancesFilter.REPOSITORY_ID_STRING, "count", "start", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    return result;
  }

  public PagedSearchResult<SVcsRoot> getVcsRoots(@Nullable final Locator locator) {
    if (locator == null) {
      return new PagedSearchResult<SVcsRoot>(myVcsManager.getAllRegisteredVcsRoots(), null, null);
    }

    if (locator.isSingleValue()){
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(getVcsRootByExternalOrInternalId(locator.getSingleValue())), null, null);
    }

    String externalId = locator.getSingleDimensionValue("id");
    if (externalId != null) {
      SVcsRoot root;
      if (TeamCityProperties.getBoolean(APIController.REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL)) {
        root = getVcsRootByExternalOrInternalId(externalId);
      } else {
        root = myProjectManager.findVcsRootByExternalId(externalId);
        if (root == null) {
          throw new NotFoundException("No VCS root can be found by id '" + externalId + "'.");
        }
        final Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
        if (count != null && count != 1) {
          throw new BadRequestException("Dimension 'id' is specified and 'count' is not 1.");
        }
      }
      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(root), null, null);
    }

    Long internalId = locator.getSingleDimensionValueAsLong("internalId");
    if (internalId != null) {
      SVcsRoot root = myVcsManager.findRootById(internalId);
      if (root == null) {
        throw new NotFoundException("No VCS root can be found by internal id '" + internalId + "'.");
      }
      final Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
      if (count != null && count != 1) {
        throw new BadRequestException("Dimension 'internalId' is specified and 'count' is not 1.");
      }
      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(root), null, null);
    }

    String rootName = locator.getSingleDimensionValue("name");
    if (rootName != null) {
      SVcsRoot root = myVcsManager.findRootByName(rootName);
      if (root == null) {
        throw new NotFoundException("No VCS root can be found by name '" + rootName + "'.");
      }
      final Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
      if (count != null && count != 1) {
        throw new BadRequestException("Dimension 'name' is specified and 'count' is not 1.");
      }
      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<SVcsRoot>(Collections.singletonList(root), null, null);
    }

    VcsRootsFilter filter = new VcsRootsFilter(locator, myProjectFinder, myVcsManager);
    locator.checkLocatorFullyProcessed();

    return new PagedSearchResult<SVcsRoot>(getVcsRoots(filter), filter.getStart(), filter.getCount());
  }

  private List<SVcsRoot> getVcsRoots(final VcsRootsFilter filter) {
    final FilterItemProcessor<SVcsRoot> filterItemProcessor = new FilterItemProcessor<SVcsRoot>(filter);
    AbstractFilter.processList(myVcsManager.getAllRegisteredVcsRoots(), filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  @NotNull
  public VcsRootInstance getVcsRootInstance(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty VCS root intance locator is not supported.");
    }
    final Locator locator = createVcsRootInstanceLocator(locatorText);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root instance id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting VCS root instance id, found empty value.");
      }
      VcsRootInstance root = myVcsManager.findRootInstanceById(parsedId);
      if (root == null) {
        throw new NotFoundException("No VCS root instance can be found by id '" + parsedId + "'.");
      }
      locator.checkLocatorFullyProcessed();
      return root;
    }

    locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
    final PagedSearchResult<VcsRootInstance> vcsRoots = getVcsRootInstances(locator);
    if (vcsRoots.myEntries.size() == 0) {
      throw new NotFoundException("No VCS root instances are found by locator '" + locatorText + "'.");
    }
    assert vcsRoots.myEntries.size()== 1;
    return vcsRoots.myEntries.get(0);
  }

  public PagedSearchResult<VcsRootInstance> getVcsRootInstances(@Nullable final Locator locator) {
    if (locator == null) {
       return new PagedSearchResult<VcsRootInstance>(getAllVcsRootInstances(myProjectManager), null, null);
     }

     if (locator.isSingleValue()){
       throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported.");
     }

     Long rootId = locator.getSingleDimensionValueAsLong("id");
     if (rootId != null) {
       VcsRootInstance root = myVcsManager.findRootInstanceById(rootId);
       if (root == null) {
         throw new NotFoundException("No VCS root instance root can be found by id '" + rootId + "'.");
       }
       locator.checkLocatorFullyProcessed();
       return new PagedSearchResult<VcsRootInstance>(Collections.singletonList(root), null, null);
     }

    VcsRootInstancesFilter filter = new VcsRootInstancesFilter(locator, myProjectFinder, myBuildTypeFinder, this, myVcsManager);
    locator.checkLocatorFullyProcessed();

    return new PagedSearchResult<VcsRootInstance>(getVcsRootInstances(filter), filter.getStart(), filter.getCount());
  }


  private List<VcsRootInstance> getVcsRootInstances(final VcsRootInstancesFilter filter) {
    //todo: current implementation is not effective: consider pre-filtering by vcs root, project, type, if specified
    final FilterItemProcessor<VcsRootInstance> filterItemProcessor = new FilterItemProcessor<VcsRootInstance>(filter);
    AbstractFilter.processList(getAllVcsRootInstances(myProjectManager), filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  private List<VcsRootInstance> getAllVcsRootInstances(ProjectManager projectManager) {
    //todo: (TeamCity) open API is there a better way to do this?
    final Set<VcsRootInstance> rootInstancesSet = new LinkedHashSet<VcsRootInstance>();
    for (SBuildType buildType : projectManager.getAllBuildTypes()) {
        rootInstancesSet.addAll(buildType.getVcsRootInstances());
    }
    final List<VcsRootInstance> result = new ArrayList<VcsRootInstance>(rootInstancesSet.size());
    result.addAll(rootInstancesSet);
    Collections.sort(result, new Comparator<VcsRootInstance>() {
      public int compare(final VcsRootInstance o1, final VcsRootInstance o2) {
        return (int)(o1.getId() - o2.getId());
      }
    });
    return result;
  }
}
