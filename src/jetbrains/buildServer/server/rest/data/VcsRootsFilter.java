package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.vcs.api.services.tc.VcsMappingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class VcsRootsFilter extends AbstractFilter<SVcsRoot> {
  private static final Logger LOG = Logger.getInstance(VcsRootsFilter.class.getName());
  public static final String REPOSITORY_ID_STRING = "repositoryIdString";

  @Nullable private final String myVcsType;
  @Nullable private final String myRepositoryIdString;
  @Nullable private final SProject myProject;
  private final VcsManager myVcsManager;

  public VcsRootsFilter(@NotNull final Locator locator, @NotNull ProjectFinder projectFinder, @NotNull VcsManager vcsManager) {
    super(locator.getSingleDimensionValueAsLong(PagerData.START),
          locator.getSingleDimensionValueAsLong(PagerData.COUNT) != null ? locator.getSingleDimensionValueAsLong(PagerData.COUNT).intValue() : null,
          null);
    myVcsManager = vcsManager;
    myVcsType = locator.getSingleDimensionValue("type");
    final String projectLocator = locator.getSingleDimensionValue("project");
    if (projectLocator != null) {
      myProject = projectFinder.getProject(projectLocator);
    } else {
      myProject = null;
    }
    myRepositoryIdString = locator.getSingleDimensionValue(REPOSITORY_ID_STRING);
  }

  @Override
  protected boolean isIncluded(@NotNull SVcsRoot root) {
    if (myVcsType != null && !myVcsType.equals(root.getVcsName())) {
      return false;
    }
    if (myProject != null && !myProject.equals(root.getProject())) {
      return false;
    }
    if (myRepositoryIdString != null && !repositoryIdStringMatches(root, myRepositoryIdString, myVcsManager)) {
      return false;
    }

    return true;
  }

  static boolean repositoryIdStringMatches(@NotNull final SVcsRoot root,
                                           @NotNull final String repositoryIdString,
                                           final VcsManager vcsManager) {
    //todo: handle errors
    final VcsSupportCore vcsSupport = vcsManager.findVcsByName(root.getVcsName());
    if (vcsSupport != null) {
      final VcsPersonalSupport personalSupport = ((ServerVcsSupport)vcsSupport).getPersonalSupport();
      if (personalSupport != null) {
        final Collection<String> mapped = personalSupport.mapFullPath(new VcsRootEntry(root, CheckoutRules.DEFAULT), repositoryIdString);
        if (mapped.size() != 0) {
          return true;
        }
      } else {
        LOG.debug("No personal support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
        return false;
      }
    } else {
      LOG.debug("No VCS support for VCS root " + LogUtil.describe(root) + " found, ignoring root in search");
      return false;
    }

    try {
      Collection<VcsMappingElement> vcsMappingElements = VcsRoot.getRepositoryMappings(root, vcsManager);
      for (VcsMappingElement vcsMappingElement : vcsMappingElements) {
        if (repositoryIdString.equals(vcsMappingElement.getFrom())) {
          return true;
        }
      }
    } catch (Exception e) {
      LOG.debug("Error while retrieving mapping for VCS root " + LogUtil.describe(root) + ". ignoring root in search", e);
    }
    return false;
  }


}
