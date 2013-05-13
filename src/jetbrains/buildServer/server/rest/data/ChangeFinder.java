package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SBuildType;
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

  @NotNull private final DataProvider myDataProvider;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final VcsRootFinder myVcsRootFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final VcsManager myVcsManager;

  public ChangeFinder(@NotNull final DataProvider dataProvider,
                      @NotNull final ProjectFinder projectFinder,
                      @NotNull final BuildFinder buildFinder,
                      @NotNull final BuildTypeFinder buildTypeFinder,
                      @NotNull final VcsRootFinder vcsRootFinder,
                      @NotNull final UserFinder userFinder,
                      @NotNull final VcsManager vcsManager) {
    myDataProvider = dataProvider;
    myProjectFinder = projectFinder;
    myBuildFinder = buildFinder;
    myBuildTypeFinder = buildTypeFinder;
    myVcsRootFinder = vcsRootFinder;
    myUserFinder = userFinder;
    myVcsManager = vcsManager;
  }

  public static String[] getChangesLocatorSupportedDimensions(){
    return new String[]{"project", "buildType", "build", "vcsRoot", "username", "user", "personal", "version", "internalVersion", "comment", "file", "sinceChange", "start", "count", "lookupLimit"};
  }

  @NotNull
  public PagedSearchResult<SVcsModification> getModifications(@NotNull Locator locator) {
    ChangesFilter changesFilter;
    final SBuildType buildType = myBuildTypeFinder.getBuildTypeIfNotNull(locator.getSingleDimensionValue("buildType"));
    final String userLocator = locator.getSingleDimensionValue("user");
    final Long count = locator.getSingleDimensionValueAsLong("count");
    changesFilter = new ChangesFilter(myProjectFinder.getProjectIfNotNull(locator.getSingleDimensionValue("project")),
                                      buildType,
                                      myBuildFinder.getBuildIfNotNull(buildType, locator.getSingleDimensionValue("build")),
                                      locator.getSingleDimensionValue("vcsRoot") == null ? null : myVcsRootFinder.getVcsRootInstance(locator.getSingleDimensionValue("vcsRoot")),
                                      getChangeIfNotNull(locator.getSingleDimensionValue("sinceChange")),
                                      locator.getSingleDimensionValue("username"),
                                      userLocator == null ? null : myUserFinder.getUser(userLocator),
                                      locator.getSingleDimensionValueAsBoolean("personal"),
                                      locator.getSingleDimensionValue("version"),
                                      locator.getSingleDimensionValue("internalVersion"),
                                      locator.getSingleDimensionValue("comment"),
                                      locator.getSingleDimensionValue("file"),
                                      locator.getSingleDimensionValueAsLong("start"),
                                      count == null ? null : count.intValue(),
                                      locator.getSingleDimensionValueAsLong("lookupLimit"));
    return new PagedSearchResult<SVcsModification>(changesFilter.getMatchingChanges(myVcsManager.getVcsHistory()), changesFilter.getStart(), changesFilter.getCount());
  }

  @NotNull
  public SVcsModification getChange(final String changeLocator) {
    if (StringUtil.isEmpty(changeLocator)) {
      throw new BadRequestException("Empty change locator is not supported.");
    }

    final Locator locator = new Locator(changeLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      @SuppressWarnings("ConstantConditions") SVcsModification modification = myVcsManager.findModificationById(locator.getSingleValueAsLong(), false);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + changeLocator + "'.");
      }
      return modification;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    Boolean isPersonal = locator.getSingleDimensionValueAsBoolean("personal", false);
    if (isPersonal == null) {
      throw new BadRequestException("Only true/false values are supported for 'personal' dimension. Was: '" +
                                    locator.getSingleDimensionValue("personal") + "'");
    }

    if (id != null) {
      SVcsModification modification = myVcsManager.findModificationById(id, isPersonal);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + locator.getSingleDimensionValue("id") + "' (searching " +
                                    (isPersonal ? "personal" : "non-personal") + " changes).");
      }
      return modification;
    }
    throw new NotFoundException("VCS root locator '" + changeLocator + "' is not supported.");
  }

  @Nullable
  public SVcsModification getChangeIfNotNull(@Nullable final String ChangeLocator) {
    return ChangeLocator == null ? null : getChange(ChangeLocator);
  }
}
