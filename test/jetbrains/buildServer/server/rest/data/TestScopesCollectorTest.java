/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScope;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import org.testng.annotations.Test;

public class TestScopesCollectorTest extends BaseTestScopesCollectorTest {

  @Test
  public void testCanGetPackages() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("package1.class1.aaa", true)
                                          .withTest("package2.class2.bbb", true)
                                          .withTest("package3.class1.ccc", true)
                                          .withTest("package4.class2.ddd", true)
                                          .finish();
    String locator = "scopeType:package,testOccurrences:(build:(id:" + build10.getBuildId() + "))";
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.locator(locator));

    assertEquals(4, result.getEntries().size());

    Set<String> packages = result.getEntries().stream()
                                           .peek(scope -> assertEquals(1, scope.getTestRuns().size()))
                                           .peek(scope -> scope.getName().equals(scope.getPackage()))
                                           .map(scope -> scope.getPackage())
                                           .collect(Collectors.toSet());

    for(int i = 1; i <= 4; i++) {
      assertTrue(packages.contains("package" + i));
    }
  }

  @Test
  public void testCanGetClasses() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("packageA.class1.aaa", true)
                                          .withTest("packageA.class2.bbb", true)
                                          .withTest("packageB.class1.ccc", true)
                                          .withTest("packageB.class2.ddd", true)
                                          .finish();

    String locator = "scopeType:class,testOccurrences:(build:(id:" + build10.getBuildId() + "))";
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.locator(locator));

    assertEquals("Although there are only class1 and class 2, packageA.classX and packageB.classX are expected to be different", 4, result.getEntries().size());

    Set<String> classes = result.getEntries().stream()
                                           .peek(scope -> assertEquals(1, scope.getTestRuns().size()))
                                           .peek(scope -> scope.getName().equals(scope.getClass1()))
                                           .map(scope -> scope.getClass1())
                                           .collect(Collectors.toSet());

    assertContains(classes, "class1", "class2");
  }

  @Test
  public void testCanGetSuites() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildType)
                                          .startSuite("suite1")
                                            .withTest("packageA.class1.aaa", true)
                                            .withTest("packageA.class2.bbb", true)
                                          .endSuite()
                                          .startSuite("suite2")
                                            .withTest("packageB.class1.ccc", true)
                                            .withTest("packageB.class2.ddd", true)
                                          .endSuite()
                                          .finish();

    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator("testOccurrences:(build:(id:" + build10.getBuildId() + ")),scopeType:suite"));
    for(TestScope scope : result.getEntries()) {
      assertEquals(2, scope.getTestRuns().size());
    }

    Set<String> suites = result.getEntries().stream()
                                         .peek(scope -> assertEquals(2, scope.getTestRuns().size()))
                                         .peek(scope -> scope.getName().equals(scope.getSuite()))
                                         .map(scope -> scope.getSuite())
                                         .collect(Collectors.toSet());

    assertEquals(2, suites.size());
    assertContains(suites, "suite1: ", "suite2: ");
  }

  @Test
  public void testCanFilterSuites() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildType)
                                          .startSuite("suite1")
                                          .withTest("packageA.class1.aaa", true)
                                          .withTest("packageA.class2.bbb", true)
                                          .endSuite()
                                          .startSuite("suite2")
                                          .withTest("packageB.class1.ccc", true)
                                          .withTest("packageB.class2.ddd", true)
                                          .endSuite()
                                          .finish();

    // $base64:c3VpdGUxOiA= is a base64 representation of a string 'suite1: '
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(id:" + build10.getBuildId() + ")),scopeType:suite,suite:(value:($base64:c3VpdGUxOiA=),matchType:equals)"
    ));
    assertEquals(1, result.getEntries().size());
    assertEquals("suite1: ", result.getEntries().get(0).getName());
  }

  @Test
  public void testReturnsSuitesComplex() {
    buildTree();
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:suite"
    ));

    assertEquals(3, result.getEntries().size());
    Set<String> resultNames = result.getEntries().stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "suite0: ");
    assertContains(resultNames, "suite1: ");
    assertContains(resultNames, "suite2: ");
  }

  @Test
  public void testReturnsPackagesComplex() {
    buildTree();
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:package"
    ));

    // suite0: packageZ
    // suite1: packageA
    // suite1: packageB
    // suite2: packageC
    // suite2: packageA
    assertEquals(5, result.getEntries().size());
    Set<String> resultNames = result.getEntries().stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "packageA");
    assertContains(resultNames, "packageB");
    assertContains(resultNames, "packageC");
    assertContains(resultNames, "packageZ");
  }

  @Test
  public void testReturnsClassesComplex() {
    buildTree();
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:class"
    ));

    // suite0: packageZ.classZ
    // suite1: packageA.class1
    // suite1: packageA.class2
    // suite1: packageB.class1
    // suite2: packageC.class2
    // suite2: packageA.class3
    assertEquals(6, result.getEntries().size());
    Set<String> resultNames = result.getEntries().stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "class1");
    assertContains(resultNames, "class2");
    assertContains(resultNames, "class3");
    assertContains(resultNames, "classZ");
  }

  @Test
  public void testFiltersByBuildType2() {
    buildTree();
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:class,buildType:(name:buildconf1)"
    ));

    // suite0: packageZ.classZ
    // suite1: packageA.class1
    // suite1: packageA.class2
    // suite1: packageB.class1
    // suite2: packageC.class2
    assertEquals(5, result.getEntries().size());
    Set<String> resultNames = result.getEntries().stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "class1");
    assertContains(resultNames, "class2");
  }

  @Test
  public void testFiltersByAffectedProject() {
    buildTree();
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:class,buildType:(affectedProject:(id:project2))"
    ));

    // suite0: packageZ.classZ
    // suite1: packageB.class1
    // suite2: packageC.class2
    assertEquals(3, result.getEntries().size());
    Set<String> resultNames = result.getEntries().stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "class1");
    assertContains(resultNames, "class2");
    assertContains(resultNames, "classZ");
  }

  @Test
  public void testFiltersByAffectedProject2() {
    buildTree();
    PagedSearchResult<TestScope> result = myTestScopesCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:class,buildType:(affectedProject:(id:subproject11))"
    ));

    // suite2: packageA.class3
    assertEquals(1, result.getEntries().size());
    Set<String> resultNames = result.getEntries().stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "class3");
  }

}
