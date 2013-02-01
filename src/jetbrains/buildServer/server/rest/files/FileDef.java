package jetbrains.buildServer.server.rest.files;

import com.intellij.util.PathUtil;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Vladislav.Rassokhin
 */
public abstract class FileDef extends FileDefRef {
  private final boolean myIsDirectory;
  private final long mySize;
  private final long myTimestamp;
  private final boolean myIsInArchive;
  private Collection<FileDefRef> myChildren = null;

  public FileDef(String name, String path, boolean isDirectory, long size, long timestamp, boolean isInArchive) {
    super(name, path);
    this.myIsDirectory = isDirectory;
    this.mySize = size;
    this.myTimestamp = timestamp;
    this.myIsInArchive = isInArchive;
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

  protected abstract Collection<FileDefRef> getChildrenLoad() throws IOException;

  public Collection<FileDefRef> getChildren() throws IOException {
    if (myChildren == null) {
      synchronized (this) {
        if (myChildren == null) {
          myChildren = getChildrenLoad();
        }
      }
    }
    return myChildren;
  }

  @Nullable
  public String getParentPath() {
    final String path = getRelativePath();
    if (this.isInArchive()) {
      int slash = path.lastIndexOf('/');
      int exclamation = path.lastIndexOf('!');
      if (slash == -1 || exclamation == -1) throw new IllegalStateException("Cannot be in archive if path does not contains '!/': " + path);
      if (slash == exclamation + 1) { // Means "!/"
        // Go out of archive
        return path.substring(0, exclamation);
      } else {
        // Still in archive
        return path.substring(0, slash);
      }
    } else {
      if (path.equals("/") || path.equals("")) {
        return null;
      }
      final String parent = PathUtil.getParentPath(path);
      if ("".equals(parent)) {
        return "/";
      }
      return parent;
    }
  }

  @Nullable
  public FileDefRef getParent() {
    String path = getParentPath();
    if (path == null) {
      return null;
    }
    return new FileDefRef(PathUtil.getFileName(path), path);
  }

  public boolean isInArchive() {
    return myIsInArchive;
  }
}
