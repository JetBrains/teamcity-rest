package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.files.FileDefRef;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Rassokhin
 */
public class FileApiUrlBuilder {
  @NotNull
  private final String myUrlsBase;

  private FileApiUrlBuilder(@NotNull final String base) {
    myUrlsBase = base;
  }

  @NotNull
  public static FileApiUrlBuilder forBuild(@NotNull final ApiUrlBuilder apiUrlBuilder, @NotNull final SBuild build) {
    return new FileApiUrlBuilder(apiUrlBuilder.getHref(build));
  }

  public String getMetadataHref(FileDefRef fd) {
    return myUrlsBase + BuildRequest.ARTIFACTS_METADATA + "/" + fd.getRelativePath();
  }

  public String getChildrenHref(FileDefRef fd) {
    return myUrlsBase + BuildRequest.ARTIFACTS_CHILDREN + "/" + fd.getRelativePath();
  }

  public String getContentHref(FileDefRef fd) {
    return myUrlsBase + BuildRequest.ARTIFACTS_CONTENT + "/" + fd.getRelativePath();
  }
}
