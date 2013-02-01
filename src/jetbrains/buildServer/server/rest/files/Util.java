package jetbrains.buildServer.server.rest.files;

import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.util.*;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * @author Vladislav.Rassokhin
 */
public class Util {

  public static final String ARCHIVE_PATH_SEPARATOR = "!/";

  @NotNull
  public static FileDef getFileDef(@NotNull final String relativePath, @NotNull final SBuild build) throws NotFoundException, AuthorizationFailedException, OperationException {
    String pathInsideArchive = null;

    final BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
    BuildArtifactHolder holder = artifacts.findArtifact(relativePath);

    if (!holder.isAvailable() && relativePath.contains(ARCHIVE_PATH_SEPARATOR)) {
      int index = relativePath.length();
      while ((index = relativePath.lastIndexOf(ARCHIVE_PATH_SEPARATOR, index - 2)) != -1) {
        holder = artifacts.findArtifact(relativePath.substring(0, index));
        if (holder.isAvailable()) {
          pathInsideArchive = relativePath.substring(index + 1).replace('\\', '/');
          break;
        }
      }
    }
    if (!holder.isAvailable()) {
      throw new NotFoundException("No artifact found. Relative path: '" + relativePath + "'");
    }
    if (!holder.isAccessible()) {
      throw new AuthorizationFailedException("Artifact is not accessible with current user permissions. Relative path: '" + relativePath + "'");
    }
    if (pathInsideArchive == null) {
      return getFileDefFromBuildArtifact(holder.getArtifact());
    } else {
      try {
        return getFileDefFromArchive(holder.getArtifact(), pathInsideArchive, relativePath);
      } catch (IOException e) {
        throw new OperationException("Error while retrieving artifact '" + relativePath + "': " + e.getMessage(), e);
      }
    }
  }

  @NotNull
  public static FileDef getFileDefFromBuildArtifact(@NotNull final BuildArtifact artifact) {
    return new FileDef(artifact.getName(), artifact.getRelativePath(), artifact.isDirectory(), artifact.getSize(), artifact.getTimestamp(), false) {
      @NotNull
      @Override
      public Collection<FileDefRef> getChildrenLoad() throws IOException {
        if (artifact.isDirectory()) { // Directory
          return CollectionsUtil.convertCollection(artifact.getChildren(), new Converter<FileDefRef, BuildArtifact>() {
            public FileDefRef createFrom(@NotNull BuildArtifact source) {
              return new FileDefRef(source.getName(), source.getRelativePath());
            }
          });
        } else if (ArchiveUtil.getArchiveType(artifact.getName()) != ArchiveType.NOT_ARCHIVE) { // Archive file
          return getArchivedFolderEntries(artifact, "");
        } else { // Simple File, return empty
          return Collections.emptyList();
        }
      }
    };
  }

  @NotNull
  public static FileDef getFileDefFromArchive(@NotNull final BuildArtifact artifact,
                                              @NotNull final String path,
                                              @NotNull final String fullPath) throws IOException {
    return doInArchive(artifact, path, new DoInArchiveHandler<FileDef>() {
      public FileDef found(@NotNull final ArchiveEntry entry, ArchiveInputStream stream) {
        final String found = entry.getName().replace('\\', '/');

        final String name = new File(found).getName();
        final long size = entry.getSize();
        final long timestamp = entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().getTime() : 0;
        final boolean isDirectory = entry.isDirectory();
        return new FileDef(name, fullPath, isDirectory, size, timestamp, true) {
          @Override
          public Collection<FileDefRef> getChildrenLoad() throws IOException {
            if (isDirectory) {
              return getArchivedFolderEntries(artifact, path);
            } else if (ArchiveUtil.getArchiveType(name) != ArchiveType.NOT_ARCHIVE) {
              return getArchivedArchiveEntries(artifact, path);
            } else {
              return Collections.emptyList();
            }
          }
        };
      }
    });
  }

  public static Collection<FileDefRef> getArchivedArchiveEntries(@NotNull final BuildArtifact artifact, @NotNull final String innerPathToElement) throws IOException {
    return doInArchive(artifact, innerPathToElement, new DoInArchiveHandler<List<FileDefRef>>() {
      public List<FileDefRef> found(@NotNull final ArchiveEntry entry, @NotNull final ArchiveInputStream stream) throws IOException {
        ArchiveInputStream as = ArchiveUtil.getArchiveInputStream(entry.getName().replace('\\', '/'), new BufferedInputStream(stream));
        if (as == null) {
          throw new NotFoundException("File " + innerPathToElement + " not found in " + artifact.getRelativePath() + ": archive not supported");
        }
        final Set<String> names = new LinkedHashSet<String>();
        for (ArchiveEntry e = as.getNextEntry(); e != null; e = as.getNextEntry()) {
          final String n = removeTLSlashes(e.getName().replace('\\', '/'));
          int count = StringUtil.countChars(n, '/');
          if (count > 0) {
            names.add(n.substring(0, n.indexOf('/')));
          } else {
            names.add(n);
          }
        }
        return CollectionsUtil.convertCollection(names, new Converter<FileDefRef, String>() {
          public FileDefRef createFrom(@NotNull String source) {
            return new FileDefRef(source, artifact.getRelativePath() + ARCHIVE_PATH_SEPARATOR + innerPathToElement + ARCHIVE_PATH_SEPARATOR + source);
          }
        });
      }
    });
  }

  public static Collection<FileDefRef> getArchivedFolderEntries(@NotNull final BuildArtifact artifact, @NotNull final String innerPathToElement) throws IOException {
    final LinkedList<String> segments = splitByArchivePathSeparator(innerPathToElement, true);
    final InputStream artifactStream = artifact.getInputStream();
    final List<Closeable> toClose = new ArrayList<Closeable>();
    final StringBuilder collectedPath = new StringBuilder(artifact.getRelativePath() + ARCHIVE_PATH_SEPARATOR);
    toClose.add(artifactStream);
    try {
      ArchiveInputStream stream = ArchiveUtil.getArchiveInputStream(artifact.getName(), artifactStream);
      while (true) {
        if (stream == null) {
          throw new NotFoundException("File " + innerPathToElement + " not found in " + artifact.getRelativePath() + ": archive not supported");
        }
        toClose.add(stream);
        final String segment = StringUtil.removeLeadingSlash(segments.poll());
        if (segments.isEmpty()) {
          final Set<String> names = new LinkedHashSet<String>();
          for (ArchiveEntry entry = stream.getNextEntry(); entry != null; entry = stream.getNextEntry()) {
            final String name = StringUtil.removeLeadingSlash(entry.getName().replace('\\', '/'));
            if (equalsIgnoringTLSlashes(innerPathToElement, name)) {
              continue;
            }
            String nta;
            if (StringUtil.isEmptyOrSpaces(segment)) {
              nta = name;
            } else if (name.startsWith(segment)) {
              nta = name.substring(segment.length());
            } else {
              continue;
            }
            nta = removeTLSlashes(nta);
            if (StringUtil.isEmptyOrSpaces(nta)) {
              continue;
            }
            int count = StringUtil.countChars(nta, '/');
            if (count > 0) {
              names.add(nta.substring(0, nta.indexOf('/')));
            } else {
              names.add(nta);
            }
          }
          return CollectionsUtil.convertCollection(names, new Converter<FileDefRef, String>() {
            public FileDefRef createFrom(@NotNull String source) {
              return new FileDefRef(source, artifact.getRelativePath() + ARCHIVE_PATH_SEPARATOR + innerPathToElement + "/" + source);
            }
          });
        } else {
          final ArchiveEntry entry = positionArchivedPath(stream, segment);
          if (entry == null) {
            throw new NotFoundException("File " + innerPathToElement + " not found in " + artifact.getRelativePath());
          }
          final String name = entry.getName().replace('\\', '/');
          collectedPath.append(removeTLSlashes(name));
          collectedPath.append(ARCHIVE_PATH_SEPARATOR);
          stream = ArchiveUtil.getArchiveInputStream(name, new BufferedInputStream(stream));
        }
      }
    } finally {
      Collections.reverse(toClose);
      for (Closeable closeable : toClose) {
        FileUtil.close(closeable);
      }
    }
  }

  public static interface DoInArchiveHandler<T> {
    public T found(@NotNull final ArchiveEntry entry, @NotNull final ArchiveInputStream stream) throws IOException;
  }

  public static <T> T doInArchive(@NotNull final BuildArtifact artifact, @NotNull final String path, @NotNull final DoInArchiveHandler<T> callable) throws IOException {
    final LinkedList<String> segments = splitByArchivePathSeparator(path, false);
    final InputStream artifactStream = artifact.getInputStream();
    final List<Closeable> toClose = new ArrayList<Closeable>();
    toClose.add(artifactStream);
    try {
      ArchiveInputStream stream = ArchiveUtil.getArchiveInputStream(artifact.getName(), artifactStream);
      while (true) {
        if (stream == null) {
          throw new NotFoundException("File " + path + " not found in " + artifact.getRelativePath() + ": archive not supported");
        }
        toClose.add(stream);
        final String segment = segments.poll();
        final ArchiveEntry entry = positionArchivedPath(stream, segment);
        if (entry == null) {
          throw new NotFoundException("File " + path + " not found in " + artifact.getRelativePath());
        }
        final String found = entry.getName().replace('\\', '/');

        if (segments.isEmpty()) {
          return callable.found(entry, stream);
        }
        stream = ArchiveUtil.getArchiveInputStream(found, new BufferedInputStream(stream));
      }
    } finally {
      Collections.reverse(toClose);
      for (Closeable closeable : toClose) {
        FileUtil.close(closeable);
      }
    }
  }

  private static ArchiveEntry positionArchivedPath(@NotNull final ArchiveInputStream stream, @NotNull String path) throws IOException {
    for (ArchiveEntry entry = stream.getNextEntry(); entry != null; entry = stream.getNextEntry()) {
      final String name = entry.getName().replace('\\', '/');
      if (equalsIgnoringTLSlashes(name, path)) {
        return entry;
      }
    }
    return null;
  }

  public static boolean equalsIgnoringTLSlashes(@NotNull String a, @NotNull String b) {
    a = removeTLSlashes(a);
    b = removeTLSlashes(b);
    return a.equals(b);
  }

  public static String removeTLSlashes(String s) {
    boolean rl = s.startsWith("/");
    boolean rt = s.endsWith("/");
    int l = s.length();
    return l == 1 && rl ? "" : s.substring(rl ? 1 : 0, l - (rt ? 1 : 0));
  }

  public static LinkedList<String> splitByArchivePathSeparator(String path, boolean withEmpty) {
    LinkedList<String> list = new LinkedList<String>(Arrays.asList(path.split(ARCHIVE_PATH_SEPARATOR)));
    if (withEmpty && path.endsWith(ARCHIVE_PATH_SEPARATOR)) {
      list.add("");
    }
    return list;
  }
}
