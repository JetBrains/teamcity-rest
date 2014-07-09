/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
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
import jetbrains.buildServer.util.ArchiveUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TCStreamUtil;
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
  @NotNull private final DataProvider myDataProvider;

  public BuildArtifactsFinder(@NotNull final DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public static Files getChildren(@NotNull final Browser browser, @NotNull final String path, @NotNull final String where, @NotNull final FileApiUrlBuilder fileApiUrlBuilder) {
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
      return new Files(result);
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
    return getContent(element, path, fileApiUrlBuilder, request);
  }

  public static Response.ResponseBuilder getContent(@NotNull final Element element,
                                             @NotNull final String path,
                                             @NotNull final FileApiUrlBuilder fileApiUrlBuilder,
                                             @NotNull final HttpServletRequest request) {
    if (!element.isContentAvailable()) {
      throw new NotFoundException("Cannot provide content for '" + path + "'. To get children use '" + fileApiUrlBuilder.getChildrenHref(element) + "'.");
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
      builder = Response.ok().entity(BuildArtifactsFinder.getStreamingOutput(element, null, null));
      if (fullFileSize != null) {
        builder.header(HttpHeaders.CONTENT_LENGTH, fullFileSize);
      }
    } else {
      try {
        HttpByteRange range = new HttpByteRange(rangeHeader, fullFileSize);
        //todo: support requests with "Range: bytes=XX-" header and unknown content-length via multipart/byteranges Content-Type including Content-Range fields for each part
        if (range.getRangesCount() > 1) {
          builder = Response.status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE).entity("Multiple Range header ranges are not (yet) supported");
        } else {
          final HttpByteRange.SimpleRange firstRange = range.getSimpleRangesIterator().next();

          builder = Response.status(HttpServletResponse.SC_PARTIAL_CONTENT);
          final long rangeLength = firstRange.getLength();
          builder.entity(BuildArtifactsFinder.getStreamingOutput(element, firstRange.getBeginIndex(), rangeLength));
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
      builder = builder.type(WebUtil.getMimeType(request, path));
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
      builder.header("ETag", "W/\"" + EncryptUtil.md5((element.getSize() >= 0 ? String.valueOf(element.getSize()) : "") + lastModified) + "\""); //mark ETag as "weak"
    }else{
      if (element.getSize() >= 0){
        builder.header("ETag", "W/\"" + EncryptUtil.md5(String.valueOf(element.getSize())) + "\""); //mark ETag as "weak"
      }
    }

    return builder;
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
            throw new OperationException("Error while processing file '" + element.getFullName() + "' content: " + e.getMessage(), e);
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

  public Files getFiles(@NotNull final SBuild build, @NotNull final String path, @Nullable final String filesLocator, @NotNull final BeanContext context) {
    @Nullable final Locator locator = getLocator(filesLocator);
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

  private Locator getLocator(final String filesLocator) {
    return StringUtil.isEmpty(filesLocator) ? null : new Locator(filesLocator, HIDDEN_DIMENSION_NAME, ARCHIVES_DIMENSION_NAME, DIRECTORY_DIMENSION_NAME);
  }

  public File getFile(@NotNull final SBuild build, @NotNull final String path, @Nullable final String locatorText, @NotNull final BeanContext context) {
    @Nullable final Locator locator = getLocator(locatorText);
    final BuildArtifactsViewMode viewMode = getViewMode(locator, build, context);
    final ArtifactTreeElement element = getArtifactElement(build, path, viewMode);
    final String par = StringUtil.removeTailingSlash(StringUtil.convertAndCollapseSlashes(element.getFullName()));
    final ArtifactTreeElement parent = par.equals("") ? null : getArtifactElement(build, ArchiveUtil.getParentPath(par), viewMode);
    return new File(element, parent, fileApiUrlBuilderForBuild(context.getContextService(ApiUrlBuilder.class), build, locatorText));
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
