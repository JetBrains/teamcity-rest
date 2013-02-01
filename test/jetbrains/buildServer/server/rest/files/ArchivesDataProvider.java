package jetbrains.buildServer.server.rest.files;

import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vladislav.Rassokhin
 */
public class ArchivesDataProvider {
  @DataProvider(name = "archived-folders")
  public static Iterator<Object[]> getArchivedFolderTestDataProvider() {
    List<FileChildrenCheckInfo> result = new ArrayList<FileChildrenCheckInfo>();

    result.add(new FileChildrenCheckInfo("1.zip", "", "a"));
    result.add(new FileChildrenCheckInfo("1.zip", "a", "b", "e"));
    result.add(new FileChildrenCheckInfo("1.zip", "a/b", "c", "d"));
    result.add(new FileChildrenCheckInfo("1.zip", "a/b/c", "01.txt", "02.txt"));
    result.add(new FileChildrenCheckInfo("1-d.zip", "", "a"));
    result.add(new FileChildrenCheckInfo("1-d.zip", "a", "b", "e"));
    result.add(new FileChildrenCheckInfo("1-d.zip", "a/b", "c", "d"));
    result.add(new FileChildrenCheckInfo("1-d.zip", "a/b/c", "01.txt", "02.txt"));
    result.add(new FileChildrenCheckInfo("1.tar.gz", "", "a"));
    result.add(new FileChildrenCheckInfo("1.tar.gz", "a", "b", "e"));
    result.add(new FileChildrenCheckInfo("1.tar.gz", "a/b", "c", "d"));
    result.add(new FileChildrenCheckInfo("1.tar.gz", "a/b/c", "01.txt", "02.txt"));
    result.add(new FileChildrenCheckInfo("1.tar", "", "a"));
    result.add(new FileChildrenCheckInfo("1.tar", "a", "b", "e"));
    result.add(new FileChildrenCheckInfo("1.tar", "a/b", "c", "d"));
    result.add(new FileChildrenCheckInfo("1.tar", "a/b/c", "01.txt", "02.txt"));


    result.add(new FileChildrenCheckInfo("pad.zip", "", "z"));
    result.add(new FileChildrenCheckInfo("pad.zip", "z", "2.zip", "2-d.zip", "2.tar.gz", "2.tar"));
    result.add(new FileChildrenCheckInfo("pad.zip", "z/2.zip!/", "a"));
    result.add(new FileChildrenCheckInfo("pad.zip", "z/2.zip!/a", "b", "e"));
    result.add(new FileChildrenCheckInfo("pad.zip", "z/2.zip!/a/b", "c", "d"));

    result.add(new FileChildrenCheckInfo("over.zip", "pad.zip!/z/2.tar!/", "a"));
    result.add(new FileChildrenCheckInfo("over.zip", "pad.zip!/z/2.tar!/a/e", "21.txt", "22.txt", "23.txt", "f"));

    return CollectionsUtil.convertCollection(result, new Converter<Object[], FileChildrenCheckInfo>() {
      public Object[] createFrom(@NotNull FileChildrenCheckInfo source) {
        return new Object[]{source};
      }
    }).iterator();
  }

  @DataProvider(name = "archived-archives")
  public static Iterator<Object[]> getArchivedArchivesTestDataProvider() {
    List<FileChildrenCheckInfo> result = new ArrayList<FileChildrenCheckInfo>();
    result.add(new FileChildrenCheckInfo("pad.zip", "z/2.zip", "a"));
    result.add(new FileChildrenCheckInfo("pad.zip", "z/2.tar.gz", "a"));
    result.add(new FileChildrenCheckInfo("over.zip", "pad.zip!/z/2.tar", "a"));

    return CollectionsUtil.convertCollection(result, new Converter<Object[], FileChildrenCheckInfo>() {
      public Object[] createFrom(@NotNull FileChildrenCheckInfo source) {
        return new Object[]{source};
      }
    }).iterator();
  }
}
