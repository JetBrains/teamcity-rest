/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import jetbrains.buildServer.controllers.HttpDownloadProcessor;
import jetbrains.buildServer.server.rest.data.ArchiveElement;
import jetbrains.buildServer.server.rest.data.BuildArtifactsFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.files.FileApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.files.Files;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.util.ArchiveUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactElement;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import jetbrains.buildServer.web.util.HttpByteRange;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;

/**
 * @author Yegor.Yarko
 *         Date: 04/07/2015
 */
@Api(hidden = true) // To prevent appearing in Swagger#definitions
public class FilesSubResource {
  public static final String METADATA = "/metadata";
  public static final String CONTENT = "/content";
  public static final String CHILDREN = "/children";

  private final Provider myProvider;
  private final String myUrlPrefix;
  @NotNull private final BeanContext myBeanContext;
  private final boolean myArchiveBrowsingSupported;

  public FilesSubResource(@NotNull final Provider provider, @NotNull final String urlPrefix, @NotNull final BeanContext beanContext, final boolean archiveBrowsingSupported) {
    myProvider = provider;
    myUrlPrefix = urlPrefix;
    myBeanContext = beanContext;
    myArchiveBrowsingSupported = archiveBrowsingSupported;
  }

  /**
   * Alias
   */
  @GET
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public Files getRoot(@QueryParam("basePath") final String basePath,
                       @QueryParam("locator") final String locator,
                       @QueryParam("fields") String fields) {
    return getChildren("", basePath, locator, fields);
  }

  /**
   * More user-friendly URL for "/artifacts/children" one.
   */
  @GET
  @Path("{path:(.*)?}") //for some reason, leading slash is not passed here
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public Files getChildrenAlias(@PathParam("path") @DefaultValue("") final String path,
                                @QueryParam("basePath") final String basePath,
                                @QueryParam("locator") final String locator,
                                @QueryParam("fields") String fields) {
    return getChildren(path, basePath, locator, fields);
  }

  @GET
  @Path(FilesSubResource.CHILDREN + "{path:(/.*)?}")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public Files getChildren(@PathParam("path") @DefaultValue("") final String path,
                           @QueryParam("basePath") final String basePath,
                           @QueryParam("locator") final String locator,
                           @QueryParam("fields") String fields) {
    if (!myArchiveBrowsingSupported && locator != null){
      final Boolean browseArchives = new Locator(locator).getSingleDimensionValueAsBoolean(BuildArtifactsFinder.ARCHIVES_DIMENSION_NAME);
      if (browseArchives != null && browseArchives){
        throw new BadRequestException("Archive browsing is not supported for this request, remove '" + BuildArtifactsFinder.ARCHIVES_DIMENSION_NAME + "' dimension");
      }
    }
    final FileApiUrlBuilder builder = fileApiUrlBuilder(locator, myUrlPrefix);
    final Element rootElement = myProvider.getElement(myProvider.preprocess(StringUtil.removeLeadingSlash(path)));
    return new Files(null, new ValueWithDefault.Value<List<? extends Element>>() {
      @Nullable
      public List<? extends Element> get() {
        return BuildArtifactsFinder.getItems(rootElement, myProvider.preprocess(basePath), locator, builder, myBeanContext.getServiceLocator());
      }
    }, builder, new Fields(fields), myBeanContext);
  }

  @Nullable
  private Element getParent(@NotNull final Element element) {
    final String parentPath = ArchiveUtil.getParentPath(element.getFullName());
    if (!StringUtil.isEmpty(parentPath)) {
      try {
        return myProvider.getElement(parentPath);
      } catch (NotFoundException e) {
        //ignore
      }
    }
    return null;
  }

  /**
   * More user-friendly URL for "/artifacts/children" one.
   */
  @GET
  @Path("files" + "{path:(/.*)?}")
  @Produces({MediaType.WILDCARD})
  public Response getContentAlias(@PathParam("path") @DefaultValue("") final String path, @Context HttpServletRequest request, @Context HttpServletResponse response) {
    return getContent(path, null, request, response);
  }

  @GET
  @Path(FilesSubResource.CONTENT + "{path:(/.*)?}")
  @Produces({MediaType.WILDCARD})
  public Response getContent(@PathParam("path") final String path,
                             @QueryParam("responseBuilder") final String responseBuilder,
                             @Context HttpServletRequest request,
                             @Context HttpServletResponse response) {
    final String preprocessedPath = myProvider.preprocess(StringUtil.removeLeadingSlash(path));
    final Element initialElement = myProvider.getElement(preprocessedPath);
    if (!initialElement.isContentAvailable()) {
      throw new NotFoundException("Cannot provide content for '" + initialElement.getFullName() + "'. To get children use '" +
                                  fileApiUrlBuilder(null, myUrlPrefix).getChildrenHref(initialElement) + "'.");
    }
    String contentResponseBuilder = responseBuilder != null ? responseBuilder : TeamCityProperties.getProperty("rest.files.contentResponseBuilder", "rest");
    if ("rest".equals(contentResponseBuilder)) {
      //pre-2017.1 way of downloading files
      final Response.ResponseBuilder builder = getContent(initialElement, request);
      myProvider.fileContentServed(preprocessedPath, request);
      return builder.build();
    } else if ("core".equals(contentResponseBuilder)) {
      processCoreDownload(initialElement, request, response);
    } else if ("coreWithDownloadProcessor".equals(contentResponseBuilder)) {
      if (!(myProvider instanceof DownloadProcessor) || !((DownloadProcessor)myProvider).processDownload(initialElement, request, response)) {
        processCoreDownload(initialElement, request, response);
      }
    } else {
      throw new BadRequestException("Unknown responseBuilder: '" + contentResponseBuilder + "'. Supported values are: '" + "rest" + "', '" + "core" + "', '" + "coreWithDownloadProcessor" + "'");
    }
    //todo: register only if no errors occurred?
    myProvider.fileContentServed(preprocessedPath, request);
    return null;
  }

  private void processCoreDownload(@NotNull final Element element, @NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    if (TeamCityProperties.getBooleanOrTrue("rest.build.artifacts.forceContentDisposition.Attachment")) {
      WebUtil.setContentDisposition(request, response, element.getName(), false);
    } else {
      response.setHeader("Content-Disposition", element.getName());
    }

    try {
      myBeanContext.getSingletonService(HttpDownloadProcessor.class).processDownload(new HttpDownloadProcessor.FileInfo() {
        public long getLastModified() {
          if (element instanceof ArtifactElement) {
            Long lastModified = ((ArtifactElement)element).getLastModified();
            if (lastModified != null) return lastModified;
          }
          return -1;
        }

        public long getFileSize() {
          return element.getSize();
        }

        @NotNull
        public String getFileName() {
          return element.getName();
        }

        @NotNull
        public String getFileDigest() {
          //including full "resolved" path into the tag to make sure same-name, same-size files available under the same URL produce different tags
          //note: "aggregated" build artifacts are still not handled in due way here
          return EncryptUtil.md5(fileApiUrlBuilder(null, myUrlPrefix).getContentHref(element) + "_" + getFileSize() + "_" + getLastModified());
        }

        @NotNull
        public InputStream getInputStream() throws IOException {
          //todo: see this method in HttpDownloadProcessor
          return element.getInputStream();
        }
      }, false /*header is forced to attachment above*/, request, response);
    } catch (IOException e) {
      //todo add better processing
      throw new OperationException("Error while processing file '" + element.getFullName() + "': " + e.toString(), e);
    }
  }


  @GET
  @Path(FilesSubResource.METADATA + "{path:(/.*)?}")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public jetbrains.buildServer.server.rest.model.files.File getMetadata(@PathParam("path") final String path,
                                                                        @QueryParam("fields") String fields,
                                                                        @Context HttpServletRequest request) {
    final Element element = myProvider.getElement(myProvider.preprocess(StringUtil.removeLeadingSlash(path)));
    return new jetbrains.buildServer.server.rest.model.files.File(element, getParent(element), fileApiUrlBuilder(null, myUrlPrefix), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/archived" + "{path:(/.*)?}")
  @Produces({MediaType.WILDCARD})
  public Response getZipped(@PathParam("path") final String path,
                            @QueryParam("basePath") final String basePath,
                            @QueryParam("locator") final String locator,
                            @QueryParam("name") final String name,
//                            @QueryParam("ignoreErrors") final String ignoreErrors, //todo: implement
                            @Context HttpServletRequest request) {
    final String processedPath = myProvider.preprocess(StringUtil.removeLeadingSlash(path));
    String actualBasePath = basePath != null ? myProvider.preprocess(basePath) : processedPath;
    String finalName = myProvider.preprocess(name);
    if (StringUtil.isEmpty(finalName)) {
      finalName = myProvider.getArchiveName(processedPath) + ".zip";
    }

    String actualLocator = Locator.setDimensionIfNotPresent(locator, BuildArtifactsFinder.DIMENSION_RECURSIVE, "true"); //include al files recursively by default
    actualLocator = Locator.setDimensionIfNotPresent(actualLocator, BuildArtifactsFinder.ARCHIVES_DIMENSION_NAME, "false"); //do not expand archives by default

    final FileApiUrlBuilder urlBuilder = fileApiUrlBuilder(locator, myUrlPrefix);
    final List<ArtifactTreeElement> elements = BuildArtifactsFinder.getItems(myProvider.getElement(processedPath), actualBasePath, actualLocator, urlBuilder,
                                                                             myBeanContext.getServiceLocator());

    final ArchiveElement archiveElement = new ArchiveElement(elements, finalName);
    final Response.ResponseBuilder builder = getContentByStream(archiveElement, request, new StreamingOutputProvider() {
      public boolean isRangeSupported() {
        return false;
      }

      public StreamingOutput getStreamingOutput(@Nullable final Long startOffset, @Nullable final Long length) {
        return archiveElement.getStreamingOutput(startOffset, length);
      }
    });
    for (ArtifactTreeElement element : elements) {
      if (!myProvider.fileContentServed(Util.concatenatePath(actualBasePath, element.getFullName()), request)) break;
    }

    return builder.build();
  }

  public static FileApiUrlBuilder fileApiUrlBuilder(@Nullable final String locator, @NotNull final String urlPathPrefix) {
    return new FileApiUrlBuilder() {
      @NotNull
      public String getMetadataHref(@Nullable Element e) {
        return Util.concatenatePath(urlPathPrefix, METADATA, e == null ? "" : e.getFullName());
      }

      @NotNull
      public String getChildrenHref(@Nullable Element e) {
        return Util.concatenatePath(urlPathPrefix, CHILDREN, e == null ? "" : e.getFullName()) + (locator == null ? "" : "?" + "locator" + "=" + locator);
      }

      @NotNull
      public String getContentHref(@Nullable Element e) {
        return Util.concatenatePath(urlPathPrefix, CONTENT, e == null ? "" : e.getFullName());
      }

      @NotNull
      public String getUrlPathPrefix() {
        return urlPathPrefix;
      }
    };
  }

  public static Response.ResponseBuilder getContent(@NotNull final Element element, @NotNull final HttpServletRequest request) {
    return getContentByStream(element, request, new StreamingOutputProvider() {
      public boolean isRangeSupported() {
        return true;
      }

      public StreamingOutput getStreamingOutput(@Nullable final Long startOffset, @Nullable final Long length) {
        return FilesSubResource.getStreamingOutput(element, startOffset, length);
      }
    });
  }

  public static Response.ResponseBuilder getContentByStream(@NotNull final Element element, @NotNull final HttpServletRequest request,
                                                            @NotNull final StreamingOutputProvider streamingOutputProvider) {

    // support ETag in request to response with 304/not modified, see also jetbrains.buildServer.controllers.artifacts.DownloadArtifactsController.doHandle()
    //TeamCity API: need to lock artifacts while reading???  e.g. see JavaDoc for jetbrains.buildServer.serverSide.artifacts.BuildArtifacts.iterateArtifacts()
    if (!element.isContentAvailable()) {
      throw new NotFoundException("Cannot provide content for '" + element.getFullName() + "' (not a file).");
    }
    final String rangeHeader = request.getHeader(HttpHeaders.RANGE);

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
        if (fullFileSize != null) {
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

    if (element instanceof ArtifactTreeElement) {
      final Long lastModified = ((ArtifactTreeElement)element).getLastModified();
      if (lastModified != null) {
        builder.lastModified(new Date(lastModified));
      }
      final long size = element.getSize();
      builder.header("ETag", "W/\"" + EncryptUtil.md5((size >= 0 ? String.valueOf(size) : "") + (lastModified != null ? lastModified : "")) + "\""); //mark ETag as "weak"
    } else {
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

  private static StreamingOutput getStreamingOutput(@NotNull final Element element, @Nullable final Long startOffset, @Nullable final Long length) {
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

  public interface StreamingOutputProvider {
    boolean isRangeSupported();

    StreamingOutput getStreamingOutput(@Nullable final Long startOffset, @Nullable final Long length);
  }

  abstract static class Provider {
    @NotNull
    public abstract Element getElement(@NotNull final String path);

    @NotNull
    public String getArchiveName(@NotNull final String path) {
      return path.replaceAll("[^a-zA-Z0-9-#.]+", "_");
    }

    @NotNull
    public String preprocess(@Nullable final String path) {
      return path == null ? "" : path;
    }

    /**
     * @return false if no further processing is necessary for the request
     */
    public boolean fileContentServed(@Nullable final String path, @NotNull final HttpServletRequest request) {
      return false;
    }
  }

  interface DownloadProcessor {
    /**
     * @param response true if the request is processed and response is complete. false if the response is not written into and the processing should be continued
     * @return
     */
    public boolean processDownload(@NotNull Element element, @NotNull HttpServletRequest request, @NotNull HttpServletResponse response);
  }
}
