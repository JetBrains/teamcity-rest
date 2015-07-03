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

import com.google.common.collect.ComparisonChain;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import jetbrains.buildServer.ArtifactsConstants;
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
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.util.browser.ZipElement;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.util.pathMatcher.AntPatternTreeMatcher;
import jetbrains.buildServer.util.pathMatcher.PathNode;
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
  public static final String DIMENSION_PATTERNS = "patterns";  //todo or "pattern" ?
  protected static final Comparator<ArtifactTreeElement> ARTIFACT_COMPARATOR = new Comparator<ArtifactTreeElement>() {
    public int compare(final ArtifactTreeElement o1, final ArtifactTreeElement o2) {
      return ComparisonChain.start()
                            .compareFalseFirst(o1.isContentAvailable(), o2.isContentAvailable())
                            .compare(o1.getFullName(), o2.getFullName())
                            .result();
    }
  };

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

      return new Files(null, children, null /*do not include parent as all children have it the same*/, fileApiUrlBuilder, fields, beanContext);
    } catch (BrowserException e) {
      throw new OperationException("Error listing children for path '" + path + "'.", e);
    }
  }

  public static File getMetadata(@NotNull final Browser browser, @NotNull final String path, @NotNull final String where,
                                 @NotNull final FileApiUrlBuilder fileApiUrlBuilder, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    Element element = getElement(browser, path, where);
    Element parent = null;
    try {
      parent = getElement(browser, ArchiveUtil.getParentPath(element.getFullName()), "");
    } catch (NotFoundException e) {
      //ignore
    }
    return new File(element, parent, fileApiUrlBuilder, fields, beanContext);
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

  public List<ArtifactTreeElement> getArtifacts(@NotNull final SBuild build, @NotNull final String path, @Nullable final String basePath, @Nullable final String filesLocator,
                                                @Nullable final BeanContext context) {
    @Nullable final Locator locator = getLocator(filesLocator);
    final ArtifactTreeElement initialElement = getArtifactElement(build, path, BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT);

    if (initialElement.isLeaf() || initialElement.getChildren() == null) {
      String additionalMessage = "";
      if (context != null) {
        additionalMessage = " To get content use '" + fileApiUrlBuilderForBuild(build, null, context).getContentHref(initialElement) + "'.";
      }
      throw new BadRequestException("Cannot provide children list for file '" + path + "'." + additionalMessage);
    }

    List<String> rules = new ArrayList<String>();
//    rules.add("+:**"); //todo: is this relative path?

    boolean includeDirectories = true;
    Boolean includeHidden = false;
    long childrenNestingLevel = 1;
    long archiveChildrenNestingLevel = 0;
    if (locator != null) {
      final Boolean directory = locator.getSingleDimensionValueAsBoolean(DIRECTORY_DIMENSION_NAME);
      if (directory != null){
        includeDirectories = directory;
      }

      includeHidden = locator.getSingleDimensionValueAsBoolean(HIDDEN_DIMENSION_NAME);

      final String filePatterns = locator.getSingleDimensionValue(DIMENSION_PATTERNS);
      if (filePatterns != null) {
        final String[] splittedPatterns = filePatterns.split(","); //might consider smarter splitting later
        if (splittedPatterns.length > 0) {
          rules.addAll(Arrays.asList(splittedPatterns));
        }
      } else {
        rules.add("+:**");
      }

      final String recursive = locator.getSingleDimensionValue(DIMENSION_RECURSIVE);
      if (recursive != null) {
        final Boolean parsedBoolean = Locator.getStrictBoolean(recursive);
        if (parsedBoolean != null) {
          if (parsedBoolean) {
            childrenNestingLevel = -1;
          } else {
            childrenNestingLevel = 1;
          }
        } else {
          //treat as nesting number
          try {
            childrenNestingLevel = Long.parseLong(recursive);
          } catch (NumberFormatException e) {
            throw new BadRequestException("Cannot parse value '" + recursive + "' for dimension '" + DIMENSION_RECURSIVE + "': should be boolean or nesting level number");
          }
        }
      }

      final String listArchives = locator.getSingleDimensionValue(ARCHIVES_DIMENSION_NAME);
      if (listArchives != null) {
        final Boolean parsedBoolean = Locator.getStrictBoolean(listArchives);
        if (parsedBoolean != null) {
          if (parsedBoolean) {
            archiveChildrenNestingLevel = 1;
          } else {
            archiveChildrenNestingLevel = 0;
          }
        } else {
          //treat as nesting number
          try {
            archiveChildrenNestingLevel = Long.parseLong(listArchives);
          } catch (NumberFormatException e) {
            throw new BadRequestException("Cannot parse value '" + listArchives + "' for dimension '" + ARCHIVES_DIMENSION_NAME + "': should be boolean or nesting level number");
          }
        }
      }

      locator.checkLocatorFullyProcessed();
    } else {
      rules.add("+:**");
    }

    final List<ArtifactTreeElement> result = new ArrayList<ArtifactTreeElement>();
    AntPatternTreeMatcher.ScanOption[] options = {};
    if (!includeDirectories) {
      options = new AntPatternTreeMatcher.ScanOption[]{AntPatternTreeMatcher.ScanOption.LEAFS_ONLY};
    }

    final Node rootNode = new Node(initialElement, childrenNestingLevel, archiveChildrenNestingLevel, includeHidden, true);
    final Collection<Node> rawResult = AntPatternTreeMatcher.scan(rootNode, rules, options);
    result.addAll(CollectionsUtil.filterAndConvertCollection(rawResult, new Converter<ArtifactTreeElement, Node>() {
      public ArtifactTreeElement createFrom(@NotNull final Node source) {
        if (basePath == null) {
          return source.getElement();
        }
        return new ArtifactTreeElementWrapper(source.getElement()) {
          @NotNull
          @Override
          public String getFullName() {
            return relativeToBase(super.getFullName(), basePath);
          }
        };
      }
    }, new Filter<Node>() {
      public boolean accept(@NotNull final Node data) {
        return !rootNode.equals(data); //TeamCity API issue: should support not returning the first node in API
      }
    }));

    Collections.sort(result, ARTIFACT_COMPARATOR);
    return result;
  }

  @NotNull
  private String relativeToBase(@NotNull final String name, @Nullable final String basePath) {
    if (basePath == null) return name;

    final String normalizedName = removeLeadingDelimeters(name);
    if (!normalizedName.startsWith(removeLeadingDelimeters(basePath))) {
      return name;
    }
    return removeLeadingDelimeters(normalizedName.substring(basePath.length()));
  }

  @NotNull
  private static String removeLeadingDelimeters(@NotNull String result) {
    return removeLeading(removeLeading(result, "!"), "/");
  }

  @NotNull
  private static String removeLeading(final @NotNull String result, final String prefix) {
    return result.startsWith(prefix) ? result.substring(prefix.length()) : result;
  }

  @Nullable
  private Locator getLocator(@Nullable final String filesLocator) {
    Locator defaults = Locator.createEmptyLocator().setDimension(DIMENSION_RECURSIVE, "false").setDimension(HIDDEN_DIMENSION_NAME, "false")
                              .setDimension(ARCHIVES_DIMENSION_NAME, "false").setDimension(DIRECTORY_DIMENSION_NAME, "true");
    final String[] supportedDimensions = {HIDDEN_DIMENSION_NAME, ARCHIVES_DIMENSION_NAME, DIRECTORY_DIMENSION_NAME, DIMENSION_RECURSIVE, DIMENSION_PATTERNS};
    return Locator.createLocator(filesLocator, defaults, supportedDimensions);
  }

  public File getFile(@NotNull final SBuild build, @NotNull final String path, @Nullable final String locatorText, @NotNull final Fields fields, @NotNull final BeanContext context) {
    @Nullable final Locator locator = getLocator(locatorText);
    final ArtifactTreeElement element = getArtifactElement(build, path, BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT);
    final String par = StringUtil.removeTailingSlash(StringUtil.convertAndCollapseSlashes(element.getFullName()));
    final ArtifactTreeElement parent = par.equals("") ? null : getArtifactElement(build, ArchiveUtil.getParentPath(par), BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT);
    return new File(element, parent, fileApiUrlBuilderForBuild(build, locatorText, context), fields, context);
  }

  @NotNull
  public static FileApiUrlBuilder fileApiUrlBuilderForBuild(@NotNull final SBuild build, @Nullable final String locator, @NotNull final BeanContext beanContext) {
    return new FileApiUrlBuilder() {
      private final String myBuildHref = beanContext.getApiUrlBuilder().getHref(build);

      public String getMetadataHref(@Nullable Element e) {
        return myBuildHref + BuildRequest.ARTIFACTS_METADATA + (e == null ? "" : "/" + e.getFullName());
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
  private static BuildArtifact getBuildArtifact(@NotNull final SBuild build,
                                               @NotNull final String path,
                                               @NotNull final BuildArtifactsViewMode mode) {
    final BuildArtifacts artifacts = build.getArtifacts(mode);
    final BuildArtifactHolder holder = artifacts.findArtifact(path);
    if (!holder.isAvailable()
        && !"".equals(path)) { //workaround for no artifact directory case
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

  private class Node implements PathNode<Node> {
    @NotNull private final ArtifactTreeElement myElement;
    private final long myListChildrenLevel;
    private final long myListArchiveChildrenLevel;
    private final Boolean myHidden;
    private final boolean myFirstNode;

    /**
     * @param element
     * @param listChildrenLevel       number of nesting to list children for; -1 for unlimited level, 0 for no children listed
     * @param listArchiveChildrenLevel  treat archives as directories (up to the specified archive nesting number)
     * @param hidden           list files under .teamcity, "null" to include both hidden and not
     * @param firstNode
     */
    public Node(@NotNull final Element element,
                final long listChildrenLevel,
                final long listArchiveChildrenLevel,
                final Boolean hidden,
                final boolean firstNode) {
      myElement = new ArtifactTreeElementWrapper(element);
      myListChildrenLevel = listChildrenLevel;
      myListArchiveChildrenLevel = listArchiveChildrenLevel;
      myHidden = hidden;
      myFirstNode = firstNode;
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean shouldHideChildren() {
      if (myListChildrenLevel == 0) return true;
      if (myFirstNode) return false;
      if (myElement.isArchive() && myListArchiveChildrenLevel == 0) return true;
      return false;
    }

    @NotNull
    public String getName() {
      return myElement.getName();
    }

    private Iterable<Node> myCachedChildren;
    private boolean myCachedChildrenInitialized = false;

    public Iterable<Node> getChildren() {
      if (!myCachedChildrenInitialized) {
        myCachedChildren = getChildrenInternal();
        myCachedChildrenInitialized = true;
      }
      return myCachedChildren;
    }

    private Iterable<Node> getChildrenInternal() {
      if (shouldHideChildren()) {
        return null;
      }
      try {
        final long nextListChildrenLevel = myListChildrenLevel > 0 ? myListChildrenLevel - 1 : myListChildrenLevel;
        final long nextListArchiveChildrenLevel =
          (myElement.isArchive() && myListArchiveChildrenLevel > 0 && !myFirstNode) ? myListArchiveChildrenLevel - 1 : myListArchiveChildrenLevel;
        //noinspection unchecked
        return CollectionsUtil.filterAndConvertCollection(myElement.getChildren(), new Converter<Node, Element>() {
          public Node createFrom(@NotNull final Element source) {
            return new Node(source, nextListChildrenLevel, nextListArchiveChildrenLevel, myHidden, false);
          }
        }, new Filter<Element>() {
          public boolean accept(@NotNull final Element data) {
            return FilterUtil.isIncludedByBooleanFilter(myHidden, isHidden(data));
          }
        });
      } catch (BrowserException e) {
        throw new OperationException("Error listing children for artifact '" + myElement.getFullName() + "'.", e);
      }
    }

    @NotNull
    public ArtifactTreeElement getElement() {
      if (myFirstNode || !myElement.isArchive() || myListArchiveChildrenLevel != 0) {
        return myElement;
      }
      return new ArtifactTreeElementWrapper(myElement) {
        @Override
        public boolean isLeaf() {
          return true;
        }

        @Nullable
        @Override
        public Iterable<Element> getChildren() throws BrowserException {
          return null;
        }

        @Override
        public String toString() {
          return myElement.toString() + " with children concealed";
        }
      };
    }

    @Override
    public String toString() {
      return "Node '" + myElement.toString() + "', childrenLevel: " + myListChildrenLevel +
             ", archiveLevel: " + myListArchiveChildrenLevel +
             ", includeHidden: " + myHidden +
             ", first: " + myFirstNode + ")";

    }
  }

  public static class ArtifactTreeElementWrapper implements ArtifactTreeElement {

    @NotNull private final Element myElement;
    @Nullable private final ZipElement myZipElement;
    @Nullable private final ArtifactTreeElement myArtifactTreeElement;

    public ArtifactTreeElementWrapper(@NotNull final Element element) {
      myElement = element;
      if (myElement instanceof ZipElement) {
        myZipElement = (ZipElement)myElement;
      } else {
        myZipElement = null;
      }
      if (myElement instanceof ArtifactTreeElement) {
        myArtifactTreeElement = (ArtifactTreeElement)myElement;
      } else {
        myArtifactTreeElement = null;
      }
    }

    @NotNull
    public String getName() {
      return myElement.getName();
    }

    @NotNull
    public String getFullName() {
      return myElement.getFullName();
    }

    public boolean isLeaf() {
      return myElement.isLeaf();
    }

    @Nullable
    public Iterable<Element> getChildren() throws BrowserException {
      return myElement.getChildren();
    }

    public boolean isContentAvailable() {
      return myElement.isContentAvailable();
    }

    @NotNull
    public InputStream getInputStream() throws IllegalStateException, IOException, BrowserException {
      return myElement.getInputStream();
    }

    public long getSize() {
      return myElement.getSize();
    }

    @NotNull
    public Browser getBrowser() {
      return myElement.getBrowser();
    }

    @SuppressWarnings("SimplifiableConditionalExpression")
    public boolean isArchive() {
      return myZipElement != null ? myZipElement.isArchive() : false;
    }

    @SuppressWarnings("SimplifiableConditionalExpression")
    public boolean isInsideArchive() {
      return myZipElement != null ? myZipElement.isInsideArchive() : false;
    }

    @Nullable
    public Long getLastModified() {
      return myArtifactTreeElement != null ? myArtifactTreeElement.getLastModified() : null;
    }

    @Override
    public String toString() {
      return myElement.toString() + " unified";
    }
  }

  private static boolean isHidden(final @NotNull Element data) {
    final String fullName = data.getFullName();
    return fullName.equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR) ||
           fullName.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/");
  }
}
