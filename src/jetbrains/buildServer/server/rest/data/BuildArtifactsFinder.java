package jetbrains.buildServer.server.rest.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.files.File;
import jetbrains.buildServer.server.rest.model.files.FileApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.files.Files;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactElement;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 27.04.13
 */
public class BuildArtifactsFinder {
  public static final String ARCHIVES_DIMENSION_NAME = "browseArchives";
  public static final String HIDDEN_DIMENSION_NAME = "hidden";
  public static final String DIRECTORY_DIMENSION_NAME = "directory";
  @NotNull private final DataProvider myDataProvider;

  public BuildArtifactsFinder(@NotNull final DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public static StreamingOutput getStreamingOutput(@NotNull final BuildArtifact artifact) {
    return new StreamingOutput() {
        public void write(final OutputStream output) throws WebApplicationException {
          InputStream inputStream = null;
          try {
            inputStream = artifact.getInputStream();
            TCStreamUtil.writeBinary(inputStream, output);
          } catch (IOException e) {
            //todo add better processing
            throw new OperationException("Error while processing file '" + artifact.getRelativePath() + "' content: " + e.getMessage(), e);
          } finally {
            FileUtil.close(inputStream);
          }
        }
      };
  }

  public Files getFiles(@NotNull final SBuild build, @NotNull final String path, @Nullable final String filesLocator, @NotNull final BeanContext context) {
    @Nullable final Locator locator =
      StringUtil.isEmpty(filesLocator) ? null : new Locator(filesLocator, HIDDEN_DIMENSION_NAME, ARCHIVES_DIMENSION_NAME, DIRECTORY_DIMENSION_NAME);
    final BuildArtifactsViewMode viewMode = getViewMode(locator, build, context);
    final ArtifactTreeElement element = getArtifactElement(build, path, viewMode);
    try {
      final Iterable<Element> children = element.getChildren();
      if (element.isLeaf() || children == null) {
        throw new BadRequestException("Cannot provide children list for file '" + path + "'. To get content use '" +
                                      fileApiUrlBuilderForBuild(context.getContextService(ApiUrlBuilder.class), build, null).getContentHref(element) + "'.");
      }

      final List<File> result = new ArrayList<File>();
      for (Element artifactItem : children) {
      // children is a collection of ArtifactTreeElement, but we ensure it.
        final ArtifactTreeElement ate = artifactItem instanceof ArtifactTreeElement
                                        ? (ArtifactTreeElement)artifactItem
                                        : getArtifactElement(build, artifactItem.getFullName(), viewMode);
        if (checkInclude(ate, locator)){
          result.add(new File(ate, null, fileApiUrlBuilderForBuild(context.getContextService(ApiUrlBuilder.class), build, filesLocator)));
        }
      }
      if (locator != null) locator.checkLocatorFullyProcessed();
      return new Files(result);
    } catch (BrowserException e) {
      throw new OperationException("Error listing children for artifact '" + path + "'.", e);
    }
  }

  private static boolean checkInclude(@NotNull final ArtifactTreeElement artifact, @Nullable final Locator locator) {
    if (locator == null){
      return true;
    }
    final Boolean directory = locator.getSingleDimensionValueAsBoolean(DIRECTORY_DIMENSION_NAME);
    return FilterUtil.isIncludedByBooleanFilter(directory, !artifact.isLeaf() && !artifact.isContentAvailable());
  }

  private BuildArtifactsViewMode getViewMode(@Nullable final Locator locator, @NotNull final SBuild build, @NotNull final BeanContext context) {
    if (locator == null){
      return BuildArtifactsViewMode.VIEW_DEFAULT_WITH_ARCHIVES_CONTENT;
    }

    final Boolean viewHidden = locator.getSingleDimensionValueAsBoolean(HIDDEN_DIMENSION_NAME, false);
    final Boolean browseArchivesValue = locator.getSingleDimensionValueAsBoolean(ARCHIVES_DIMENSION_NAME, !(viewHidden != null && viewHidden));
    final boolean browseArchives = browseArchivesValue == null ? true : browseArchivesValue;

    if (viewHidden == null) {
      if (browseArchives) {
        return BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT;
      } else {
        return BuildArtifactsViewMode.VIEW_ALL;
      }
    } else if (!viewHidden) {
      if (browseArchives) {
        return BuildArtifactsViewMode.VIEW_DEFAULT_WITH_ARCHIVES_CONTENT;
      } else {
        return BuildArtifactsViewMode.VIEW_DEFAULT;
      }
    } else if (!browseArchives) {
      myDataProvider.checkProjectPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build.getProjectId());
      return BuildArtifactsViewMode.VIEW_HIDDEN_ONLY;
    }

    throw new BadRequestException("Unsupported combination of '" + HIDDEN_DIMENSION_NAME + "' and '" + ARCHIVES_DIMENSION_NAME + "' dimensions.");
  }

  @NotNull
  public static FileApiUrlBuilder fileApiUrlBuilderForBuild(@NotNull final ApiUrlBuilder apiUrlBuilder, @NotNull final SBuild build, @Nullable final String locator) {
    return new FileApiUrlBuilder() {
      private final String myHrefBase = apiUrlBuilder.getHref(build) + BuildRequest.ARTIFACTS;

      public String getMetadataHref(Element e) {
        return myHrefBase + BuildRequest.METADATA + "/" + e.getFullName();
      }

      public String getChildrenHref(Element e) {
        return myHrefBase + BuildRequest.CHILDREN + "/" + e.getFullName() + (locator == null ? "" : "?" + "locator" + "=" + locator);
      }

      public String getContentHref(Element e) {
        return myHrefBase + BuildRequest.CONTENT + "/" + e.getFullName();
      }
    };
  }

  @NotNull
  public static ArtifactTreeElement getArtifactElement(@NotNull final SBuild build, @NotNull final String path, final BuildArtifactsViewMode viewMode) {
    return new ArtifactElement(getBuildArtifact(build, path, viewMode));
  }

  @NotNull
  public static BuildArtifact getBuildArtifact(@NotNull final SBuild build,
                                               @NotNull final String path,
                                               @NotNull final BuildArtifactsViewMode mode) {
    final BuildArtifacts artifacts = build.getArtifacts(mode);
    final BuildArtifactHolder holder = artifacts.findArtifact(path);
    if (!holder.isAvailable()) {
      final BuildArtifactHolder testHolder = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT).findArtifact(path);
      if (testHolder.isAvailable()){
        throw new NotFoundException("No artifact with relative path '" + holder.getRelativePath() + "' found with current view mode." +
                                    " Try adding parameter 'locator=" + HIDDEN_DIMENSION_NAME + ":any' to the request.");
      }else{
        throw new NotFoundException("No artifact with relative path '" + holder.getRelativePath() + "' found in build " + LogUtil.describe(build));
      }
    }
    if (!holder.isAccessible()) {
      throw new AuthorizationFailedException("Artifact is not accessible with current user permissions. Relative path: '" + holder.getRelativePath() + "'");
    }
    return holder.getArtifact();
  }
}
