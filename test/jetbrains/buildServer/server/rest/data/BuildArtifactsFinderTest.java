/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.data;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import jetbrains.BuildServerCreator;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.db.TestDB;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import jetbrains.buildServer.zip.FileZipFactory;
import jetbrains.buildServer.zip.ZipFactory;
import jetbrains.buildServer.zip.ZipWriter;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 18.09.2014
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions"})
@Test
public class BuildArtifactsFinderTest extends BaseTestCase {
  private BuildServerCreator myFixture;
  private final TempFiles myTempFiles = new TempFiles();

  private BuildArtifactsFinder myBuildArtifactsFinder;

  private SFinishedBuild myBuildWithArtifacts;

  private void createTestFiles(final File targetDir) throws IOException {
    final File dotTeamCity = new File(targetDir, ".teamcity");
    dotTeamCity.mkdir();
    new File(dotTeamCity, "logs").mkdir(); //this is also created by default
    new File(dotTeamCity, "settings").mkdir(); //this is also created by default
    new File(dotTeamCity, "properties").mkdir(); //this is also created by default

    final File dotTeamCityDirA = new File(dotTeamCity, "dirA");
    dotTeamCityDirA.mkdir();

    new File(targetDir, "file.txt").createNewFile();

    final File dir1 = new File(targetDir, "dir1");
    dir1.mkdir();
    new File(dir1, "file.txt").createNewFile();

    ZipArchiveBuilder.using(new FileZipFactory(true, true)).createArchive(targetDir, "archive.zip").
      addFileWithContent("a/file1.txt", "content1").
      addFileWithContent("a/file2.txt", "content2").
      addFileWithContent("a/b/file3.txt", "content3").
      addFileWithContent("file4.txt", "content4").
      build();

    ZipArchiveBuilder.using(new FileZipFactory(true, true)).createArchive(new File(targetDir, ".teamcity/dirA"), "archive1.zip").
      addFileWithContent("a/file1.txt", "content1").
      addFileWithContent("a/file2.txt", "content2").
      addFileWithContent("a/b/file3.txt", "content3").
      addFileWithContent("file4.txt", "content4").
      build();
  }

  @BeforeClass
  protected void suiteSetUp() throws IOException {
    TestLogger.initInternalProperties();

    TestDB.createSchemaIfNotCreated();
    myFixture = new BuildServerCreator(BuildArtifactsFinderTest.class, myTempFiles.createTempDir());
    myFixture.createNewServer();
    myFixture.loadConfigurationFromDiskAndFireStartup();

    final SRunningBuild runningBuild = myFixture.startBuild();
    myBuildWithArtifacts = myFixture.finishBuild(runningBuild, false);

    final File artifactsDir = myBuildWithArtifacts.getArtifactsDirectory();
    artifactsDir.mkdirs();

    createTestFiles(artifactsDir);

    final PermissionChecker permissionChecker = new PermissionChecker(myFixture.getServer().getSecurityContext());

    myBuildArtifactsFinder = new BuildArtifactsFinder(permissionChecker);
  }

  @AfterClass
  protected void suiteTearDown() {
    myFixture.shutdown();
    myTempFiles.cleanup();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testLocatorSet1() throws Exception {
    List<ArtifactTreeElement> artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "", null);

    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive.zip");
  }

  @Test
  public void testLocatorHidden() throws Exception {
    List<ArtifactTreeElement> artifacts;

    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "hidden:true", null);

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity");


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, ".teamcity", "hidden:true", null);

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/logs");
    assertContainsByFullName(artifacts, ".teamcity/settings");
    assertContainsByFullName(artifacts, ".teamcity/properties");
    assertContainsByFullName(artifacts, ".teamcity/dirA");


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, ".teamcity/dirA", "hidden:true", null);

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");
  }

  @Test(expectedExceptions = jetbrains.buildServer.server.rest.errors.NotFoundException.class)
  public void testLocatorHiddenNotFound1() {
    List<ArtifactTreeElement> artifacts;

    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "dir1", "hidden:true", null);

    assertSize(0, artifacts);
  }

  @Test(expectedExceptions = jetbrains.buildServer.server.rest.errors.NotFoundException.class)
  public void testLocatorHiddenNotFound2() {
    List<ArtifactTreeElement> artifacts;

    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, ".teamcity", "", null);

    assertSize(0, artifacts);
  }

  @Test
  public void testLocatorArchive1() throws Exception {
    ArtifactTreeElement element;
    List<ArtifactTreeElement> artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "", null);

    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "browseArchives:true", null);

    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "browseArchives:false", null);

    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertNull(element.getChildren());


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "hidden:true", null);

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity");


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, ".teamcity", "hidden:true", null);

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/logs");
    assertContainsByFullName(artifacts, ".teamcity/settings");
    assertContainsByFullName(artifacts, ".teamcity/properties");
    assertContainsByFullName(artifacts, ".teamcity/dirA");


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, ".teamcity/dirA", "hidden:true", null);

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");
    element = findElement(artifacts, ".teamcity/dirA/archive1.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());

    // archive-specific
    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "", null);
    element = findElement(artifacts, "archive.zip");

    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "archive.zip", "", null);

    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, "archive.zip!/a");
    assertContainsByFullName(artifacts, "archive.zip!/file4.txt");


    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "archive.zip", "browseArchives:true", null);

    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, "archive.zip!/a");
    assertContainsByFullName(artifacts, "archive.zip!/file4.txt");
    element = findElement(artifacts, "archive.zip!/file4.txt");
    assertNotNull(element);
    assertEquals("file4.txt", element.getName());
    assertFalse(element.isArchive());


    /*
    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, ".teamcity/archive1.zip", "hidden:true", null);
    //this works as if browseArchives:false so far
    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/archive1.zip!/a");
    assertContainsByFullName(artifacts, ".teamcity/archive1.zip!/file4.txt");
    */

  }

  @Test
  public void testLocatorRecursive() throws Exception {
    ArtifactTreeElement element;
    List<ArtifactTreeElement> artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "recursive:true", null);

    assertSize(10, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a");
    assertContainsByFullName(artifacts, "archive.zip!/file4.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a/b");
    assertContainsByFullName(artifacts, "archive.zip!/a/file1.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a/file2.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a/b/file3.txt");

    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "recursive:true,browseArchives:false", null);

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");
  }

  private void assertContainsByFullName(final List<ArtifactTreeElement> artifacts, final String fullName) {
    if (findElement(artifacts, fullName) == null) {
      throw new AssertionFailedError("Collection does not contain element with full name '" + fullName + "'. Collection: " + artifacts);
    }
  }

  @Nullable
  private ArtifactTreeElement findElement(@NotNull final List<ArtifactTreeElement> artifacts, @NotNull final String fullName) {
    return CollectionsUtil.findFirst(artifacts, new Filter<ArtifactTreeElement>() {
      public boolean accept(@NotNull final ArtifactTreeElement data) {
        return fullName.equals(data.getFullName());
      }
    });
  }

  private void assertSize(final int expectedSize, final Collection collection) {
    if (expectedSize != collection.size()) {
      throw new AssertionFailedError("Size is " + collection.size() + " instead of " + expectedSize + ". Content: " + collection);
    }
  }

  //Copy of jetbrains.buildServer.zip.ZipArchiveBuilder in order not to create module dependency
  public static class ZipArchiveBuilder {

    private final ZipFactory myZipFactory;

    private ZipWriter myZipWriter;
    private File myArchive;

    private ZipArchiveBuilder(final ZipFactory zipFactory) {
      myZipFactory = zipFactory;
    }

    public static ZipArchiveBuilder using(ZipFactory zipFactory) {
      return new ZipArchiveBuilder(zipFactory);
    }

    public ZipArchiveBuilder createArchive(File dir, String name) throws IOException {
      myArchive = new File(dir, name);
      myZipWriter = myZipFactory.createZipArchive(myArchive.getAbsolutePath());
      return this;
    }

    public ZipArchiveBuilder addFileWithContent(final String filePath, final String content) throws IOException {
      return addFileWithContent(filePath, content.getBytes(Charset.defaultCharset()));
    }

    public ZipArchiveBuilder addFileWithContent(final String filePath, byte[] content) throws IOException {
      final OutputStream file = myZipWriter.createBinaryFile(filePath);
      file.write(content);
      file.close();
      return this;
    }

    public File build() throws IOException {
      myZipWriter.close();
      return myArchive;
    }
  }
}
