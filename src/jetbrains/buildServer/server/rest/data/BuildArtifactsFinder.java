/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.data;

import com.google.common.collect.Iterators;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.files.File;
import jetbrains.buildServer.server.rest.model.files.FileApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.files.Files;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactElement;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import jetbrains.buildServer.web.util.HttpByteRange;
import jetbrains.buildServer.web.util.WebUtil;
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
  public static final String DIMENSION_RECURSIVE = "recursive";
  public static final String DIMENSION_PATTERNS = "patterns";
  @NotNull private final PermissionChecker myPermissionChecker;

  public BuildArtifactsFinder(@NotNull final PermissionChecker permissionChecker) {
    myPermissionChecker = permissionChecker;
  }

  public static Files getChildren(@NotNull final Browser browser, @NotNull final String path, @NotNull final String where,
                                  @NotNull final FileApiUrlBuilder fileApiUrlBuilder,
                                  @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    Element element = getElement(browser, path, where);
    try {
      final Iterable<Element> children = element.getChildren();
      if (element.isLeaf() || children == null) {
        throw new BadRequestException("Cannot provide children list for file '" + path + "'. To get content use '" + fileApiUrlBuilder.getContentHref(element) + "'.");
      }

      final List<File> result = new ArrayList<File>();
      for (Element child : children) {
        result.add(new File(child, null, null /*do not include parent as all children have it the same*/, fileApiUrlBuilder));
      }
      return new Files(null, result, fields, beanContext);
    } catch (BrowserException e) {
      throw new OperationException("Error listing children for path '" + path + "'.", e);
    }
  }

  public static File getMetadata(@NotNull final Browser browser, @NotNull final String path, @NotNull final String where, @NotNull final FileApiUrlBuilder fileApiUrlBuilder) {
    Element element = getElement(browser, path, where);
    Element parent = null;
    try {
      parent = getElement(browser, ArchiveUtil.getParentPath(element.getFullName()), "");
    } catch (NotFoundException e) {
      //ignore
    }
    return new File(element, null, parent, fileApiUrlBuilder);
  }

  @NotNull
  public static Element getElement(@NotNull final Browser browser, @NotNull final String path, @NotNull final String where) {
    final Element element;
    if (path.replace("\\","").replace("/","").replace(" ", "").length() == 0){ //TeamCity API issue: cannot list root of the Browser by empty string or "/"
      element = browser.getRoot();
    }else{
      element = browser.getElement(path);
    }
    if (element == null) {
      throw new NotFoundException("Path '" + path + "' is not found in " + where + " or an erorr occurred");
       //TeamCity API: or erorr occurred (related http://youtrack.jetbrains.com/issue/TW-34377)
    }
    return element;
  }

  public static Response.ResponseBuilder getContent(@NotNull final Browser browser,
                                             @NotNull final String path,
                                             @NotNull final String where,
                                             @NotNull final FileApiUrlBuilder fileApiUrlBuilder,
                                             HttpServletRequest request) {
    Element element = getElement(browser, path, where);
    if (!element.isContentAvailable()) {
      throw new NotFoundException("Cannot provide content for '" + path + "'. To get children use '" + fileApiUrlBuilder.getChildrenHref(element) + "'.");
    }
    return getContent(element, request);
  }

  public static Response.ResponseBuilder getContent(@NotNull final Element element, @NotNull final HttpServletRequest request) {
    return getContentByStream(element, request, new StreamingOutputProvider() {
      public boolean isRangeSupported() {
        return true;
      }

      public StreamingOutput getStreamingOutput(@Nullable final Long startOffset, @Nullable final Long length) {
        return BuildArtifactsFinder.getStreamingOutput(element, startOffset, length);
      }
    });
  }

  public static Response.ResponseBuilder getContentByStream(@NotNull final Element element, @NotNull final HttpServletRequest request,
                                                            @NotNull final StreamingOutputProvider streamingOutputProvider) {
    //TeamCity API: need to lock artifacts while reading???  e.g. see JavaDoc for jetbrains.buildServer.serverSide.artifacts.BuildArtifacts.iterateArtifacts()
    if (!element.isContentAvailable()) {
      throw new NotFoundException("Cannot provide content for '" + element.getFullName() + "' (not a file).");
    }
    final String rangeHeader = request.getHeader("Range");

    Long fullFileSize = null;
    try {
      final long size = element.getSize();
      if (size >= 0) {
        fullFileSize = size;
      }
    } catch (IllegalStateException e) {
      //just do not set size in the case
    }

    Response.ResponseBuilder builder;
    if (StringUtil.isEmpty(rangeHeader)) {
      builder = Response.ok().entity(streamingOutputProvider.getStreamingOutput(null, null));
      if (fullFileSize != null) {
        builder.header(HttpHeaders.CONTENT_LENGTH, fullFileSize);
      }
    } else {
      if (!streamingOutputProvider.isRangeSupported()) {
        throw new BadRequestException("Ranged requests are not supported for this entity");
      }
      try {
        HttpByteRange range = new HttpByteRange(rangeHeader, fullFileSize);
        //todo: support requests with "Range: bytes=XX-" header and unknown content-length via multipart/byteranges Content-Type including Content-Range fields for each part
        if (range.getRangesCount() > 1) {
          builder = Response.status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE).entity("Multiple Range header ranges are not (yet) supported");
        } else {
          final HttpByteRange.SimpleRange firstRange = range.getSimpleRangesIterator().next();

          builder = Response.status(HttpServletResponse.SC_PARTIAL_CONTENT);
          final long rangeLength = firstRange.getLength();
          builder.entity(streamingOutputProvider.getStreamingOutput(firstRange.getBeginIndex(), rangeLength));
          builder.header("Content-Range", range.getContentRangeHeaderValue(firstRange));
          if (fullFileSize != null) {
            builder.header(HttpHeaders.CONTENT_LENGTH, rangeLength);
          }
        }
      } catch (ParseException e) {
        builder = Response.status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE).entity("Error parsing Range header: " + e.getMessage());
        if (fullFileSize != null){
          builder.header("Content-Range", HttpByteRange.getContentRangeHeaderValueFor416Response(fullFileSize));
        }
      }
    }

    builder.header("Accept-Ranges", HttpByteRange.RANGE_UNIT_BYTES);

    if (TeamCityProperties.getBooleanOrTrue("rest.build.artifacts.setMimeType")) {
      builder = builder.type(WebUtil.getMimeType(request, element.getName()));
    } else {
      builder = builder.type(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }
    if (TeamCityProperties.getBooleanOrTrue("rest.build.artifacts.forceContentDisposition.Attachment")) {
      // make sure the file is not displayed in the browser (TW-27206)
      builder = builder.header("Content-Disposition", WebUtil.getContentDispositionValue(request, "attachment", element.getName()));
    } else {
      builder = builder.header("Content-Disposition", WebUtil.getContentDispositionValue(request, null, element.getName()));
    }

    if (element instanceof ArtifactTreeElement){
      final Long lastModified = ((ArtifactTreeElement)element).getLastModified();
      if (lastModified != null){
        builder.lastModified(new Date(lastModified));
      }
      final long size = element.getSize();
      builder.header("ETag", "W/\"" + EncryptUtil.md5((size >= 0 ? String.valueOf(size) : "") + (lastModified != null ? lastModified : "")) + "\""); //mark ETag as "weak"
    }else{
      final long size = element.getSize();
      if (size >= 0) {
        builder.header("ETag", "W/\"" + EncryptUtil.md5(String.valueOf(size)) + "\""); //mark ETag as "weak"
      }
    }

    // see jetbrains.buildServer.web.util.WebUtil.addCacheHeadersForIE and http://youtrack.jetbrains.com/issue/TW-9821 for details)
    if (WebUtil.isIE(request)) {
      builder.header("Cache-Control", "private,must-revalidate");
      builder.header("Pragma", "private");
    }
    return builder;
  }

  public interface StreamingOutputProvider {
    boolean isRangeSupported();

    StreamingOutput getStreamingOutput(@Nullable final Long startOffset, @Nullable final Long length);
  }

  public static StreamingOutput getStreamingOutput(@NotNull final Element element, @Nullable final Long startOffset, @Nullable final Long length) {
    return new StreamingOutput() {
        public void write(final OutputStream output) throws WebApplicationException {
          InputStream inputStream = null;
          try {
            inputStream = element.getInputStream();
            if (startOffset != null || length != null) {
              TCStreamUtil.skip(inputStream, startOffset != null ? startOffset : 0);
              TCStreamUtil.writeBinary(inputStream, length != null ? length : element.getSize(), output);
            } else {
              TCStreamUtil.writeBinary(inputStream, output);
            }
          } catch (IOException e) {
            //todo add better processing
            throw new OperationException("Error while processing file '" + element.getFullName() + "': " + e.toString(), e);
          } finally {
            FileUtil.close(inputStream);
          }
        }
      };
  }

  @NotNull
  public static FileApiUrlBuilder getStandardFileApiUrlBuilder(@NotNull final String myBaseHref) {
    return new FileApiUrlBuilder() {
      public String getMetadataHref(@Nullable Element e) {
        return myBaseHref + BuildRequest.METADATA + (e == null ? "" : "/" + e.getFullName());
      }

      public String getChildrenHref(@Nullable Element e) {
        return myBaseHref + BuildRequest.CHILDREN + (e == null ? "" : "/" + e.getFullName());
      }

      public String getContentHref(@Nullable Element e) {
        return myBaseHref + BuildRequest.CONTENT + (e == null ? "" : "/" + e.getFullName());
      }
    };
  }

  public List<File> getFiles(final SBuild build, final String resolvedPath, final String locator, final BeanContext beanContext) {
    final List<ArtifactTreeElement> artifacts = getArtifacts(build, resolvedPath, locator, beanContext);

    return CollectionsUtil.convertCollection(artifacts, new Converter<File, ArtifactTreeElement>() {
      public File createFrom(@NotNull final ArtifactTreeElement source) {
        return new File(source, null, fileApiUrlBuilderForBuild(beanContext.getContextService(ApiUrlBuilder.class), build, locator));
      }
    });
  }

  public List<ArtifactTreeElement> getArtifacts(@NotNull final SBuild build, @NotNull final String path, @Nullable final String filesLocator, @Nullable final BeanContext context) {
    @Nullable final Locator locator = getLocator(filesLocator);
    final BuildArtifactsViewMode viewMode = getViewMode(locator, build);
    final Element initialElement = getArtifactElement(build, path, viewMode);

    if (initialElement.isLeaf() || initialElement.getChildren() == null) {
      String additionalMessage = "";
      if (context != null) {
        additionalMessage = " To get content use '" + fileApiUrlBuilderForBuild(context.getContextService(ApiUrlBuilder.class), build, null).getContentHref(initialElement) + "'.";
      }
      throw new BadRequestException("Cannot provide children list for file '" + path + "'." + additionalMessage);
    }

/*
  // possible code after http://youtrack.jetbrains.com/issue/TW-37211 fix:

    TreePatternScanner.filterNamedTree(new ElementNamedTreeNode((ArtifactTreeElement)initialElement), "", TreePatternScanner.TreeTraverseMode.BREADTHFIRST,
                                         new TreePatternScanner.NamedTreeMatchingVisitor<ElementNamedTreeNode>() {
                                           public void nodeMatched(final ElementNamedTreeNode node) {
                                             result.add(node.getElement());
                                           }

                                           public void nodeExcluded(final ElementNamedTreeNode node) {

                                           }
                                         });

  private class ElementNamedTreeNode implements TreePatternScanner.NamedTreeNode<ElementNamedTreeNode> {
    private final ArtifactTreeElement myElement;

    public ElementNamedTreeNode(final ArtifactTreeElement myElement) {
      this.myElement = myElement;
    }

    public String getName() {
      return myElement.getName();
    }

    public Iterable<ElementNamedTreeNode> getChildren() {
      //todo: filter children by locator to limit traversing (e.g. check recursive, etc.)
      return CollectionsUtil.convertCollection(myElement.getChildren(), new Converter<ElementNamedTreeNode, Element>() {
        public ElementNamedTreeNode createFrom(@NotNull final Element source) {
          return new ElementNamedTreeNode((ArtifactTreeElement)source); //todo: can always cast?
        }
      });
    }

    public ArtifactTreeElement getElement() {
      return myElement;
    }
  }
  */

    final List<ArtifactTreeElement> result = new ArrayList<ArtifactTreeElement>();

    Iterable<Element> processingQueue = initialElement.getChildren();
    try {
      Iterator<Element> iterator = processingQueue.iterator();
      while (iterator.hasNext()) {
        Element element = iterator.next();
        final ArtifactTreeElement atElement = element instanceof ArtifactTreeElement
                                              ? (ArtifactTreeElement)element
                                              : getArtifactElement(build, element.getFullName(), viewMode);
        if (checkIncludesElement(atElement, locator)) {
          result.add(atElement);
        }

        if (checkCanIncludeSubElements(atElement, locator)) {
          final Iterable<Element> elementChildren = atElement.getChildren();
          if (elementChildren != null) {
            iterator = Iterators.concat(elementChildren.iterator(), iterator);
          }
        }
      }

      if (locator != null) locator.checkLocatorFullyProcessed();
      return result;
    } catch (BrowserException e) {
      throw new OperationException("Error listing children for artifact '" + path + "'.", e);
    }
  }

  private Locator getLocator(final String filesLocator) {
    return StringUtil.isEmpty(filesLocator) ? null : new Locator(filesLocator, HIDDEN_DIMENSION_NAME, ARCHIVES_DIMENSION_NAME, DIRECTORY_DIMENSION_NAME,
                                                                 DIMENSION_RECURSIVE /*, DIMENSION_PATTERNS*/);
  }

  public File getFile(@NotNull final SBuild build, @NotNull final String path, @Nullable final String locatorText, @NotNull final BeanContext context) {
    @Nullable final Locator locator = getLocator(locatorText);
    final BuildArtifactsViewMode viewMode = getViewMode(locator, build);
    final ArtifactTreeElement element = getArtifactElement(build, path, viewMode);
    final String par = StringUtil.removeTailingSlash(StringUtil.convertAndCollapseSlashes(element.getFullName()));
    final ArtifactTreeElement parent = par.equals("") ? null : getArtifactElement(build, ArchiveUtil.getParentPath(par), viewMode);
    return new File(element, parent, fileApiUrlBuilderForBuild(context.getContextService(ApiUrlBuilder.class), build, locatorText));
  }

  private static boolean checkIncludesElement(@NotNull final ArtifactTreeElement artifact, @Nullable final Locator locator) {
    if (locator == null){
      return true;
    }
    final Boolean directory = locator.getSingleDimensionValueAsBoolean(DIRECTORY_DIMENSION_NAME);
    if (!FilterUtil.isIncludedByBooleanFilter(directory, !artifact.isLeaf() && !artifact.isContentAvailable())) {
      return false;
    }

    /* pattern
    final String filePattern = locator.getSingleDimensionValue(DIMENSION_PATTERNS); //todo: support multiple or +/- syntax, cache
    //noinspection RedundantIfStatement
    if (filePattern != null && !isMatchedFully(artifact.getFullName(), filePattern)) {
      return false;
    }
    */

    return true;
  }

  private static boolean checkCanIncludeSubElements(@NotNull final ArtifactTreeElement artifact, @Nullable final Locator locator) {
    @Nullable final Boolean recursive = locator == null ? null : locator.getSingleDimensionValueAsBoolean(DIMENSION_RECURSIVE, false);
    if (recursive == null || !recursive) {
      return false; //not recursive by default
    }

    /* pattern
    final String filePattern = locator.getSingleDimensionValue(DIMENSION_PATTERNS); //todo: support multiple or +/- syntax, cache
    //noinspection RedundantIfStatement
    if (filePattern != null && !canMatchChildren(artifact.getFullName(), filePattern)) {
      return false;
    }
    */

    return true;
  }

/* pattern
  //todo: implement!
  private static boolean isMatchedFully(final String elementFullName, final String filePattern) {
//    final SearchPattern searchPattern = SearchPattern.patternFromString(filePattern);
//    searchPattern.getInitialState().find(artifact.getFullName());
    return SearchPattern.wildcardMatch(elementFullName, filePattern) && !SearchPattern.wildcardMatch(elementFullName +"?*", filePattern);
  }

  //todo: implement!
  private static boolean canMatchChildren(final String elementFullName, final String filePattern) {
    if (filePattern.startsWith("**")){
      return SearchPattern.wildcardMatch(elementFullName + filePattern, filePattern);
    } else{
      return true;
    }
  }
*/

  private BuildArtifactsViewMode getViewMode(@Nullable final Locator locator, @NotNull final SBuild build) {
    if (locator == null){
      return BuildArtifactsViewMode.VIEW_DEFAULT_WITH_ARCHIVES_CONTENT;
    }

    final Boolean viewHidden = locator.getSingleDimensionValueAsBoolean(HIDDEN_DIMENSION_NAME, false);

    //todo: make the default "true" (so far not supported by our BuildArtifactsViewMode) - can either support or filter afterwards
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
      myPermissionChecker.checkProjectPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build.getProjectId());
      return BuildArtifactsViewMode.VIEW_HIDDEN_ONLY;
    }

    throw new BadRequestException("Unsupported combination of '" + HIDDEN_DIMENSION_NAME + "' and '" + ARCHIVES_DIMENSION_NAME + "' dimensions.");
  }

  @NotNull
  public static FileApiUrlBuilder fileApiUrlBuilderForBuild(@NotNull final ApiUrlBuilder apiUrlBuilder, @NotNull final SBuild build, @Nullable final String locator) {
    return new FileApiUrlBuilder() {
      private final String myBuildHref = apiUrlBuilder.getHref(build);

      public String getMetadataHref(@Nullable Element e) {
        return myBuildHref + BuildRequest.ARTIFACTS_METADATA + (e == null ? "" : "/" + e.getFullName() + (locator == null ? "" : "?" + "locator" + "=" + locator));
      }

      public String getChildrenHref(@Nullable Element e) {
        return myBuildHref + BuildRequest.ARTIFACTS_CHILDREN + (e == null ? "" : "/" + e.getFullName() + (locator == null ? "" : "?" + "locator" + "=" + locator));
      }

      public String getContentHref(@Nullable Element e) {
        return myBuildHref + BuildRequest.ARTIFACTS_CONTENT + (e == null ? "" : "/" + e.getFullName());
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
