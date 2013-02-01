package jetbrains.buildServer.server.rest.files;

import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Rassokhin
 */
public class FileDefRef {
  private final String myRelativePath;
  private final String myName;

  public FileDefRef(String name, String relativePath) {
    this.myName = StringUtil.removeLeadingSlash(StringUtil.removeTailingSlash(name));
    this.myRelativePath = StringUtil.removeLeadingSlash(StringUtil.removeTailingSlash(relativePath));
  }

  @NotNull
  public String getRelativePath() {
    return myRelativePath;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return "FileDefRef{" +
        "myRelativePath='" + myRelativePath + '\'' +
        ", myName='" + myName + '\'' +
        '}';
  }
}
