package jetbrains.buildServer.server.rest.files;

import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.Mockito.*;

/**
 * @author Vladislav.Rassokhin
 */
public class UtilTest {

  @DataProvider(name = "remove-slashes-data")
  public Object[][] getRemoveSlashesData() {
    return new Object[][]{
        {"", ""},
        {"", "/"},
        {"", "//"},
        {"a", "a"},
        {"a", "/a"},
        {"a", "a/"},
        {"a", "/a/"},
        {"a/b", "a/b/"},
        {"a/b", "/a/b/"},
    };
  }

  @DataProvider(name = "split-data")
  public Object[][] getSplitData() {
    return new Object[][]{
        {Arrays.asList("a"), "a", false},
        {Arrays.asList("a", "b"), "a!/b", false},
        {Arrays.asList("a", "b/c", "d"), "a!/b/c!/d", false},
        {Arrays.asList("a"), "a!/", false},
        {Arrays.asList("a", "b"), "a!/b!/", false},
        {Arrays.asList("a", "b/c", "d"), "a!/b/c!/d!/", false},
        {Arrays.asList("a", ""), "a!/", true},
        {Arrays.asList("a", "b", ""), "a!/b!/", true},
        {Arrays.asList("a", "b/c", "d", ""), "a!/b/c!/d!/", true},
    };
  }

  @Test(dataProvider = "remove-slashes-data")
  public void testRemoveTLSlashes(String expected, String base) throws Exception {
    Assert.assertEquals(expected, Util.removeTLSlashes(base));
  }

  @Test(dataProviderClass = ArchivesDataProvider.class, dataProvider = "archived-archives")
  public void testGetArchivedArchiveEntries(final FileChildrenCheckInfo info) throws Exception {
    final AtomicInteger myStreamUsages;

    myStreamUsages = new AtomicInteger(0);
    final BuildArtifact artifact = mock(BuildArtifact.class);
    when(artifact.getName()).thenReturn(info.file);
    when(artifact.getRelativePath()).thenReturn(info.file);
    when(artifact.getInputStream()).thenAnswer(new Answer<InputStream>() {
      public InputStream answer(InvocationOnMock invocation) throws Throwable {
        myStreamUsages.incrementAndGet();
        return new FileInputStream(getFile(info.file)) {
          private boolean myIsClosed = false;

          @Override
          public void close() throws IOException {
            if (!myIsClosed) {
              myIsClosed = true;
              myStreamUsages.decrementAndGet();
              super.close();
            }
          }
        };
      }
    });

    final Collection<FileDefRef> refs = Util.getArchivedArchiveEntries(artifact, info.path);
    System.out.println("refs = " + refs);
    assertChildrenEquals(refs, info.childs);
    for (FileDefRef ref : refs) {
      Assert.assertEquals(ref.getRelativePath(), artifact.getRelativePath() + "!/" + info.path + "!/" + ref.getName());
    }
    Assert.assertEquals(myStreamUsages.get(), 0);
  }

  @Test(dataProvider = "split-data")
  public void testSplitByArchivePathSeparator(List<String> expected, String path, Boolean empty) throws Exception {
    LinkedList<String> list = Util.splitByArchivePathSeparator(path, empty);
    Assert.assertEquals(list, expected);
  }

  @Test(dataProviderClass = ArchivesDataProvider.class, dataProvider = "archived-folders")
  public void testGetArchivedFolderEntries(final FileChildrenCheckInfo info) throws Exception {
    final AtomicInteger myStreamUsages;

    myStreamUsages = new AtomicInteger(0);
    final BuildArtifact artifact = mock(BuildArtifact.class);
    when(artifact.getName()).thenReturn(info.file);
    when(artifact.getRelativePath()).thenReturn(info.file);
    when(artifact.getInputStream()).thenAnswer(new Answer<InputStream>() {
      public InputStream answer(InvocationOnMock invocation) throws Throwable {
        myStreamUsages.incrementAndGet();
        return new FileInputStream(getFile(info.file)) {
          private boolean myIsClosed = false;

          @Override
          public void close() throws IOException {
            if (!myIsClosed) {
              myIsClosed = true;
              myStreamUsages.decrementAndGet();
              super.close();
            }
          }
        };
      }
    });

    final Collection<FileDefRef> refs = Util.getArchivedFolderEntries(artifact, info.path);
    System.out.println("refs = " + refs);
    assertChildrenEquals(refs, info.childs);
    for (FileDefRef ref : refs) {
      Assert.assertEquals(ref.getRelativePath(), artifact.getRelativePath() + "!/" + info.path + "/" + ref.getName());
    }
    Assert.assertEquals(myStreamUsages.get(), 0);
  }

  private void assertChildrenEquals(Collection<FileDefRef> refs, String[] expected) {
    String[] names = CollectionsUtil.convertCollection(refs, new Converter<String, FileDefRef>() {
      public String createFrom(@NotNull FileDefRef source) {
        return source.getName();
      }
    }).toArray(new String[0]);
    Assert.assertEqualsNoOrder(names, expected);
  }

  private File getFile(String file) {
    return new File(getTestDataDir(), "archives/" + file);
  }

  private File getTestDataDir() {
    File wd = new File("").getAbsoluteFile();
    if (new File(wd, "rest-api.xml").exists()) {
      return new File(wd, "testData");
    } else {
      return new File("svnrepo/rest-api/testData");
    }
  }
}
