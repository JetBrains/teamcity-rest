package jetbrains.buildServer.server.rest.files;

import com.intellij.util.PathUtil;
import jetbrains.buildServer.util.ArchiveUtil;
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
    final String path = StringUtil.removeTailingSlash(StringUtil.convertAndCollapseSlashes(getRelativePath()));
    if (path.equals("")) {
      return null;
    }
    return ArchiveUtil.getParentPath(path);
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
