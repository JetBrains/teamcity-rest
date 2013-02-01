package jetbrains.buildServer.server.rest.files;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Vladislav.Rassokhin
 */
public class FileChildrenCheckInfo {
  public final String file;
  public final String path;
  public final String[] childs;

  public FileChildrenCheckInfo(String file, String path, String... childs) {
    this.file = file;
    this.path = path;
    this.childs = childs;
  }

  @Override
  public String toString() {
    return "FileChildrenCheckInfo{" +
        "file='" + file + '\'' +
        ", path='" + path + '\'' +
        ", childs=" + Arrays.toString(childs) +
        '}';
  }

  public Set<String> getChildsSet() {
    HashSet<String> strings = new HashSet<String>();
    Collections.addAll(strings, childs);
    return strings;
  }

}
