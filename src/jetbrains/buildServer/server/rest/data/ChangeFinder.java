package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 12.05.13
 */
public class ChangeFinder {
  private static final Logger LOG = Logger.getInstance(BuildTypeFinder.class.getName());
  public static final String ID = "id";
  public static final String PERSONAL = "personal";

  @NotNull private final DataProvider myDataProvider;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final VcsRootFinder myVcsRootFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final VcsManager myVcsManager;
  @NotNull private final ServiceLocator myServiceLocator;

  public ChangeFinder(@NotNull final DataProvider dataProvider,
                      @NotNull final ProjectFinder projectFinder,
                      @NotNull final BuildFinder buildFinder,
                      @NotNull final BuildTypeFinder buildTypeFinder,
                      @NotNull final VcsRootFinder vcsRootFinder,
                      @NotNull final UserFinder userFinder,
                      @NotNull final VcsManager vcsManager,
                      @NotNull final ServiceLocator serviceLocator) {
    myDataProvider = dataProvider;
    myProjectFinder = projectFinder;
    myBuildFinder = buildFinder;
    myBuildTypeFinder = buildTypeFinder;
    myVcsRootFinder = vcsRootFinder;
    myUserFinder = userFinder;
    myVcsManager = vcsManager;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public static Locator getChangesLocator(@Nullable String locatorText, boolean returnNotEmpty) {
    if (returnNotEmpty && StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty change locator is not supported.");
    }
    final String[] supported =
      {ID, "project", "buildType", "build", "vcsRoot", "vcsRootInstance", "username", "user", "version", "internalVersion", "comment", "file",
        "sinceChange", "start", "count", "lookupLimit", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME};
    final Locator result;
    if (returnNotEmpty) {
      result = new Locator(locatorText, supported);
    } else {
      result = Locator.createEmptyLocator(supported);
    }
    result.addHiddenDimensions("branch", PERSONAL); //hide these for now
    return result;
  }

  @NotNull
  public PagedSearchResult<SVcsModification> getModifications(@NotNull Locator locator) {
    final SVcsModification singleChange = getSingleChange(locator);
    if (singleChange != null) {
      if (!myDataProvider.checkCanView(singleChange)) {
        throw new AuthorizationFailedException("Current user does not have permission " + Permission.VIEW_PROJECT +
                                               " in any of the projects associated with the change with id: '" + singleChange.getId() + "'");
      }
      final List<SVcsModification> result = Collections.singletonList(singleChange);
      return new PagedSearchResult<SVcsModification>(result, null, null);
    }

    ChangesFilter changesFilter;
    final SBuildType buildType = myBuildTypeFinder.getBuildTypeIfNotNull(locator.getSingleDimensionValue("buildType"));
    final String userLocator = locator.getSingleDimensionValue("user");
    final Long count = locator.getSingleDimensionValueAsLong("count");
    final String vcsRootInstance = locator.getSingleDimensionValue("vcsRootInstance");
    final String vcsRoot = locator.getSingleDimensionValue("vcsRoot");

    final String sinceChangeDimension = locator.getSingleDimensionValue("sinceChange");
    Long sinceChangeId = null;
    if (sinceChangeDimension != null) {
      //if change id - do not find change to support cases when it does not exist
      try {
        sinceChangeId = Long.parseLong(sinceChangeDimension);
      } catch (NumberFormatException e) {
        //not id - proceed as usual
        SVcsModification modification = getChange(sinceChangeDimension);
        sinceChangeId = modification.getId();
      }
    }

    changesFilter = new ChangesFilter(myProjectFinder.getProjectIfNotNull(locator.getSingleDimensionValue("project")),
                                      buildType,
                                      getBranchName(locator.getSingleDimensionValue("branch")),
                                      myBuildFinder.getBuildIfNotNull(buildType, locator.getSingleDimensionValue("build")),
                                      vcsRootInstance == null ? null : myVcsRootFinder.getVcsRootInstance(vcsRootInstance),
                                      vcsRoot == null ? null : myVcsRootFinder.getVcsRoot(vcsRoot),
                                      sinceChangeId,
                                      locator.getSingleDimensionValue("username"),
                                      userLocator == null ? null : myUserFinder.getUser(userLocator),
                                      locator.getSingleDimensionValueAsBoolean(PERSONAL),
                                      locator.getSingleDimensionValue("version"),
                                      locator.getSingleDimensionValue("internalVersion"),
                                      locator.getSingleDimensionValue("comment"),
                                      locator.getSingleDimensionValue("file"),
                                      locator.getSingleDimensionValueAsLong("start"),
                                      count == null ? null : count.intValue(),
                                      locator.getSingleDimensionValueAsLong("lookupLimit"), myDataProvider);
    return new PagedSearchResult<SVcsModification>(changesFilter.getMatchingChanges(myServiceLocator), changesFilter.getStart(), changesFilter.getCount());
  }

  @Nullable
  private String getBranchName(@Nullable final String branch) {
    if (branch == null) {
      return null;
    }
    final Locator branchLocator;
    try {
      branchLocator = new Locator(branch, "name");
      final String result = branchLocator.getSingleDimensionValue("name");
      branchLocator.checkLocatorFullyProcessed();
      return result;
    } catch (LocatorProcessException e) {
      throw new BadRequestException("Error procesing branch locator '" + branch + "'", e);
    }
  }

  @Nullable
  private SVcsModification getSingleChange(@NotNull Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      @SuppressWarnings("ConstantConditions") SVcsModification modification = myVcsManager.findModificationById(locator.getSingleValueAsLong(), false);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + locator.getSingleValueAsLong() + "'.");
      }
      return modification;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {
      Boolean isPersonal = locator.getSingleDimensionValueAsBoolean(PERSONAL, false);
      if (isPersonal == null) {
        throw new BadRequestException("When 'id' dimension is present, only true/false values are supported for '" + PERSONAL + "' dimension. Was: '" +
                                      locator.getSingleDimensionValue(PERSONAL) + "'");
      }

      SVcsModification modification = myVcsManager.findModificationById(id, isPersonal);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + locator.getSingleDimensionValue("id") + "' (searching " +
                                    (isPersonal ? "personal" : "non-personal") + " changes).");
      }
      return modification;
    }

    return null;
  }

  @NotNull
  public SVcsModification getChange(@Nullable final String changeLocator) {
    final Locator locator = getChangesLocator(changeLocator, true);

    if (!locator.isSingleValue()) {
      locator.setDimension("count", String.valueOf(1));
    }
    locator.addIgnoreUnusedDimensions("count");
    final PagedSearchResult<SVcsModification> changes = getModifications(locator);
    locator.checkLocatorFullyProcessed();
    if (changes.myEntries.size() > 0) {
      return changes.myEntries.iterator().next();
    }
    throw new NotFoundException("No changes found by locator '" + changeLocator + "'.");
  }

  @Nullable
  public SVcsModification getChangeIfNotNull(@Nullable final String ChangeLocator) {
    return ChangeLocator == null ? null : getChange(ChangeLocator);
  }
}
