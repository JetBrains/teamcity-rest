package jetbrains.buildServer.server.rest.files;

import jetbrains.buildServer.util.impl.Lazy;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Vladislav.Rassokhin
 */
@Test
public class FileDefTest {

  @DataProvider(name = "parent-path-datas")
  public static String[][] getParentTestDatas() {
    return new String[][]{
        {"a/b/c", "a/b"},
        {"a/b/c/", "a/b"},
        {"a/b.zip", "a"},
        {"a/b.zip!/c.txt", "a/b.zip"},
        {"a/b.zip!/c.zip!/d", "a/b.zip!/c.zip"},
        {"a/b.zip!/c.zip!/d/e.txt", "a/b.zip!/c.zip!/d"},
        {"a/b.zip!/c/d.zip!/e", "a/b.zip!/c/d.zip"},
        {"a/b.zip!/c/d.zip!/e/f.txt", "a/b.zip!/c/d.zip!/e"},
        {"a", ""},
        {"/b.zip", ""},
        {"/", null},
        {"", null}
    };
  }

  @Test(dataProvider = "parent-path-datas")
  public void testParentPath(String path, String expected) throws Exception {
    final FileDef fileDef = new FileDef("a", path, true, 0L, 0L, new Lazy<Collection<FileDefRef>>() {
      @Nullable
      @Override
      protected Collection<FileDefRef> createValue() {
        return Collections.emptyList();
      }
    });
    Assert.assertEquals(fileDef.getParentPath(), expected, "Parent path for FileDef incorrect");
    final FileDefRef parent = fileDef.getParent();
    Assert.assertEquals(parent != null ? parent.getRelativePath() : null , expected, "Parent for FileDef incorrect");
  }
}
