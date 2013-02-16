package jetbrains.buildServer.server.rest.files;

import com.intellij.util.PathUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.impl.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Vladislav.Rassokhin
 * @since 8.0
 */
public class FileDef extends FileDefRef {
  private final boolean myIsDirectory;
  private final long mySize;
  private final long myTimestamp;
  private final Lazy<Collection<FileDefRef>> myChildrenLoader;

  public FileDef(@NotNull final String name, @NotNull final String path, boolean isDirectory, long size, long timestamp, @NotNull final Lazy<Collection<FileDefRef>> children) {
    super(name, path);
    myIsDirectory = isDirectory;
    mySize = size;
    myTimestamp = timestamp;
    myChildrenLoader = children;
  }


  public boolean isDirectory() {
    return myIsDirectory;
  }

  public long getSize() {
    return mySize;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public Collection<FileDefRef> getChildren() {
    return myChildrenLoader.getValue();
  }

  /**
   * @return parent relative path without leading slashes or null if this FileDef is root (relative path is '/' or empty)
   */
  @Nullable
  public String getParentPath() {
    final String path = StringUtil.removeTailingSlash(getRelativePath());
    if (path.equals("")) {
      return null;
    }
    int slash = path.lastIndexOf('/');
    if (slash == -1) {
      return "";
    }
    int exclamation = path.lastIndexOf("!/");
    if (exclamation != -1 && slash == exclamation + 1) {
      // Means "!/"
      // Go out of archive
      return path.substring(0, exclamation);
    } else {
      // Still in archive or not in archive
      final String parent = PathUtil.getParentPath(path);
      if ("".equals(parent)) {
        return "";
      }
      return parent;
    }
  }

  /**
   * @return FileDefRef for parent file or null if this FileDef is root (relative path is '/' or empty)
   * @see #getParentPath()
   */
  @Nullable
  public FileDefRef getParent() {
    String path = getParentPath();
    if (path == null) {
      return null;
    }
    return new FileDefRef(PathUtil.getFileName(path), path);
  }
}
