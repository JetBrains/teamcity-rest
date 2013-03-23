package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
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

  public VcsRootFinder(@NotNull VcsManager vcsManager){
    myVcsManager = vcsManager;
  }

  @NotNull
  public SVcsRoot getVcsRoot(final String vcsRootLocator) {
    if (StringUtil.isEmpty(vcsRootLocator)) {
      throw new BadRequestException("Empty VCS root locator is not supported.");
    }

    final Locator locator = new Locator(vcsRootLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root id
      @SuppressWarnings("ConstantConditions") SVcsRoot root = myVcsManager.findRootById(locator.getSingleValueAsLong());
      if (root == null) {
        throw new NotFoundException("No root can be found by id '" + vcsRootLocator + "'.");
      }
      return root;
    }

    Long rootId = locator.getSingleDimensionValueAsLong("id");
    if (rootId != null){
      SVcsRoot root = myVcsManager.findRootById(rootId);
      if (root == null) {
        throw new NotFoundException("No root can be found by id '" + vcsRootLocator + "'.");
      }
      return root;
    }

    String rootName = locator.getSingleDimensionValue("name");
    if (rootName != null) {
      SVcsRoot root = myVcsManager.findRootByName(rootName);
      if (root == null) {
        throw new NotFoundException("No root can be found by name '" + rootName + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("VCS root locator '" + vcsRootLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      return root;
    }

    throw new NotFoundException("VCS root locator '" + vcsRootLocator + "' is not supported.");
  }

  @NotNull
  public VcsRootInstance getVcsRootInstance(@Nullable final String vcsRootLocator) {
    if (StringUtil.isEmpty(vcsRootLocator)) {
      throw new BadRequestException("Empty VCS root instance locator is not supported.");
    }

    final Locator locator = new Locator(vcsRootLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting VCS root instance id, found empty value.");
      }
      VcsRootInstance root = myVcsManager.findRootInstanceById(parsedId);
      if (root == null) {
        throw new NotFoundException("No root instance can be found by id '" + parsedId + "'.");
      }
      return root;
    }

    Long rootId = locator.getSingleDimensionValueAsLong("id");
    if (rootId == null) {
      throw new BadRequestException("No 'id' dimension found in locator '" + vcsRootLocator + "'.");
    }
    VcsRootInstance root = myVcsManager.findRootInstanceById(rootId);
    if (root == null) {
      throw new NotFoundException("No root instance can be found by id '" + rootId + "'.");
    }
    return root;
  }
}
