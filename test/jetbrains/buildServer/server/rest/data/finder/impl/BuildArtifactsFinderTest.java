/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.BuildServerCreator;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.server.rest.data.TimeCondition;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.db.TestDB;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import jetbrains.buildServer.zip.FileZipFactory;
import jetbrains.buildServer.zip.ZipFactory;
import jetbrains.buildServer.zip.ZipWriter;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.TeamCityAsserts.assertLess;

/**
 * @author Yegor.Yarko
 * Date: 18.09.2014
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions"})
@Test
public class BuildArtifactsFinderTest extends BaseTestCase {
  private final TempFiles myTempFiles = new TempFiles();
  private BuildServerCreator myFixture;
  private SFinishedBuild myBuildWithArtifacts;
  private File myFile1;
  private File myFile2;

  @SuppressWarnings("UnstableApiUsage")
  private void createTestFiles(final File targetDir) throws IOException {
    final File dotTeamCity = new File(targetDir, ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR);
    dotTeamCity.mkdir();
    new File(dotTeamCity, "logs").mkdir(); //this is also created by default
    new File(dotTeamCity, "settings").mkdir(); //this is also created by default
    new File(dotTeamCity, "properties").mkdir(); //this is also created by default

    final File dotTeamCityDirA = new File(dotTeamCity, "dirA");
    dotTeamCityDirA.mkdir();

    myFile1 = new File(targetDir, "file.txt");
    myFile1.createNewFile();

    final File dir1 = new File(targetDir, "dir1");
    dir1.mkdir();
    myFile2 = new File(dir1, "file.txt");
    myFile2.createNewFile();

    ZipArchiveBuilder.using(new FileZipFactory(true, true)).createArchive(targetDir, "archive.zip").
      addFileWithContent("a/file1.txt", "content1").
                       addFileWithContent("a/file2.txt", "content2").
                       addFileWithContent("a/b/file3.txt", "content3").
                       addFileWithContent("file4.txt", "content4").
                       build();

    ZipArchiveBuilder.using(new FileZipFactory(true, true)).createArchive(targetDir, "archive_nested.zip").
      addFileWithContent("archive.zip", Files.toByteArray(new File(targetDir, "archive.zip"))).
                       addFileWithContent("file4.txt", "content4").
                       build();

    ZipArchiveBuilder.using(new FileZipFactory(true, true)).createArchive(new File(targetDir, ".teamcity/dirA"), "archive1.zip").
      addFileWithContent("a/file1.txt", "content1").
                       addFileWithContent("a/file2.txt", "content2").
                       addFileWithContent("a/b/file3.txt", "content3").
                       addFileWithContent("file4.txt", "content4").
                       build();
    final File extendedHiddenDir = new File(targetDir, ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + ".extended");
    extendedHiddenDir.mkdir();
    final File underExtendedHiddenDir = new File(extendedHiddenDir, "file.json");
    underExtendedHiddenDir.createNewFile();
  }

  @BeforeClass
  protected void suiteSetUp() throws IOException {
    TestDB.createSchemaIfNotCreated();
    myFixture = new BuildServerCreator(BuildArtifactsFinderTest.class, myTempFiles.createTempDir(), TestDB.getTestDBAccess());
    myFixture.createNewServer();
    myFixture.loadConfigurationFromDiskAndFireStartup();

    TimeCondition timeCondition = new TimeCondition(myFixture);
    myFixture.addService(timeCondition);

    final SRunningBuild runningBuild = myFixture.startBuild();
    myBuildWithArtifacts = myFixture.finishBuild(runningBuild, false);

    final File artifactsDir = myBuildWithArtifacts.getArtifactsDirectory();
    artifactsDir.mkdirs();

    createTestFiles(artifactsDir);
  }

  @AfterClass
  protected void suiteTearDown() {
    myFixture.shutdown();
    myTempFiles.cleanup();
  }

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testOrder() {
    List<ArtifactTreeElement> artifacts = getArtifacts("", null);
    checkOrderedCollection(getNames(artifacts), "dir1", "archive.zip", "archive_nested.zip", "file.txt");
  }

  private List<ArtifactTreeElement> getArtifacts(final String path, final String filesLocator) {
    return getArtifacts(path, filesLocator, null, myBuildWithArtifacts.getBuildPromotion());
  }

  private List<ArtifactTreeElement> getArtifacts(final String path, final String filesLocator, final String basePath, final BuildPromotion build) {
//    FilesSubResource.fileApiUrlBuilder(filesLocator, BuildRequest.getArtifactsUrlPrefix(myBuildWithArtifacts, urlPrefix));
    return BuildArtifactsFinder.getItems(BuildArtifactsFinder.getArtifactElement(build, path, myFixture), basePath, filesLocator,
                                         null, myFixture);
  }

  private List<String> getNames(final List<ArtifactTreeElement> artifacts) {
    return CollectionsUtil.convertCollection(artifacts, Element::getFullName);
  }

  public void testLocatorSet1() {
    List<ArtifactTreeElement> artifacts = getArtifacts("", null);

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip");
  }

  @Test
  public void testLocatorHidden() {
    List<ArtifactTreeElement> artifacts;

    artifacts = getArtifacts("", "hidden:any");

    assertSize(6, artifacts);
    assertContainsByFullName(artifacts, ".teamcity");
    assertContainsByFullName(artifacts, ".teamcity.extended");

    artifacts = getArtifacts("", "hidden:true");

    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, ".teamcity");
    assertContainsByFullName(artifacts, ".teamcity.extended");

    artifacts = getArtifacts(".teamcity", "hidden:true");

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/logs");
    assertContainsByFullName(artifacts, ".teamcity/settings");
    assertContainsByFullName(artifacts, ".teamcity/properties");
    assertContainsByFullName(artifacts, ".teamcity/dirA");

    artifacts = getArtifacts(".teamcity.extended", "hidden:true");
    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity.extended/file.json");


    artifacts = getArtifacts(".teamcity/dirA", "hidden:true");

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");
  }

  @Test(expectedExceptions = jetbrains.buildServer.server.rest.errors.NotFoundException.class)
  public void testLocatorHiddenNotFound1() {
    getArtifacts("dir_missing", null);
  }

  @Test(expectedExceptions = jetbrains.buildServer.server.rest.errors.LocatorProcessException.class)
  public void testUnknownLocator() {
    getArtifacts("", "aaa:bbb");
  }

  @Test(expectedExceptions = jetbrains.buildServer.server.rest.errors.BadRequestException.class)
  public void testUnknownLocator2() {
    getArtifacts("", "modified:bbb");
  }

  @Test
  public void testLocatorHiddenNotFound2() {
    List<ArtifactTreeElement> artifacts;

    artifacts = getArtifacts(".teamcity", null);
    assertSize(4, artifacts);

    artifacts = getArtifacts(".teamcity/logs", null);
    int logsCount = artifacts.size();

    artifacts = getArtifacts(".teamcity/logs", "hidden:true");
    assertSize(logsCount, artifacts);

    artifacts = getArtifacts(".teamcity/logs", "hidden:false");
    assertSize(logsCount, artifacts);
  }

  @Test
  public void testLocatorHiddenNotFound3() {
    List<ArtifactTreeElement> artifacts;

    artifacts = getArtifacts("dir1", "hidden:true");

    assertSize(0, artifacts);
  }

  @Test
  public void testLocatorArchive1() {
    ArtifactTreeElement element;
    List<ArtifactTreeElement> artifacts = getArtifacts("", null);

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "archive.zip");

    element = findElement(artifacts, "dir1");
    assertFalse(element.isArchive());
    assertFalse(element.isLeaf());
    assertFalse(element.isContentAvailable());
    assertSize(1, Lists.newArrayList(element.getChildren()));

    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertNull(element.getChildren());  //as archives browsing not enabled


    artifacts = getArtifacts("", "browseArchives:true");

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));


    artifacts = getArtifacts("", "browseArchives:false");

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertNull(element.getChildren());


    artifacts = getArtifacts("", "hidden:true");

    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, ".teamcity");
    assertContainsByFullName(artifacts, ".teamcity.extended");


    artifacts = getArtifacts(".teamcity", "hidden:true");

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/logs");
    assertContainsByFullName(artifacts, ".teamcity/settings");
    assertContainsByFullName(artifacts, ".teamcity/properties");
    assertContainsByFullName(artifacts, ".teamcity/dirA");


    artifacts = getArtifacts(".teamcity/dirA", "hidden:true");

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");
    element = findElement(artifacts, ".teamcity/dirA/archive1.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());

    artifacts = getArtifacts(".teamcity/dirA", "hidden:true,browseArchives:false");

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");
    element = findElement(artifacts, ".teamcity/dirA/archive1.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());

    artifacts = getArtifacts(".teamcity/dirA", "hidden:true,browseArchives:true");

    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");
    element = findElement(artifacts, ".teamcity/dirA/archive1.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = getArtifacts(".teamcity/dirA", "hidden:true,browseArchives:true,recursive:true");

    assertSize(7, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/file4.txt");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a/file1.txt");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a/file2.txt");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a/b");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a/b/file3.txt");
    element = findElement(artifacts, ".teamcity/dirA/archive1.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = getArtifacts(".teamcity/dirA", "hidden:true,browseArchives:true,recursive:true", ".teamcity/dirA", myBuildWithArtifacts.getBuildPromotion());

    assertSize(7, artifacts);
    assertContainsByFullName(artifacts, "archive1.zip");
    assertContainsByFullName(artifacts, "archive1.zip!/file4.txt");
    assertContainsByFullName(artifacts, "archive1.zip!/a");
    assertContainsByFullName(artifacts, "archive1.zip!/a/file1.txt");
    assertContainsByFullName(artifacts, "archive1.zip!/a/file2.txt");
    assertContainsByFullName(artifacts, "archive1.zip!/a/b");
    assertContainsByFullName(artifacts, "archive1.zip!/a/b/file3.txt");
    element = findElement(artifacts, "archive1.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = getArtifacts(".teamcity/dirA", "hidden:true,browseArchives:true,recursive:true,directory:false");

    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/file4.txt");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a/file1.txt");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a/file2.txt");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a/b/file3.txt");

    artifacts = getArtifacts("archive.zip", null);

    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, "archive.zip!/a");
    assertContainsByFullName(artifacts, "archive.zip!/file4.txt");


    artifacts = getArtifacts("archive.zip", "browseArchives:true");

    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, "archive.zip!/a");
    assertContainsByFullName(artifacts, "archive.zip!/file4.txt");
    element = findElement(artifacts, "archive.zip!/file4.txt");
    assertNotNull(element);
    assertEquals("file4.txt", element.getName());
    assertFalse(element.isArchive());


    artifacts = getArtifacts(".teamcity", "hidden:true");
    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA");
    assertContainsByFullName(artifacts, ".teamcity/logs");
    assertContainsByFullName(artifacts, ".teamcity/properties");
    assertContainsByFullName(artifacts, ".teamcity/settings");

    artifacts = getArtifacts(".teamcity/dirA", "hidden:true");
    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip");


    artifacts = getArtifacts(".teamcity/dirA", "hidden:true,browseArchives:true");
    assertSize(1, artifacts);
    element = findElement(artifacts, ".teamcity/dirA/archive1.zip");
    assertNotNull(element);
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = getArtifacts(".teamcity/dirA/archive1.zip", "hidden:true");
    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/file4.txt");

    artifacts = getArtifacts(".teamcity/dirA/archive1.zip", "hidden:true,browseArchives:true");
    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/a");
    assertContainsByFullName(artifacts, ".teamcity/dirA/archive1.zip!/file4.txt");
  }

  @Test
  public void testLocatorDirectory() {
    ArtifactTreeElement element;
    List<ArtifactTreeElement> artifacts;

    artifacts = getArtifacts("", "directory:any");
    assertSize(4, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertNull(element.getChildren());

    artifacts = getArtifacts("", "directory:true");
    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, "dir1");

    artifacts = getArtifacts("", "directory:false");
    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertNull(element.getChildren());

    artifacts = getArtifacts("", "directory:false,browseArchives:true");
    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, "file.txt");

    artifacts = getArtifacts("", "directory:true,browseArchives:true");
    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "archive.zip");
    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));
  }

  @Test
  public void testLocatorRecursive() {
    ArtifactTreeElement element;
    List<ArtifactTreeElement> artifacts = getArtifacts("", "recursive:true");

    assertSize(5, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");

    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertNull(element.getChildren());

    artifacts = getArtifacts("", "recursive:true,browseArchives:true");

    assertSize(13, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a");
    assertContainsByFullName(artifacts, "archive.zip!/file4.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a/b");
    assertContainsByFullName(artifacts, "archive.zip!/a/file1.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a/file2.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a/b/file3.txt");
    assertContainsByFullName(artifacts, "archive_nested.zip!/archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip!/file4.txt");

    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = getArtifacts("", "recursive:2,browseArchives:true");

    assertSize(9, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");
    assertContainsByFullName(artifacts, "archive.zip!/a");
    assertContainsByFullName(artifacts, "archive.zip!/file4.txt");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip!/archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip!/file4.txt");

    element = findElement(artifacts, "archive.zip");
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = getArtifacts("", "recursive:true,browseArchives:false");

    assertSize(5, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");
  }

  @Test
  public void testNestedArchives() {
    ArtifactTreeElement element;
    List<ArtifactTreeElement> artifacts = getArtifacts("archive_nested.zip", null);

    assertSize(2, artifacts);
    element = findElement(artifacts, "archive_nested.zip!/archive.zip");
    assertNotNull(element);
    assertTrue(element.isArchive());
    assertTrue(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertNull(element.getChildren());

    artifacts = getArtifacts("archive_nested.zip", "browseArchives:true");

    assertSize(2, artifacts);
    element = findElement(artifacts, "archive_nested.zip!/archive.zip");
    assertNotNull(element);
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));

    artifacts = getArtifacts("archive_nested.zip", "browseArchives:true,recursive:true");

    assertSize(8, artifacts);
    element = findElement(artifacts, "archive_nested.zip!/archive.zip");
    assertNotNull(element);
    assertTrue(element.isArchive());
    assertFalse(element.isLeaf());
    assertTrue(element.isContentAvailable());
    assertSize(2, Lists.newArrayList(element.getChildren()));
  }

  @Test
  public void testPattern() {
    List<ArtifactTreeElement> artifacts = getArtifacts("", "pattern:*.txt");
    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, "file.txt");

    artifacts = getArtifacts("", "recursive:true,pattern:*.txt");
    assertSize(1, artifacts);
    assertContainsByFullName(artifacts, "file.txt");


    /*
    // https://youtrack.jetbrains.com/issue/TW-41613
    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "pattern:(+:**,-:*.txt)", null);
    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip");

    artifacts = myBuildArtifactsFinder.getArtifacts(myBuildWithArtifacts, "", "recursive:true,pattern:(+:**,-:*.txt)", null);
    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "archive.zip");
    assertContainsByFullName(artifacts, "archive_nested.zip");
    */

    artifacts = getArtifacts("", "recursive:true,pattern:**/*.txt");
    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");

    artifacts = getArtifacts("", "recursive:true,pattern:(**/*.txt,d*)");
    assertSize(3, artifacts);
    assertContainsByFullName(artifacts, "dir1");
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "dir1/file.txt");

    artifacts = getArtifacts("", "recursive:true,pattern:(file.txt,archive.zip)");
    assertSize(2, artifacts);
    assertContainsByFullName(artifacts, "file.txt");
    assertContainsByFullName(artifacts, "archive.zip");
  }

  @Test
  public void testPathWithPatterns() {
    assertEquals("file.txt", getArtifact("file.txt"));
    assertEquals("file.txt", getArtifact("fil?.txt"));
    assertEquals("archive_nested.zip", getArtifact("archiv*d.zip"));
    assertEquals("archive.zip", getArtifact("archive.???"));
    assertEquals("archive.zip", getArtifact("archive.*"));
    assertEquals("file.txt", getArtifact("*.txt"));
//    assertEquals("dir1/file.txt", getArtifact("d*/file.txt")); https://youtrack.jetbrains.com/issue/TW-43015
  }

  @Test
  public void testModified() {
    myFile1.setLastModified(Dates.now().getTime() - 10 * 60 * 1000); //file.txt  -10 minutes
    myFile2.setLastModified(Dates.now().getTime() - 5 * 60 * 1000); //dir1/file.txt  -5 minutes

    List<ArtifactTreeElement> artifacts = getArtifacts("", "modified:-30m");
    checkOrderedCollection(getNames(artifacts), "dir1", "archive.zip", "archive_nested.zip", "file.txt");

    artifacts = getArtifacts("", "modified:-7m");
    checkOrderedCollection(getNames(artifacts), "dir1", "archive.zip", "archive_nested.zip");

    artifacts = getArtifacts("", "modified:-30m,modified:(condition:before,date:-4m),recursive:true");
    checkOrderedCollection(getNames(artifacts), "dir1/file.txt", "file.txt");

    artifacts = getArtifacts("", "modified:-30m,modified:(condition:before,date:-6m),recursive:true");
    checkOrderedCollection(getNames(artifacts), "file.txt");
  }

  @Test
  public void testSize() throws Exception {
    File dir = new File(myBuildWithArtifacts.getArtifactsDirectory(), "sizeTest");
    dir.mkdir();
    createFileOfSize(dir, "file0", 0);
    createFileOfSize(dir, "file1", 1023);
    createFileOfSize(dir, "file2", 1024);
    createFileOfSize(dir, "file3", 1025);
    createFileOfSize(dir, "file4", 2 * 1024 + 1);

    File dir1 = new File(dir, "dir");
    dir1.mkdir();
    createFileOfSize(dir1, "file0", 0);

    checkOrderedCollection(getNames(getArtifacts(dir.getName(), null)), "sizeTest/dir", "sizeTest/file0", "sizeTest/file1", "sizeTest/file2", "sizeTest/file3", "sizeTest/file4");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "recursive:true")),
                           "sizeTest/dir", "sizeTest/dir/file0", "sizeTest/file0", "sizeTest/file1", "sizeTest/file2", "sizeTest/file3", "sizeTest/file4");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:1023")), "sizeTest/dir", "sizeTest/file0", "sizeTest/file1");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:1024")), "sizeTest/dir", "sizeTest/file0", "sizeTest/file1", "sizeTest/file2");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:1Kb")), "sizeTest/dir", "sizeTest/file0", "sizeTest/file1", "sizeTest/file2");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:1050")), "sizeTest/dir", "sizeTest/file0", "sizeTest/file1", "sizeTest/file2", "sizeTest/file3");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:2Kb")), "sizeTest/dir", "sizeTest/file0", "sizeTest/file1", "sizeTest/file2", "sizeTest/file3");

    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:0")), "sizeTest/dir", "sizeTest/file0");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:-1")), "sizeTest/dir");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "size:-10")), "sizeTest/dir");

    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "recursive:true,size:0")), "sizeTest/dir", "sizeTest/dir/file0", "sizeTest/file0");
    checkOrderedCollection(getNames(getArtifacts(dir.getName(), "directory:false,recursive:true,size:-1")));

    assertExceptionThrown(() -> getArtifacts(dir.getName(), "size:aa"), BadRequestException.class);
  }

  @Test
  public void testOrderSameLevel() throws Exception {
    final SRunningBuild runningBuild = myFixture.startBuild();
    final File artifactsDir = myFixture.finishBuild(runningBuild, false).getArtifactsDirectory();
    artifactsDir.mkdirs();

    File dir = new File(artifactsDir, "orderTest");
    dir.mkdir();
    createFileOfSize(dir, "a1", 5);
    createFileOfSize(dir, "a4", 5);
    createFileOfSize(dir, "b1", 5);
    createFileOfSize(dir, "c1", 5);
    createFileOfSize(dir, "A21", 5);
    createFileOfSize(dir, "A2", 5);
    createFileOfSize(dir, "A3", 5);
    createFileOfSize(dir, "B2", 0);
    createFileOfSize(dir, "B4", 15);
    createFileOfSize(dir, "C2", 5);
    createFileOfSize(dir, "0", 5);
    createFileOfSize(dir, "1", 5);
    createFileOfSize(dir, "2", 5);
    createFileOfSize(dir, "10", 5);
    createFileOfSize(dir, "01", 5);
    createFileOfSize(dir, "_", 5);
    new File(dir, "B3").mkdir();
    File dir2 = new File(artifactsDir, "orderTesa");
    dir2.mkdir();
    createFileOfSize(dir2, "a2", 5);

    checkOrderedCollection(getNames(getArtifacts(dir.getName(), null, null, runningBuild.getBuildPromotion())),
                           "orderTest/B3",
                           "orderTest/0",
                           "orderTest/01",
                           "orderTest/1",
                           "orderTest/2",
                           "orderTest/10",
                           "orderTest/_",
                           "orderTest/a1",
                           "orderTest/A2",
                           "orderTest/A3",
                           "orderTest/a4",
                           "orderTest/A21",
                           "orderTest/b1",
                           "orderTest/B2",
                           "orderTest/B4",
                           "orderTest/c1",
                           "orderTest/C2"
    );
  }

  @Test
  public void testOrderRecursive() throws Exception {
    final SRunningBuild runningBuild = myFixture.startBuild();
    final File artifactsDir = myFixture.finishBuild(runningBuild, false).getArtifactsDirectory();
    artifactsDir.mkdirs();

    createFileOfSize(artifactsDir, "file.txt", 5);
    createFileOfSize(artifactsDir, "a.txt", 5);
    createFileOfSize(artifactsDir, "dir1", 0); //file with name like dir

    File dir2 = new File(artifactsDir, "dir2");
    dir2.mkdir();
    createFileOfSize(dir2, "file.txt", 5);
    createFileOfSize(dir2, "filE1.txt", 0);

    File dir3 = new File(artifactsDir, "dir3");
    dir3.mkdir();

    File dir0 = new File(artifactsDir, "dir0");
    dir0.mkdir();
    createFileOfSize(dir0, "file13.txt", 5);
    createFileOfSize(dir0, "fil.txt", 5);
    createFileOfSize(dir0, "a", 5);
    createFileOfSize(dir0, "filf.txt", 5);
    createFileOfSize(dir0, "fild.txt", 5);
    createFileOfSize(dir0, "filE12.txt", 5);
    createFileOfSize(dir0, "filE14.txt", 5);
    createFileOfSize(dir0, "filE.txt", 5);
    File dir01 = new File(dir0, "dir01");
    dir01.mkdir();

    checkOrderedCollection(getNames(getArtifacts("", "recursive:true", null, runningBuild.getBuildPromotion())),
                           "dir0",
                           "dir0/dir01",
                           "dir0/a",
                           "dir0/fil.txt",
                           "dir0/fild.txt",
                           "dir0/filE.txt",
                           "dir0/filE12.txt",
                           "dir0/file13.txt",
                           "dir0/filE14.txt",
                           "dir0/filf.txt",
                           "dir2",
                           "dir2/file.txt",
                           "dir2/filE1.txt",
                           "dir3",
                           "a.txt",
                           "dir1",
                           "file.txt"
    );
  }

  @Test
  public void testOrderRecursiveCaseSensitiveFileSystem() throws Exception {
    if (!SystemInfo.isLinux) {
      throw new SkipException("Can only run on case-sensitive file system");
    }

    final SRunningBuild runningBuild = myFixture.startBuild();
    final File artifactsDir = myFixture.finishBuild(runningBuild, false).getArtifactsDirectory();
    artifactsDir.mkdirs();

    createFileOfSize(artifactsDir, "name_a", 5);
    createFileOfSize(artifactsDir, "nAme_A", 5);
    createFileOfSize(artifactsDir, "name_A", 5);
    createFileOfSize(artifactsDir, "name_B1", 0);
    createFileOfSize(artifactsDir, "name_b3", 5);
    File name_b2 = new File(artifactsDir, "name_b2");
    name_b2.mkdir();
    createFileOfSize(name_b2, "aa1", 0);
    createFileOfSize(name_b2, "aa3", 0);
    File name_B2 = new File(artifactsDir, "name_B2");
    name_B2.mkdir();
    createFileOfSize(name_B2, "aa2", 0);
    createFileOfSize(name_B2, "aa4", 0);
    createFileOfSize(artifactsDir, "name_C", 5);
    createFileOfSize(artifactsDir, "name_c", 5);

    checkOrderedCollection(getNames(getArtifacts("", "recursive:true", null, runningBuild.getBuildPromotion())),
                           "name_b2",
                           "name_b2/aa1",
                           "name_b2/aa3",
                           "name_B2",
                           "name_B2/aa2",
                           "name_B2/aa4",
                           "name_a",
                           "name_A",
                           "nAme_A",
                           "name_B1",
                           "name_b3",
                           "name_c",
                           "name_C"
    );
  }

  @Test
  public void testComparator() {
    List<ArtifactTreeElement> result = toArtifactTreeElements(
      "_name_b2",
      "_name_B2",
      "name_B21",
      "name_A",
      "name_a",
      "nAme_A",
      "name_B1",
      "name_b2/aa1",
      "name_b2/aa3",
      "name_B2/aa2",
      "name_B2/aa4",
      "name_b3",
      "name_C",
      "name_c",
      "name_d",
      "name_D"
    );
    result.sort(BuildArtifactsFinder.ARTIFACT_COMPARATOR);

    checkOrderedCollection(getNames(result),
                           "name_b2",
                           "name_b2/aa1",
                           "name_b2/aa3",
                           "name_B2",
                           "name_B2/aa2",
                           "name_B2/aa4",
                           "name_a",
                           "name_A",
                           "nAme_A",
                           "name_B1",
                           "name_b3",
                           "name_B21",
                           "name_c",
                           "name_C",
                           "name_d",
                           "name_D"
    );
  }

  @Test
  public void testComparatorForNumbers() {
    List<ArtifactTreeElement> result = toArtifactTreeElements(
      "name10",
      "name02",
      "name11",
      "name20",
      "name0001",
      "name01",
      "name1",
      "name3",
      "name01b",
      "10",
      "02",
      "02x",
      "002x",
      "02a",
      "10-15",
      "10-014",
      "nam0"
    );
    result.sort(BuildArtifactsFinder.ARTIFACT_COMPARATOR);

    checkOrderedCollection(getNames(result),
                           "002x",
                           "02",
                           "02a",
                           "02x",
                           "10",
                           "10-014",
                           "10-15",
                           "nam0",
                           "name0001",
                           "name01",
                           "name01b",
                           "name1",
                           "name02",
                           "name3",
                           "name10",
                           "name11",
                           "name20"
    );
  }

  @Test
  public void testComparatorSet1() {
    //these data used to throw IllegalArgumentException: Comparison method violates its general contract!
    List<ArtifactTreeElement> result = toArtifactTreeElements(
      "_i/j/j-",
      "i/j/j/19",
      "_i/j/j-",
      "i/j/j/19",
      "_i/j/j-",
      "_i/j/j-",
      "i/j/j/19",
      "_i/jF",
      "_i/js",
      "_i/j/js",
      "_i/js",
      "i/j/j/19",
      "i/j/j/19",
      "_i/j/js",
      "_i/j/ju",
      "i/m/m/19",
      "_i/p/an",
      "_i/p/bu",
      "_i/p/lv",
      "_i/p/pr",
      "_i/p/re",
      "_i/p/re",
      "_i/p/re",
      "_i/p/te",
      "i/p/t/19",
      "i/p/v/19",
      "_i/s/st",
      "i/t/t/19",
      "_i/t/jp",
      "_i/v/hg",
      "_i/x/xm",
      "_i/x/xm"
    );
    result.sort(BuildArtifactsFinder.ARTIFACT_COMPARATOR);
    checkOrderedCollection(getNames(result),
                           "i/j/j/19",
                           "i/j/j/19",
                           "i/j/j/19",
                           "i/j/j/19",
                           "i/j/j/19",
      /*_*/"i/j/j-",
      /*_*/"i/j/j-",
      /*_*/"i/j/j-",
      /*_*/"i/j/j-",
      /*_*/"i/j/js",
      /*_*/"i/j/js",
      /*_*/"i/j/ju",
      /*_*/"i/jF",
      /*_*/"i/js",
      /*_*/"i/js",
                           "i/m/m/19",
      /*_*/"i/p/an",
      /*_*/"i/p/bu",
      /*_*/"i/p/lv",
      /*_*/"i/p/pr",
      /*_*/"i/p/re",
      /*_*/"i/p/re",
      /*_*/"i/p/re",
                           "i/p/t/19",
      /*_*/"i/p/te",
                           "i/p/v/19",
      /*_*/"i/s/st",
      /*_*/"i/t/jp",
                           "i/t/t/19",
      /*_*/"i/v/hg",
      /*_*/"i/x/xm",
      /*_*/"i/x/xm"
    );
  }

  @Test
  public void testComparatorSet2() {
    List<ArtifactTreeElement> result = toArtifactTreeElements(
      "_i/vaa/hhh",
      "i/p/test/10"
    );
    result.sort(BuildArtifactsFinder.ARTIFACT_COMPARATOR);
    checkOrderedCollection(getNames(result),
                           "i/p/test/10",
                           "i/vaa/hhh"
    );
  }

  @TestFor(issues = "TW-66354")
  @Test
  public void testComparatorWhenArtifactNameContainsDigits() {
    final List<ArtifactTreeElement> result = toArtifactTreeElements("abc10vf",
                                                                    "abc9vf",
                                                                    "abc910vf");

    assertGreater(BuildArtifactsFinder.ARTIFACT_COMPARATOR.compare(result.get(0), result.get(1)), 0);
    assertLess(BuildArtifactsFinder.ARTIFACT_COMPARATOR.compare(result.get(0), result.get(2)), 0);
    assertLess(BuildArtifactsFinder.ARTIFACT_COMPARATOR.compare(result.get(1), result.get(2)), 0);

    assertLess(BuildArtifactsFinder.ARTIFACT_COMPARATOR.compare(result.get(1), result.get(0)), 0);
    assertGreater(BuildArtifactsFinder.ARTIFACT_COMPARATOR.compare(result.get(2), result.get(0)), 0);
    assertGreater(BuildArtifactsFinder.ARTIFACT_COMPARATOR.compare(result.get(2), result.get(1)), 0);
  }

  /**
   * if name starts with "_", then underscore is removed and the items is considered a directory
   */
  private List<ArtifactTreeElement> toArtifactTreeElements(final String... names) {
    return Stream.of(names).map(name -> new ArtifactTreeElement() {
      public Long getLastModified() {
        return null;
      }

      public boolean isArchive() {
        return false;
      }

      public boolean isInsideArchive() {
        return false;
      }

      @NotNull
      public String getName() {
        return new File(getFullName()).getName();
      }

      @NotNull
      public String getFullName() {
        return name.startsWith("_") ? name.substring(1) : name;
      }

      public boolean isLeaf() {
        return false;
      }

      public Iterable<Element> getChildren() throws BrowserException {
        return null;
      }

      public boolean isContentAvailable() {
        return !name.startsWith("_");
      }

      @NotNull
      public InputStream getInputStream() throws IllegalStateException, BrowserException {
        return null;
      }


      public long getSize() {
        return 0;
      }

      @NotNull
      public Browser getBrowser() {
        return null;
      }
    }).collect(Collectors.toList());
  }

  private void createFileOfSize(final File dir, String name, int size) throws IOException {
    File result = new File(dir, name);
    FileUtil.writeFile(result, StringUtil.repeat("a", "", size), "US-ASCII");
  }

  @NotNull
  private String getArtifact(final String path) {
    return BuildArtifactsFinder.getArtifactElement(myBuildWithArtifacts.getBuildPromotion(), path, myFixture).getFullName();
  }

  private void assertContainsByFullName(final List<ArtifactTreeElement> artifacts, final String fullName) {
    if (findElement(artifacts, fullName) == null) {
      throw new AssertionFailedError("Collection does not contain element with full name '" + fullName + "'. Collection: " + artifacts);
    }
  }

  @Nullable
  private ArtifactTreeElement findElement(@NotNull final List<ArtifactTreeElement> artifacts, @NotNull final String fullName) {
    return CollectionsUtil.findFirst(artifacts, data -> fullName.equals(data.getFullName()));
  }

  private void assertSize(final int expectedSize, final Collection<?> collection) {
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
