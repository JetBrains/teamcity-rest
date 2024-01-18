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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.log.LogInitializer;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.util.ItemFilterUtil;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.serverSide.impl.*;
import jetbrains.buildServer.serverSide.impl.projects.ProjectImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.StandardProperties;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.OperationRequestor;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2015
 */
public class BuildPromotionFinderTest extends BaseFinderTest<BuildPromotion> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setFinder(myBuildPromotionFinder);
  }


  @Test
  public void testBasic2() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final RunningBuildEx runningBuild = build().in(buildConf).run();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuilds("id:" + runningBuild.getBuildId(), runningBuild.getBuildPromotion());
    checkBuilds("id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("id:" + runningBuild.getBuildId() + ",running:true", runningBuild.getBuildPromotion());
    checkBuilds("id:" + runningBuild.getBuildId() + ",running:false");
  }

  @Test
  public void testSateFiltering() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final RunningBuildEx runningBuild = build().in(buildConf).run();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuilds(null, build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("running:true", runningBuild.getBuildPromotion());
    checkBuilds("running:false", build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("running:any", runningBuild.getBuildPromotion(), build2.getBuildPromotion(), build1.getBuildPromotion());

    checkBuilds("state:any", queuedBuild.getBuildPromotion(), runningBuild.getBuildPromotion(), build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("state:queued", queuedBuild.getBuildPromotion());
    checkBuilds("state:running", runningBuild.getBuildPromotion());
    checkBuilds("state:finished", build2.getBuildPromotion(), build1.getBuildPromotion());

    checkBuilds("state:(queued:true,running:true,finished:true)", queuedBuild.getBuildPromotion(), runningBuild.getBuildPromotion(), build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("state:(queued:true)", queuedBuild.getBuildPromotion());
    checkBuilds("state:(queued:true,running:false)", queuedBuild.getBuildPromotion());
    checkBuilds("state:(running:true)", runningBuild.getBuildPromotion());
    checkBuilds("state:(finished:true)",build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("state:(queued:true,running:true,finished:false)", queuedBuild.getBuildPromotion(), runningBuild.getBuildPromotion());
  }

  @Test
  public void testSnapshotDependencies() throws Exception {
    final BuildTypeImpl buildConf0 = registerBuildType("buildConf0", "project");
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    final BuildTypeImpl buildConf3 = registerBuildType("buildConf3", "project");
    final BuildTypeImpl buildConf4 = registerBuildType("buildConf4", "project");
    addDependency(buildConf4, buildConf3);
    addDependency(buildConf3, buildConf2);
    addDependency(buildConf2, buildConf1);
    addDependency(buildConf1, buildConf0);
    final BuildPromotion build4 = build().in(buildConf4).addToQueue().getBuildPromotion();
    final BuildPromotion build3 = build4.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build2 = build3.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build1 = build2.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build0 = build1.getDependencies().iterator().next().getDependOn();
    finishBuild(BuildBuilder.run(build1.getQueuedBuild(), myFixture), false);
    BuildBuilder.run(build2.getQueuedBuild(), myFixture);

    final String baseToLocatorStart = "snapshotDependency:(to:(id:" + build4.getId() + ")";
    checkBuilds(baseToLocatorStart + ")", build1, build0); //by default only finished builds
    checkBuilds(baseToLocatorStart + "),state:any", build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + "),state:running", build2);
    checkBuilds(baseToLocatorStart + "),state:queued", build3);
    checkBuilds(baseToLocatorStart + "),state:(queued:true)", build3);
    checkBuilds(baseToLocatorStart + "),state:(running:true,queued:true)", build3, build2);

    checkBuilds(baseToLocatorStart + ",includeInitial:true)", build1, build0); //by default only finished builds
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:any", build4, build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:running", build2);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:queued", build4, build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:(queued:true)", build4, build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:(running:true,queued:true)", build4, build3, build2);

    checkBuilds(baseToLocatorStart + ",includeInitial:false)", build1, build0);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:any", build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:running", build2);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:queued", build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:(queued:true)", build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:(running:true,queued:true)", build3, build2);

    checkBuilds(baseToLocatorStart + ",recursive:true)", build1, build0);
    checkBuilds(baseToLocatorStart + ",recursive:true),state:any", build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + ",recursive:false)");
    checkBuilds(baseToLocatorStart + ",recursive:false),state:any", build3);

    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + "))", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + ")),defaultFilter:false", build2, build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + ")),state:any", build2, build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + "),includeInitial:true),state:any", build3, build2, build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build2.getId() + "))", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build2.getId() + "),recursive:false)", build1);
    checkBuilds("snapshotDependency:(to:(id:" + build2.getId() + ")),state:any", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build1.getId() + "))", build0);
    checkBuilds("snapshotDependency:(to:(id:" + build1.getId() + ")),state:any", build0);
    checkBuilds("snapshotDependency:(to:(id:" + build1.getId() + "),includeInitial:true),state:any", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build0.getId() + "),includeInitial:true)", build0);
    checkBuilds("snapshotDependency:(to:(id:" + build0.getId() + ")),state:any");

    final String baseFromLocatorStart = "snapshotDependency:(from:(id:" + build0.getId() + ")";
    checkBuilds(baseFromLocatorStart + ")", build1); //by default only finished builds
    checkBuilds(baseFromLocatorStart + "),state:any", build4, build3, build2, build1);
    checkBuilds(baseFromLocatorStart + ",includeInitial:true),state:any", build4, build3, build2, build1, build0);
    checkBuilds(baseFromLocatorStart + ",includeInitial:true,recursive:false),state:any", build1, build0);
    checkBuilds(baseFromLocatorStart + "),state:running", build2);
    checkBuilds(baseFromLocatorStart + "),state:queued", build4, build3);
    checkBuilds(baseFromLocatorStart + "),state:(running:true,queued:true)", build4, build3, build2);
    checkBuilds(baseFromLocatorStart + ",includeInitial:true),state:(running:true,queued:true)", build4, build3, build2);

    checkBuilds("snapshotDependency:(from:(id:" + build1.getId() + "),to:(id:" + build3.getId() + "),includeInitial:true),state:any", build3, build2, build1);
    checkBuilds("snapshotDependency:(from:(id:" + build1.getId() + "),to:(id:" + build3.getId() + ")),state:any", build2);

    checkBuilds("snapshotDependency:(to:(id:" + (build4.getId() + 10) + ")),state:any");
  }

  @Test
  public void testSnapshotDependenciesAndBranches() throws Exception {
    final BuildTypeImpl buildConf0 = registerBuildType("buildConf0", "project");
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    addDependency(buildConf2, buildConf1);
    addDependency(buildConf1, buildConf0);

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    final SVcsRoot vcsRoot = buildConf0.getProject().createVcsRoot("vcs", "extId", "name");

    buildConf0.addVcsRoot(vcsRoot);
    buildConf1.addVcsRoot(vcsRoot);
    buildConf2.addVcsRoot(vcsRoot);

    final VcsRootInstance vcsRootInstance = buildConf0.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");

    final BuildPromotion build20 = build().in(buildConf2).finish().getBuildPromotion();
    final BuildPromotion build10 = build20.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build00 = build10.getDependencies().iterator().next().getDependOn();

    final BuildPromotion build2_20 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build2_10 = build2_20.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build2_00 = build2_10.getDependencies().iterator().next().getDependOn();

    checkBuilds("snapshotDependency:(to:(id:" + build10.getId() + "))", build00);
    checkBuilds("snapshotDependency:(to:(id:" + build2_10.getId() + "))", build2_00);

    final BuildPromotion build3_20 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();
    checkBuilds("snapshotDependency:(from:(id:" + build2_00.getId() + ")),equivalent:(id:" + build3_20.getId() + ")", build2_20);
  }

  @Test
  public void testArtifactDependencies() throws Exception {
    final BuildTypeImpl buildConf0 = registerBuildType("buildConf0", "project");
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    final BuildTypeImpl buildConf3 = registerBuildType("buildConf3", "project");
    final BuildTypeImpl buildConf4 = registerBuildType("buildConf4", "project");
    addArtifactDependency(buildConf4, buildConf3);
    addArtifactDependency(buildConf3, buildConf2);
    addArtifactDependency(buildConf2, buildConf1);
    addArtifactDependency(buildConf1, buildConf0);

    DownloadedArtifactsLoggerImpl artifactsLogger = myFixture.getSingletonService(DownloadedArtifactsLoggerImpl.class);

    final BuildPromotion build0 = build().in(buildConf0).finish().getBuildPromotion();
    addArtifact(build0, ARTIFACT_DEP_FILE_NAME);
    final BuildPromotion build1 = build().in(buildConf1).finish().getBuildPromotion();
    artifactsLogger.logArtifactDownload(build1.getAssociatedBuildId(), build0.getAssociatedBuildId(), ARTIFACT_DEP_FILE_NAME);
    addArtifact(build1, ARTIFACT_DEP_FILE_NAME);
    final BuildPromotion build2 = build().in(buildConf2).finish().getBuildPromotion();
    artifactsLogger.logArtifactDownload(build2.getAssociatedBuildId(), build1.getAssociatedBuildId(), ARTIFACT_DEP_FILE_NAME);
    addArtifact(build2, ARTIFACT_DEP_FILE_NAME);
    final BuildPromotion build3 = build().in(buildConf3).run().getBuildPromotion();
    artifactsLogger.logArtifactDownload(build3.getAssociatedBuildId(), build2.getAssociatedBuildId(), ARTIFACT_DEP_FILE_NAME);
    addArtifact(build3, ARTIFACT_DEP_FILE_NAME);
    final BuildPromotion build4 = build().in(buildConf4).addToQueue().getBuildPromotion();

    artifactsLogger.waitForQueuePersisting();

    final String baseToLocatorStart = "artifactDependency:(to:(id:" + build2.getId() + ")";
    checkBuilds(baseToLocatorStart + ")", build1, build0);
    checkBuilds(baseToLocatorStart + "),state:any", build1, build0);
    checkBuilds(baseToLocatorStart + ",includeInitial:true)", build2, build1, build0); //by default only finished builds
    checkBuilds(baseToLocatorStart + ",recursive:false)", build1);

    checkBuilds("artifactDependency:(to:(id:" + build3.getId() + "))", build2, build1, build0);

    final String baseFromLocatorStart = "artifactDependency:(from:(id:" + build1.getId() + ")";
    checkBuilds(baseFromLocatorStart + ")", build2);
    checkBuilds(baseFromLocatorStart + "),state:any", build3, build2);
  }

  private void addArtifact(final BuildPromotion build, final String artifactName) throws IOException {
    final File artifactsDir = build.getArtifactsDirectory();
    artifactsDir.mkdirs();
    File myFile1 = new File(artifactsDir, artifactName);
    myFile1.createNewFile();
  }

  @Test
  public void testMultipleBuildTypes() throws Exception {
    myFixture.createEnabledAgent("Ant");// adding one more agent to allow parallel builds

    final BuildTypeImpl buildConf0 = registerBuildType("buildConf0", "project");
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project1");

    final BuildPromotion build05 = build().in(buildConf0).number("10").finish().getBuildPromotion();
    final BuildPromotion build10 = build().in(buildConf1).number("10").finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf0).finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf0).number("10").finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf2).finish().getBuildPromotion();

    final BuildPromotion build50 = build().in(buildConf0).number("10").run().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf2).run().getBuildPromotion();
    final BuildPromotion build70 = build().in(buildConf0).addToQueue().getBuildPromotion();
    final BuildPromotion build80 = build().in(buildConf1).addToQueue().getBuildPromotion();

    check(null, build40, build30, build20, build10, build05);
    check("buildType:(id:" + buildConf0.getExternalId() + ")", build30, build20, build05);
    check("buildType:(project:(name:" + "project" + "))", build30, build20, build10, build05);
    check("buildType:(project:(name:" + "project" + ")),number:10", build30, build10, build05);
    check("buildType:(item:(id:" + buildConf1.getExternalId() + "),item:(id:(" + buildConf0.getExternalId() + ")))", build30, build20, build10, build05);

    check("defaultFilter:false", build70, build80, build60, build50, build40, build30, build20, build10, build05);
    check("buildType:(id:" + buildConf0.getExternalId() + "),defaultFilter:false", build70, build50, build30, build20, build05);
    check("buildType:(id:" + buildConf1.getExternalId() + "),defaultFilter:false", build80, build10);
    check("buildType:(id:" + buildConf2.getExternalId() + "),defaultFilter:false", build60, build40);
    check("buildType:(project:(name:" + "project" + ")),defaultFilter:false", build70, build80, build50, build30, build20, build10, build05);
    check("buildType:(project:(name:" + "project" + ")),number:10,defaultFilter:false", build50, build30, build10, build05);
    check("buildType:(item:(id:" + buildConf1.getExternalId() + "),item:(id:(" + buildConf0.getExternalId() + "))),defaultFilter:false",
          build70, build80, build50, build30, build20, build10, build05);
    check("buildType:(item:(id:" + buildConf0.getExternalId() + "),item:(id:(" + buildConf1.getExternalId() + "))),defaultFilter:false",
          build70, build80, build50, build30, build20, build10, build05);
  }

  @Test
  public void testMultipleBuildTypesInProjects() throws Exception {
    myFixture.createEnabledAgent("Ant");// adding one more agent to allow parallel builds

    ProjectEx project1 = getRootProject().createProject("project1", "project1");
    ProjectEx project11 = project1.createProject("project1_1", "project1_1");
    ProjectEx project2 = getRootProject().createProject("project2", "project2");

    final BuildTypeEx buildConf0 = project1.createBuildType("buildConf");
    final BuildTypeEx buildConf1 = project11.createBuildType("buildConf");
    final BuildTypeEx buildConf2 = project2.createBuildType("buildConf2");

    final BuildPromotion build05 = build().in(buildConf0).number("10").finish().getBuildPromotion();
    final BuildPromotion build10 = build().in(buildConf1).number("10").finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf0).finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf0).number("10").finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf2).finish().getBuildPromotion();

    final BuildPromotion build50 = build().in(buildConf0).number("10").run().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf2).run().getBuildPromotion();
    final BuildPromotion build70 = build().in(buildConf0).addToQueue().getBuildPromotion();
    final BuildPromotion build80 = build().in(buildConf1).addToQueue().getBuildPromotion();

    check("defaultFilter:false", build70, build80, build60, build50, build40, build30, build20, build10, build05);
    String df = ",defaultFilter:false";
    check("buildType:(id:" + buildConf0.getExternalId() + ")" + df, build70, build50, build30, build20, build05);
    check("buildType:(id:" + buildConf0.getExternalId() + "),affectedProject:(id:" + project1.getExternalId() + ")" + df, build70, build50, build30, build20, build05);
    check("buildType:(id:" + buildConf0.getExternalId() + "),project:(id:" + project1.getExternalId() + ")" + df, build70, build50, build30, build20, build05);

    check("buildType:(affectedProject:(id:" + project1.getExternalId() + "))" + df, build70, build80, build50, build30, build20, build10, build05);
    check("buildType:(project:(id:" + project1.getExternalId() + "))" + df, build70, build50, build30, build20, build05);

    check("buildType:(id:" + buildConf1.getExternalId() + "),affectedProject:(id:" + project1.getExternalId() + ")" + df, build80, build10);
    checkExceptionOnBuildsSearch(NotFoundException.class, "buildType:(id:" + buildConf1.getExternalId() + "),project:(id:" + project1.getExternalId() + ")");

    check("buildType:(name:" + "buildConf" + "),project:(id:" + project1.getExternalId() + ")" + df, build70, build50, build30, build20, build05);
    check("buildType:(name:" + "buildConf" + "),project:(id:" + project11.getExternalId() + ")" + df, build80, build10);
    check("buildType:(name:" + "buildConf" + "),affectedProject:(id:" + project1.getExternalId() + ")" + df, build70, build80, build50, build30, build20, build10, build05);

    check("buildType:(affectedProject:(id:" + project1.getExternalId() + ")),number:10" + df, build50, build30, build10, build05);
    check("buildType:(item:(id:" + buildConf1.getExternalId() + "),item:(id:(" + buildConf0.getExternalId() + ")))" + df,
          build70, build80, build50, build30, build20, build10, build05);
  }

  @Test
  public void testQueuedBuildFinding() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuilds(String.valueOf(queuedBuild.getItemId()), queuedBuild.getBuildPromotion());
    checkBuilds("id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("taskId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("promotionId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),promotionId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
  }

  @Test
  public void testReplacementBuildFindingByMergedPromotion() throws Exception { //see also jetbrains.buildServer.server.rest.data.finder.impl.BuildFinderTest.testQueuedBuildByMergedPromotion()
    final BuildTypeImpl buildConf = registerBuildType("buildConf", "project");
    myFixture.registerVcsSupport("vcsSuport");
    buildConf.addVcsRoot(buildConf.getProject().createVcsRoot("vcsSuport", "extId", "name"));
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    buildConf2.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(buildConf));

    final SQueuedBuild queuedBuild1 = build().in(buildConf).addToQueue();
    final long id1 = queuedBuild1.getBuildPromotion().getId();

    final SQueuedBuild queuedBuild2 = build().in(buildConf2).addToQueue();
    final BuildPromotion queuedDependency = queuedBuild2.getBuildPromotion().getDependencies().iterator().next().getDependOn();
    final long id2 = queuedDependency.getId();

    assertEquals(3, myFixture.getBuildQueue().getNumberOfItems());
    assertNotSame(id1, id2);

    assertTrue(((BuildPromotionEx)queuedBuild1.getBuildPromotion()).getTopDependencyGraph().collectChangesForGraph(new CancelableTaskHolder()));
    assertTrue(((BuildPromotionEx)queuedBuild2.getBuildPromotion()).getTopDependencyGraph().collectChangesForGraph(new CancelableTaskHolder()));

    myFixture.getBuildQueue().setMergeBuildsInQueue(true);
    myFixture.getBuildQueue().mergeBuilds(null);

    assertEquals(2, myFixture.getBuildQueue().getNumberOfItems());


    checkBuilds("id:" + id2, queuedDependency);
    checkBuilds("id:" + id1, queuedDependency);
    checkBuilds(String.valueOf(id1), queuedDependency);
    checkBuilds("taskId:" + id1, queuedDependency);
  }

  @Test
  public void testEquivalentBuildFinding() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).withBranch("branch1").finish();
    final SFinishedBuild build3 = build().in(buildConf).withBranch("branch1").finish();
    final SFinishedBuild build4 = build().in(buildConf).withBranch("branch2").finish();
    final SFinishedBuild build5 = build().in(buildConf).withBranch("branch1").finish();

    checkBuilds("equivalent:(id:" + build0.getBuildId() + ")", build1.getBuildPromotion());
    checkBuilds("equivalent:(id:" + build2.getBuildId() + ")", build5.getBuildPromotion(), build3.getBuildPromotion(), build1.getBuildPromotion(), build0.getBuildPromotion());
    checkBuilds("equivalent:(id:" + build4.getBuildId() + ")", build1.getBuildPromotion(), build0.getBuildPromotion());
  }


  @Test
  public void testFailedToStart() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf).failed().finish().getBuildPromotion();
    BuildPromotion build40 = build().in(buildConf).cancel(getOrCreateUser("user")).getBuildPromotion();
    BuildPromotion build50 = build().in(buildConf).failedToStart().cancel(getOrCreateUser("user")).getBuildPromotion();

    checkBuilds(null, build30, build10);
    checkBuilds("status:SUCCESS", build10);
    checkBuilds("status:FAILURE", build30);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + ")", build30, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),state:any", build30, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),canceled:any,state:any", build50, build40, build30, build10); //TeamCity API: build50 should not be here

    checkBuilds("failedToStart:true", build20);
    checkBuilds("failedToStart:any", build30, build20, build10);
    checkBuilds("failedToStart:any,canceled:true", build50, build40);
//    checkBuilds("failedToStart:true,canceled:true", build50);   //TeamCity API: failed to start builds are reported to be not failed to start if canceled
    checkBuilds("status:SUCCESS,failedToStart:any", build10);
    checkBuilds("status:FAILURE,failedToStart:any", build30, build20);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),failedToStart:any", build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),failedToStart:true", build20);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),state:any,failedToStart:any", build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),canceled:any,state:any,failedToStart:any", build50, build40, build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),canceled:false,failedToStart:any", build30, build20, build10);
  }


  @Test
  @TestFor(issues = {"TW-21926"})
  public void testMultipleBuildsWithIdLocator() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final BuildPromotion build2 = build().in(buildConf).failed().finish().getBuildPromotion();

    final RunningBuildEx running3 = startBuild(myBuildType);
    running3.stop(createUser("uuser1"), "cancel comment");
    SBuild b3canceled = finishBuild(running3, true);

    final BuildPromotion build4 = build().in(buildConf2).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build5 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();

    final BuildPromotion runningBuild5 = build().in(buildConf).run().getBuildPromotion();
    final BuildPromotion queuedBuild = build().in(buildConf).addToQueue().getBuildPromotion();

    checkBuilds("taskId:" + queuedBuild.getId(), queuedBuild);
    checkBuilds("promotionId:" + queuedBuild.getId(), queuedBuild);
    checkBuilds("id:" + runningBuild5.getId(), runningBuild5);
    checkBuilds("id:" + build2.getId(), build2);
    checkBuilds("id:" + b3canceled.getBuildId(), b3canceled.getBuildPromotion());
    checkBuilds("id:" + build4.getId(), build4);
    checkBuilds("id:" + build5.getId(), build5);
  }

  @Test
  public void testSinceUntil() {
    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild build10 = build().in(buildConf1).finish();
    time.jumpTo(10);
    final Date afterBuild10 = time.getNow();
    time.jumpTo(10);
    final SFinishedBuild build20 = build().in(buildConf2).failed().finish();
    time.jumpTo(10);

    final SFinishedBuild build25Deleted = build().in(buildConf2).failed().finish();
    final long build25DeletedId = build25Deleted.getBuildId();
    myFixture.getSingletonService(BuildHistory.class).removeEntry(build25Deleted);

    final SFinishedBuild build30 = build().in(buildConf2).failedToStart().finish();
    time.jumpTo(10);
    final Date afterBuild30 = time.getNow();
    time.jumpTo(10);

    final SFinishedBuild build40 = build().in(buildConf1).finish();
    time.jumpTo(10);

    final SFinishedBuild build50Deleted = build().in(buildConf2).failed().finish();
    final long build50DeletedId = build50Deleted.getBuildId();
    myFixture.getSingletonService(BuildHistory.class).removeEntry(build50Deleted);

    final SFinishedBuild build60 = build().in(buildConf2).finish();
    time.jumpTo(10);
    final Date afterBuild60 = time.getNow();

    final SFinishedBuild build70 = build().in(buildConf1).finish();

    time.jumpTo(10);
    final SRunningBuild build80 = build().in(buildConf1).run();
    time.jumpTo(10);
    final SQueuedBuild build90 = build().in(buildConf1).addToQueue();

    checkBuilds("defaultFilter:false", 8, getBuildPromotions(build90, build80, build70, build60, build40, build30, build20, build10));
    checkBuilds("state:any", 7, getBuildPromotions(build90, build80, build70, build60, build40, build20, build10));
    checkBuilds("defaultFilter:false,buildType:(id:" + buildConf1.getExternalId() + ")", 5, getBuildPromotions(build90, build80, build70, build40, build10));

    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),state:any", 7, getBuildPromotions(build90, build80, build70, build60, build40, build20));
    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),state:any,failedToStart:any", 8, getBuildPromotions(build90, build80, build70, build60, build40, build30, build20));
    checkBuilds("sinceBuild:(id:" + build30.getBuildId() + "),state:any", 6, getBuildPromotions(build90, build80, build70, build60, build40));
    checkBuilds("sinceBuild:(id:" + build30.getBuildId() + "),state:any,failedToStart:any", 6, getBuildPromotions(build90, build80, build70, build60, build40));
    checkBuilds("sinceBuild:(id:" + build60.getBuildId() + "),state:any", 4, getBuildPromotions(build90, build80, build70));

    if (build60.getStartDate().getTime() % 1000 == 0) { //documenting current behavior for the legacy "sinceDate": it is not quite consistent when the time has 0 ms
      checkBuilds("sinceDate:(" + Util.formatTime(build60.getStartDate()) + "),state:any", 5, getBuildPromotions(build80, build70));
      checkBuilds("sinceBuild:(id:" + build30.getBuildId() + "),sinceDate:(" + Util.formatTime(build60.getStartDate()) + "),state:any", 5, getBuildPromotions(build80, build70));
    } else {
      checkBuilds("sinceDate:(" + Util.formatTime(build60.getStartDate()) + "),state:any", 5, getBuildPromotions(build80, build70, build60));
      checkBuilds("sinceBuild:(id:" + build30.getBuildId() + "),sinceDate:(" + Util.formatTime(build60.getStartDate()) + "),state:any", 5, getBuildPromotions(build80, build70, build60));
    }


    checkExceptionOnBuildsSearch(LocatorProcessException.class, "sinceBuild:(xxx)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "sinceBuild:(buildType:(" + buildConf1.getId() + "),status:FAILURE)");

    //documenting current behavior: extra ")" does not produce an error
    checkBuilds("sinceBuild:(id:" + build25DeletedId + "),state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40, build30));
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),state:finished,defaultFilter:false", getBuildPromotions(build60, build40, build30, build20, build10));
    checkBuilds("untilBuild:(id:" + build50DeletedId + "),state:any,defaultFilter:false", getBuildPromotions(build40, build30, build20, build10));

    if (build20.getStartDate().getTime() % 1000 == 0) { //documenting current behavior for the legacy "sinceDate": it is not quite consistent when the time has 0 ms
      checkBuilds("sinceDate:" + Util.formatTime(build20.getStartDate()) + ",state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40, build30));
      checkBuilds("sinceDate:" + Util.formatTime(build20.getStartDate()) + "),state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40, build30));
    } else {
      checkBuilds("sinceDate:" + Util.formatTime(build20.getStartDate()) + ",state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40, build30, build20));
      checkBuilds("sinceDate:" + Util.formatTime(build20.getStartDate()) + "),state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40, build30, build20));
    }
    checkBuilds("sinceDate:" + Util.formatTime(afterBuild30) + "),state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40));

    if (build60.getStartDate().getTime() % 1000 == 0) { //documenting current behavior for the legacy "sinceDate": it is not quite consistent when the time has 0 ms
      checkBuilds("untilDate:" + Util.formatTime(build60.getStartDate()) + "),state:finished,defaultFilter:false", getBuildPromotions(build60, build40, build30, build20, build10));
    } else {
      checkBuilds("untilDate:" + Util.formatTime(build60.getStartDate()) + "),state:finished,defaultFilter:false", getBuildPromotions(build40, build30, build20, build10));
    }
    checkBuilds("untilDate:" + Util.formatTime(afterBuild30) + "),state:finished,defaultFilter:false", getBuildPromotions(build30, build20, build10));

    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),sinceDate:" + Util.formatTime(build10.getStartDate()) + ",state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40, build30, build20));
    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),sinceDate:" + Util.formatTime(afterBuild30) + ",state:finished,defaultFilter:false", getBuildPromotions(build70, build60, build40));
    if (build60.getStartDate().getTime() % 1000 == 0) { //documenting current behavior for the legacy "sinceDate": it is not quite consistent when the time has 0 ms
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + Util.formatTime(build60.getStartDate()) + ",state:finished,defaultFilter:false", getBuildPromotions(build60, build40, build30, build20, build10));
    } else {
      checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + Util.formatTime(build60.getStartDate()) + ",state:finished,defaultFilter:false", getBuildPromotions(build40, build30, build20, build10));
    }
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + Util.formatTime(afterBuild30) + ",state:finished,defaultFilter:false", getBuildPromotions(build30, build20, build10));

    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilBuild:" + build60.getBuildId()+ ",state:finished,defaultFilter:false", getBuildPromotions(build60, build40, build30));
    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilDate:" + Util.formatTime(afterBuild30) + ",state:finished,defaultFilter:false", getBuildPromotions(build30));
    checkBuilds("sinceDate:(" + Util.formatTime(afterBuild10) + "),untilDate:" + Util.formatTime(afterBuild30) + ",state:finished,defaultFilter:false", getBuildPromotions(build30, build20));
  }

  @org.testng.annotations.DataProvider(name = "allBooleans")
  public static Object[][] allBooleans() {
     return new Object[][] {{true}, {false}};
  }

  @Test(dataProvider = "allBooleans")
  public void testTimes(boolean timeAsSecondBoundary) {
    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);
    initFinders(); //recreate finders to let time service sink in
    if (timeAsSecondBoundary) {
      // set the time to the next whole second
      time.jumpTo(1000L - time.now() % 1000);
    } else if (time.now() % 1000 == 0){
      // make the time not at the whole second if it is
      time.jumpTo(10L);
    }
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SQueuedBuild bQueuedAt_0ms = build().in(buildConf1).addToQueue();

    // Jump 1 s just to have two builds to be queued at different times
    time.jumpTo(1);
    final SQueuedBuild bQueuedAt_1ms = build().in(buildConf2).addToQueue();

    // Jump to 10s from starting time, account for already jumped 1s
    time.jumpTo(9);
    final SFinishedBuild bFinishedAt_10_000ms = myFixture.finishBuild(BuildBuilder.run(bQueuedAt_0ms, myFixture), false);
    time.jumpTo(20);

    // Set time to the next whole second + 100ms, thus ensuring next 100 ms jump will be within the same second
    time.jumpTo(1000L - time.now() % 1000 + 100);
    final SFinishedBuild bFinishedAt_31_100ms = myFixture.finishBuild(BuildBuilder.run(bQueuedAt_1ms, myFixture), false);

    time.jumpTo(100L);
    final SFinishedBuild bFinishedAt_31_200ms = build().in(buildConf2).finish();

    time.jumpTo(10);
    final SFinishedBuild bDeletedAt_41_200ms = build().in(buildConf2).failed().finish();
    myFixture.getSingletonService(BuildHistory.class).removeEntry(bDeletedAt_41_200ms);

    final BuildBuilder bQueuedAt_41_200ms = build().in(buildConf2);
    time.jumpTo(10);
    final SFinishedBuild bFailedToStartAndFinishedAt_51_200ms = bQueuedAt_41_200ms.failedToStart().finish();
    time.jumpTo(10);
    final Date time_61_200ms = time.getNow();
    time.jumpTo(10);

    final SFinishedBuild bFinishedAt_71_200ms = build().in(buildConf1).finish();
    time.jumpTo(10);

    final SFinishedBuild bDeletedAt_81_200ms = build().in(buildConf2).failed().finish();
    myFixture.getSingletonService(BuildHistory.class).removeEntry(bDeletedAt_81_200ms);

    final SFinishedBuild bFinishedAt_81_200ms = build().in(buildConf2).finish();
    time.jumpTo(10);

    final SFinishedBuild bFinishedAt_91_200ms = build().in(buildConf1).finish();

    time.jumpTo(10);
    final SRunningBuild bStartedAt_101_200ms = build().in(buildConf1).run();

    time.jumpTo(10);
    final SQueuedBuild bQueuedAt_111_200ms = build().in(buildConf1).addToQueue();
    time.jumpTo(10);
    final SQueuedBuild bQueuedAt_121_200ms = build().in(buildConf1).parameter("a", "x").addToQueue(); //preventing build from merging in the queue
    time.jumpTo(10);
    final SQueuedBuild bQueuedAt_131_200ms_and_moved_to_top = build().in(buildConf1).parameter("a", "y").addToQueue(); //preventing build from merging in the queue
    //noinspection ConstantConditions
    myFixture.getBuildQueue().moveTop(bQueuedAt_131_200ms_and_moved_to_top.getItemId());

    checkBuilds(
      // Should return 'normal' finished builds after given one
      "finishDate:(build:(id:" + bFinishedAt_10_000ms.getBuildId() + "),condition:after),state:any",
      getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms)
    );
    checkBuilds(
      // Should return failed to start builds in addition to all 'normal' finished builds
      "finishDate:(build:(id:" + bFinishedAt_10_000ms.getBuildId() + "),condition:after),state:any,failedToStart:any",
      getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFailedToStartAndFinishedAt_51_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms)
    );
    checkBuilds(
      // Should return 'normal' finished builds, check for time precision, closest returned build is within the same second
      "finishDate:(build:(id:" + bFinishedAt_31_100ms.getBuildId() + "),condition:after),state:any",
      getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms)
    );
    checkBuilds(
      // Should return 'normal' finished builds including the given one
      "finishDate:(build:(id:" + bFinishedAt_31_100ms.getBuildId() + "),condition:after,includeInitial:true),state:any",
      getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms)
    );
    checkBuilds(
      // Should return 'normal' finished before the given one
      "finishDate:(build:(id:" + bFinishedAt_31_100ms.getBuildId() + "),condition:before),state:any",
      getBuildPromotions(bFinishedAt_10_000ms)
    );
    checkBuilds(
      // Should return given build only
      "finishDate:(build:(id:" + bFinishedAt_31_100ms.getBuildId() + "),condition:equals),state:any",
      getBuildPromotions(bFinishedAt_31_100ms)
    );
    checkBuilds(
      // Should return all 'normal' builds within the same minute as the given one
      "finishDate:(build:(id:" + bFinishedAt_31_100ms.getBuildId() + ")),state:any",
      getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms)
    );
    checkExceptionOnBuildsSearch(
      // Build at given date is not finished and there are no other finished builds inside the same second
      BadRequestException.class,
      "finishDate:(build:(id:" + bQueuedAt_111_200ms.getItemId() + "))"
    );

    if (bFinishedAt_10_000ms.getFinishDate().getTime() % 1000 == 0) { //the comparison is strict "after", but the time in locator is with seconds precision, so times without ms part are special case
      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after,includeInitial:true),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));

      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after),state:any,failedToStart:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFailedToStartAndFinishedAt_51_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after,includeInitial:true),state:any,failedToStart:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFailedToStartAndFinishedAt_51_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));
    } else {
      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));
      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after,includeInitial:true),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));

      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after),state:any,failedToStart:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFailedToStartAndFinishedAt_51_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));
      checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after,includeInitial:true),state:any,failedToStart:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFailedToStartAndFinishedAt_51_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));
    }
    checkBuilds("finishDate:(date:" + getTimeWithMs(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("finishDate:(date:" + getTimeWithMs(bFinishedAt_10_000ms.getFinishDate()) + ",condition:after),state:any,failedToStart:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFailedToStartAndFinishedAt_51_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_31_100ms.getFinishDate()) + ",condition:after),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("finishDate:(date:" + getTimeWithMs(bFinishedAt_31_100ms.getFinishDate()) + ",condition:after),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms));
    checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_31_100ms.getFinishDate()) + ",condition:after,includeInitial:true),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_31_100ms.getFinishDate()) + ",condition:before),state:any", getBuildPromotions(bFinishedAt_10_000ms));
    checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_31_100ms.getFinishDate()) + ",condition:equals),state:any", getBuildPromotions());
    checkBuilds("finishDate:(date:" + getTimeWithMs(bFinishedAt_31_100ms.getFinishDate()) + ",condition:equals),state:any", getBuildPromotions(bFinishedAt_31_100ms));
    checkBuilds("finishDate:(date:" + Util.formatTime(bFinishedAt_31_100ms.getFinishDate()) + "),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("finishDate:(date:" + getTimeWithMs(bFinishedAt_31_100ms.getFinishDate()) + "),state:any", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms));

    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSSZ", Locale.ENGLISH)).format(bFinishedAt_31_100ms.getFinishDate()) + "),state:any",
                getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms));
    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSSX", Locale.ENGLISH)).format(bFinishedAt_31_100ms.getFinishDate()) + "),state:any",
                getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms));
    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)).format(bFinishedAt_31_100ms.getFinishDate()) + "),state:any",
                getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms)); //new time parsing uses ms
    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.ENGLISH)).format(bFinishedAt_31_100ms.getFinishDate()) + "),state:any",
                getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));


    checkBuilds("startDate:(build:(id:" + bFinishedAt_10_000ms.getBuildId() + "),condition:after)", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("startDate:(build:(id:" + bFinishedAt_10_000ms.getBuildId() + "),condition:after),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));

    checkBuilds("startDate:(build:(id:" + bFinishedAt_10_000ms.getBuildId() + "))", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    if (bFinishedAt_10_000ms.getStartDate().getTime() % 1000 == 0) {
      checkBuilds("startDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getStartDate()) + ")", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
      checkBuilds("startDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getStartDate()) + ",includeInitial:true)", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));

      checkBuilds("startDate:(" + Util.formatTime(bFinishedAt_10_000ms.getStartDate()) + ")", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    } else{
      checkBuilds("startDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getStartDate()) + ")", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));
      checkBuilds("startDate:(date:" + Util.formatTime(bFinishedAt_10_000ms.getStartDate()) + ",includeInitial:true)", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));

      checkBuilds("startDate:(" + Util.formatTime(bFinishedAt_10_000ms.getStartDate()) + ")", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));
    }
    checkBuilds("startDate:(date:" + getTimeWithMs(bFinishedAt_10_000ms.getStartDate()) + ")", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("startDate:(" + getTimeWithMs(bFinishedAt_10_000ms.getStartDate()) + ")", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));

    checkBuilds("queuedDate:(build:(id:" + bFinishedAt_10_000ms.getBuildId() + "),condition:after)", getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));
    checkBuilds("queuedDate:(build:(id:" + bFinishedAt_10_000ms.getBuildId() + "),condition:after),state:any",
                getBuildPromotions(bQueuedAt_131_200ms_and_moved_to_top, bQueuedAt_111_200ms, bQueuedAt_121_200ms, bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms));

    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms, bFinishedAt_31_200ms, bFinishedAt_31_100ms, bFinishedAt_10_000ms));

    time.jumpTo(time_61_200ms.getTime() + 10 * 60 * 1000 - time.getNow().getTime()); // set time to afterBuild30 + 10 minutes
    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms));
    time.jumpTo(9);
    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms));
    time.jumpTo(1);
    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms));
    time.jumpTo(60*10);
    checkBuilds("startDate:(-10m),state:any");

    checkBuilds("startDate:(build:(id:" + bFinishedAt_81_200ms.getBuildId() + "),shift:-11s),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms));
    checkBuilds("startDate:(build:(id:" + bFinishedAt_81_200ms.getBuildId() + "),shift:-10s),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms, bFinishedAt_81_200ms));
    checkBuilds("startDate:(build:(id:" + bFinishedAt_81_200ms.getBuildId() + "),shift:+9s),state:any", getBuildPromotions(bStartedAt_101_200ms, bFinishedAt_91_200ms));
    checkBuilds("startDate:(build:(id:" + bFinishedAt_81_200ms.getBuildId() + "),shift:+10s),state:any", getBuildPromotions(bStartedAt_101_200ms));
    checkBuilds("startDate:(condition:after,build:(id:" + bFinishedAt_81_200ms.getBuildId() + ")" +
                ",shift:-11s),startDate:(condition:before,build:(id:" + bFinishedAt_81_200ms.getBuildId() + "),shift:+11s),state:any",
                getBuildPromotions(bFinishedAt_91_200ms, bFinishedAt_81_200ms, bFinishedAt_71_200ms));
    checkBuilds("startDate:(condition:after,build:(id:" + bFinishedAt_81_200ms.getBuildId() + ")" +
                ",shift:-9s),startDate:(condition:before,build:(id:" + bFinishedAt_81_200ms.getBuildId() + "),shift:+9s),state:any",
                getBuildPromotions(bFinishedAt_81_200ms));
    checkBuilds("startDate:(condition:after,build:(id:" + bFinishedAt_81_200ms.getBuildId() + ")" +
                ",shift:-10s),startDate:(condition:before,build:(id:" + bFinishedAt_81_200ms.getBuildId() + "),shift:+10s),state:any",
                getBuildPromotions(bFinishedAt_81_200ms));

    checkExceptionOnBuildsSearch(BadRequestException.class, "finishDate:(xxx)");
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "finishDate:(time:20150101T000000+0000,build:(id:3))");
    checkExceptionOnBuildsSearch(BadRequestException.class, "finishDate:(time:20150101T000000+0000,condition:xxx)");
  }

  @Test
  public void testTimesISO() {
    final MockTimeService time = new MockTimeService(new DateTime(2016, 2, 16, 16, 47, 43, 0, DateTimeZone.forOffsetHours(1)).getMillis());
    myServer.setTimeService(time);
    initFinders(); //recreate finders to let time service sink in

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildConf1).finish();  //20160216T164743.000+0100
    time.jumpTo(7L * 24 * 60 * 60 * 1000); // +1 week
    time.jumpTo(10);
    final SFinishedBuild build15 = build().in(buildConf1).finish();  //20160223T164753.000+0100
    time.jumpTo(24 * 60 * 60);  // +1 day
    final SFinishedBuild build20 = build().in(buildConf1).finish();  //20160224T164753.000+0100
    time.jumpTo(10050L);
    final SFinishedBuild build30 = build().in(buildConf1).finish();  //20160224T164803.050+0100
    time.jumpTo(100L);
    final SFinishedBuild build35 = build().in(buildConf1).finish();  //20160224T164803.150+0100
    time.jumpTo(24 * 60 * 60);  // +1 day
    time.jumpTo(10);
    final SFinishedBuild build40 = build().in(buildConf1).finish(); //20160225T164813.050+0100

    checkBuilds(null, getBuildPromotions(build40, build35, build30, build20, build15, build10));
    checkBuilds("finishDate:(date:20160224T164803.0+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T164803.050+0100)", getBuildPromotions(build40, build35));
    checkBuilds("finishDate:(date:20160224T164803.049+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T154803.049Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T154803Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T154803.0Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T154803.999Z)", getBuildPromotions(build40));
    checkBuilds("finishDate:(date:20160224T154804Z)", getBuildPromotions(build40));

    setInternalProperty("rest.timeMatching.roundEntityTimeToSeconds", "true");
    checkBuilds("finishDate:(date:20160224T154803Z)", getBuildPromotions(build40)); //this uses compatibility approach comparing the build time to be strongly > by seconds
    removeInternalProperty("rest.timeMatching.roundEntityTimeToSeconds");

    checkBuilds("finishDate:(date:2016-02-24T16:48:03.049+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24T16:48:03+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24T16:48:03.049Z)", getBuildPromotions(build40));
    checkBuilds("finishDate:(date:2016-02-24T15:48:03.049Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24T15:48:03Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T164803.0+01)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24)", getBuildPromotions(build40, build35, build30, build20));
    checkBuilds("finishDate:(date:2016-2-23)", getBuildPromotions(build40, build35, build30, build20, build15));
    checkBuilds("finishDate:(date:2016-02-23)", getBuildPromotions(build40, build35, build30, build20, build15));
    checkBuilds("finishDate:(date:2016-W8)", getBuildPromotions(build40, build35, build30, build20, build15));
    checkBuilds("finishDate:(shift:-1d)", getBuildPromotions(build40));
    checkBuilds("finishDate:(shift:-1d10s1ms)", getBuildPromotions(build40, build35));
    checkBuilds("finishDate:(date:00:00)", getBuildPromotions(build40));
    checkBuilds("finishDate:(date:00:00,shift:-1d)", getBuildPromotions(build40, build35, build30, build20));
    checkBuilds("finishDate:(date:00:00,shift:+1m)", getBuildPromotions(build40));
    checkBuilds("finishDate:(date:0,shift:-48h)", getBuildPromotions(build40, build35, build30, build20, build15));

    checkExceptionOnBuildsSearch(BadRequestException.class, "finishDate:(date:20160224T164803+0100xxx)");
  }

  @Test
  public void testTimesNoTimeZone() {
    //using year 2001 as with 2016 there are not actual tz issues (e.g. MSK with JDK 1.8_05)
    final MockTimeService time = new MockTimeService(new DateTime(2001, 2, 16, 16, 47, 43, 0).getMillis());

    myServer.setTimeService(time);
    initFinders(); //recreate finders to let time service sink in

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildConf1).finish();
    time.jumpTo(120);
    final SFinishedBuild build15 = build().in(buildConf1).finish();
    time.jumpTo(120);
    final SFinishedBuild build20 = build().in(buildConf1).finish();

    checkBuilds("finishDate:(date:2001-02-16T16:47:44)", getBuildPromotions(build20, build15));
    checkBuilds("finishDate:(date:20010216T164744.0)", getBuildPromotions(build20, build15));
  }

  @Test(dataProvider = "allBooleans")
  public void testStopProcessing(boolean timeAsSecondBoundary) {
    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);
    if (timeAsSecondBoundary) {
      time.jumpTo(1000L - time.now() % 1000);
    } else if (time.now() % 1000 == 0){
      time.jumpTo(10L);
    }
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild finishedBuild03 = build().in(buildConf1).finish();
    time.jumpTo(10);
    final SFinishedBuild finishedBuild05 = build().in(buildConf1).finish();
    time.jumpTo(10);
    final SQueuedBuild queuedBuild10 = build().in(buildConf1).addToQueue();
    time.jumpTo(10);
    final SQueuedBuild queuedBuild20 = build().in(buildConf1).parameter("a", "x").addToQueue();
    time.jumpTo(10);
    myFixture.getBuildQueue().moveTop(queuedBuild20.getItemId());
    final RunningBuildEx runningBuild20 = BuildBuilder.run(queuedBuild20, myFixture);
    time.jumpTo(10);
    final SQueuedBuild queuedBuild30 = build().in(buildConf1).parameter("a", "y").addToQueue();
    time.jumpTo(10);
    myFixture.getBuildQueue().moveTop(queuedBuild10.getItemId());
    final MockBuildAgent agent2 = myFixture.createEnabledAgent("Ant");// adding one more agent to allow parallel builds
    final RunningBuildEx runningBuild10 = BuildBuilder.run(queuedBuild10, myFixture);
    time.jumpTo(10);
    final SFinishedBuild finishedBuild10 = myFixture.finishBuild(runningBuild10, false);
    time.jumpTo(10);
    final RunningBuildEx runningBuild30 = BuildBuilder.run(queuedBuild30, myFixture);
    agent2.setEnabled(false, null, "");
    time.jumpTo(10);
    final SFinishedBuild finishedBuild30 = myFixture.finishBuild(runningBuild30, false);
    time.jumpTo(10);
    final SFinishedBuild finishedBuild20 = myFixture.finishBuild(runningBuild20, false);
    time.jumpTo(10);
    final SQueuedBuild queuedBuild40 = build().in(buildConf1).parameter("a", "z").addToQueue();
    time.jumpTo(10);
    final SQueuedBuild queuedBuild50 = build().in(buildConf1).parameter("a", "z1").addToQueue();
    time.jumpTo(10);
    myFixture.getBuildQueue().moveTop(queuedBuild50.getItemId());
    final RunningBuildEx runningBuild50 = BuildBuilder.run(queuedBuild50, myFixture);
    time.jumpTo(10);

    checkBuilds("state:any", 7, getBuildPromotions(queuedBuild40, runningBuild50, finishedBuild30, finishedBuild10, finishedBuild20, finishedBuild05, finishedBuild03));
    checkBuilds("startDate:(build:(id:" + finishedBuild10.getBuildId() + "),condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30));
    checkBuilds("startDate:(build:(id:" + finishedBuild20.getBuildId() + "),condition:after),state:any", 6, getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after),state:any", 4, getBuildPromotions(runningBuild50));
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after)", 2, new BuildPromotion[]{});
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after),state:finished", 2, new BuildPromotion[]{});
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after),state:queued", 1, new BuildPromotion[]{});
    checkBuilds("startDate:(build:(id:" + finishedBuild10.getBuildId() + "),condition:after)", 3, getBuildPromotions(finishedBuild30));
    checkBuilds("startDate:(build:(id:" + finishedBuild20.getBuildId() + "),condition:after)", 4, getBuildPromotions(finishedBuild30, finishedBuild10));

    if (finishedBuild10.getStartDate().getTime() % 1000 == 0) {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after,includeInitial:true),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
    } else {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after,includeInitial:true),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
    }

    checkBuilds("startDate:(date:" + getTimeWithMs(finishedBuild10.getStartDate()) + ",condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30));
    checkBuilds("startDate:(date:" + getTimeWithMs(new Date(finishedBuild10.getStartDate().getTime() - 1)) + ",condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
    checkBuilds("startDate:(date:" + getTimeWithMs(new Date(finishedBuild10.getStartDate().getTime() + 1)) + ",condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30));
    checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:before),state:any", 7, getBuildPromotions(finishedBuild20, finishedBuild05, finishedBuild03));

    if (finishedBuild20.getStartDate().getTime() % 1000 == 0) {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after),state:any", 6,
                  getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after,includeInitial:true),state:any", 6,
                  getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10, finishedBuild20));
    } else {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after),state:any", 6,
                  getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10, finishedBuild20));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after,includeInitial:true),state:any", 6,
                  getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10, finishedBuild20));
    }
    
    checkBuilds("startDate:(date:" + getTimeWithMs(new Date(finishedBuild20.getStartDate().getTime())) + ",condition:after),state:any", 6, getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));

    if (finishedBuild30.getStartDate().getTime() % 1000 == 0) {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after),state:any", 4, getBuildPromotions(runningBuild50));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after,includeInitial:true),state:any", 4, getBuildPromotions(runningBuild50, finishedBuild30));
    } else {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after),state:any", 4, getBuildPromotions(runningBuild50, finishedBuild30));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after,includeInitial:true),state:any", 4, getBuildPromotions(runningBuild50, finishedBuild30));
    }
    checkBuilds("startDate:(date:" + getTimeWithMs(finishedBuild30.getStartDate()) + ",condition:after),state:any", 4, getBuildPromotions(runningBuild50));
    if (finishedBuild30.getStartDate().getTime() % 1000 == 0) {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after)", 2, getBuildPromotions());
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after,includeInitial:true)", 2, getBuildPromotions(finishedBuild30));
    } else {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after)", 2, getBuildPromotions(finishedBuild30));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after,includeInitial:true)", 2, getBuildPromotions(finishedBuild30));
    }
    checkBuilds("startDate:(date:" + getTimeWithMs(finishedBuild30.getStartDate()) + ",condition:after)", 2, new BuildPromotion[]{});
    if (finishedBuild10.getStartDate().getTime() % 1000 == 0) {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after)", 3, getBuildPromotions(finishedBuild30));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after,includeInitial:true)", 3, getBuildPromotions(finishedBuild30, finishedBuild10));
    } else {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after)", 3, getBuildPromotions(finishedBuild30, finishedBuild10));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after,includeInitial:true)", 3, getBuildPromotions(finishedBuild30, finishedBuild10));
    }
    checkBuilds("startDate:(date:" + getTimeWithMs(finishedBuild10.getStartDate()) + ",condition:after)", 3, getBuildPromotions(finishedBuild30));
    if (finishedBuild20.getStartDate().getTime() % 1000 == 0) {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after)", 4, getBuildPromotions(finishedBuild30, finishedBuild10));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after,includeInitial:true)", 4, getBuildPromotions(finishedBuild30, finishedBuild10, finishedBuild20));
    } else {
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after)", 4, getBuildPromotions(finishedBuild30, finishedBuild10, finishedBuild20));
      checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after,includeInitial:true)", 4, getBuildPromotions(finishedBuild30, finishedBuild10, finishedBuild20));
    }
    checkBuilds("startDate:(date:" + getTimeWithMs(finishedBuild20.getStartDate()) + ",condition:after)", 4, getBuildPromotions(finishedBuild30, finishedBuild10));

    checkBuilds(
      "startDate:(build:(id:" + finishedBuild20.getBuildId() + "),condition:after),startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:before),state:any", 6,
      getBuildPromotions(finishedBuild10));
    checkBuilds(
      "startDate:(build:(id:" + finishedBuild05.getBuildId() + "),condition:after),finishDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:before),state:any", 7,
      getBuildPromotions(finishedBuild10));
  }

  @NotNull
  private String getTimeWithMs(@NotNull final Date time) {
    return (new SimpleDateFormat(TeamCityProperties.getProperty("rest.defaultDateFormat", "yyyyMMdd'T'HHmmss.SSSZ"), Locale.ENGLISH)).format(time);
  }

  @Test
  public void testLookupLimit() throws IOException {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild finishedBuild03 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild03, map("a", "b"));
    final SFinishedBuild finishedBuild05 = build().in(buildConf2).finish();
    writeFinalParameters(finishedBuild05, map());

    for (int i = 0; i < 100; i++) {
      build().in(buildConf1).finish();
    }

    checkBuilds("property:(name:a)", 102, getBuildPromotions(finishedBuild03));
    checkBuilds("property:(name:a),lookupLimit:20", 21, new BuildPromotion[]{});

    setInternalProperty("rest.request.builds.defaultLookupLimit", "30");
    checkBuilds("property:(name:a)", 101, new BuildPromotion[]{}); //lookupLimit should not be less than count which is 100 by defualt
    checkBuilds("property:(name:a),count:30", 31, new BuildPromotion[]{});

    checkBuilds("property:(name:a),lookupLimit:40", 41, new BuildPromotion[]{});

    checkNoBuildFound("property:(name:a)");
  }

  @Test
  public void testSinceWithQueuedBuilds() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildPromotion queuedBuild10 = build().in(buildConf).addToQueue().getBuildPromotion();
    final BuildPromotion queuedBuild20 = build().in(buildConf).parameter("a", "x").addToQueue().getBuildPromotion(); //preventing build from merging in the queue
    final BuildPromotion queuedBuild30 = build().in(buildConf).parameter("a", "y").addToQueue().getBuildPromotion(); //preventing build from merging in the queue
    //noinspection ConstantConditions
    myFixture.getBuildQueue().moveTop(queuedBuild30.getQueuedBuild().getItemId());

    checkBuilds("state:any", queuedBuild30, queuedBuild10, queuedBuild20);
    checkBuilds("state:any,sinceBuild:(id:" + queuedBuild10.getId() +")", queuedBuild30, queuedBuild20);
    checkBuilds("state:any,sinceBuild:(id:" + queuedBuild20.getId() +")", queuedBuild30);
    checkBuilds("state:any,sinceBuild:(id:" + queuedBuild30.getId() +")");
  }

  @Test
  public void testRevision() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "rev_Vcs1_1")));
    changesPolicy.setCurrentState(root2, createVersionState("master", map("master", "xxx")));
    final BuildPromotion build10 = build().in(buildConf).finish().getBuildPromotion();
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "rev_Vcs1_2")));
    final BuildPromotion build20 = build().in(buildConf).finish().getBuildPromotion();
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "xxx")));
    changesPolicy.setCurrentState(root2, createVersionState("master", map("master", "rev_Vcs2_3")));
    final BuildPromotion build30 = build().in(buildConf).finish().getBuildPromotion();
    final MockVcsModification modification = new MockVcsModification(null, "comment", new Date(), "change_v1") {
      @Override
      public String getDisplayVersion() {
        return "change_V1_display";
      }
    }.setRoot(root1);
    vcs.addChange(root1, modification);
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "change_v1")));
    final BuildPromotion build40 = build().in(buildConf).onModifications(modification).finish().getBuildPromotion();

    checkBuilds("revision:rev_Vcs1_2", build20);
    checkBuilds("revision:rev_Vcs1_");
    checkBuilds("revision:(version:rev_Vcs1_2)", build20);
    checkBuilds("revision:(version:rev_Vcs1_)");
    checkBuilds("revision:(version:(value:rev_Vcs1_2))", build20);
    checkBuilds("revision:(version:(value:rev_Vcs1_,matchType:equals))");
    checkBuilds("revision:(version:(value:rev_Vcs1_2,matchType:contains))", build20);
    checkBuilds("revision:(version:(value:rev_Vcs1_,matchType:contains))", build20, build10);
    checkBuilds("revision:(version:(value:abra,matchType:contains))");
    checkBuilds("revision:(internalVersion:change_v1)", build40);
    checkBuilds("revision:(version:change_V1_display)", build40);
    checkBuilds("revision:(version:change_V1_display,vcsRoot:(id:" + root1.getParent().getExternalId() + "))", build40);
    checkBuilds("revision:(internalVersion:xxx)", build30, build20, build10);
    checkBuilds("revision:(internalVersion:xxx,vcsRoot:(id:" + root1.getParent().getExternalId() + "))", build30);
  }

  @Test
  public void testDefaultFiltering() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SUser user = createUser("uuser");

    SBuild build1 = build().in(buildConf).finish();
    SBuild build2failed = build().in(buildConf).failed().finish();

    SBuild build5personal = build().in(buildConf).personalForUser(user.getUsername()).finish();

    RunningBuildEx build7running = startBuild(buildConf);
    build7running.stop(user, "cancel comment");
    SBuild build7canceled = finishBuild(build7running, false);

    final RunningBuildEx build8running = startBuild(buildConf);
    build8running.addBuildProblem(createBuildProblem()); //make the build failed
    build8running.stop(user, "cancel comment");
    SBuild build8canceledFailed = finishBuild(build8running, true);

    SBuild build9failedToStart = build().in(buildConf).failedToStart().finish();
    SBuild build11inBranch = build().in(buildConf).withBranch("branch").finish();

    SBuild build13running = startBuild(buildConf);
    SQueuedBuild build14queued = addToQueue(buildConf);

    checkBuilds(null, getBuildPromotions(build2failed, build1));
    checkBuilds("defaultFilter:false", getBuildPromotions(build14queued, build13running, build11inBranch, build9failedToStart, build8canceledFailed, build7canceled, build5personal, build2failed, build1));
    checkBuilds("defaultFilter:true", getBuildPromotions(build2failed, build1));
    checkBuilds("canceled:true", getBuildPromotions(build8canceledFailed, build7canceled));
    checkBuilds("canceled:true,state:(queued:true)");
    checkBuilds("failedToStart:true,state:(queued:true)");
    checkBuilds("canceled:false", getBuildPromotions(build2failed, build1));
    checkBuilds("canceled:false,defaultFilter:false", getBuildPromotions(build14queued, build13running, build11inBranch, build9failedToStart, build5personal, build2failed, build1));
    checkBuilds("canceled:any", getBuildPromotions(build8canceledFailed, build7canceled, build2failed, build1));
    checkBuilds("personal:true", getBuildPromotions(build5personal));
    checkBuilds("personal:any", getBuildPromotions(build5personal, build2failed, build1));
    checkBuilds("state:any", getBuildPromotions(build14queued, build13running, build2failed, build1));
  }

  @Test //(invocationCount = 2000)
  public void testBuildNumber() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SUser user = createUser("uuser");

    SBuild build10 = build().in(buildConf).finish();
    SBuild build20 = build().in(buildConf).number("100").finish();
    SBuild build30 = build().in(buildConf).number("100").failed().finish();
    SBuild build40 = build().in(buildConf).number("100").failedToStart().finish();
    SBuild build50 = build().in(buildConf).number("100").personalForUser(user.getUsername()).finish();
    SBuild build60 = build().in(buildConf).number("100").cancel(user);
    SBuild build70 = build().in(buildConf).finish();
    SBuild build80 = build().in(buildConf).number("100").run();

    final String buildTypeDimension = ",buildType:(id:" + buildConf.getExternalId() + ")";
    String buildNumber = build80.getBuildNumber();
    if (!"100".equals(buildNumber)) {
      fail("Concurrency detected on setting and then getting running build number!  Got: " + buildNumber);
    }
    checkBuilds("number:100" + buildTypeDimension, getBuildPromotions(build30, build20));
    checkBuilds("number:100", getBuildPromotions(build30, build20));
//getBuildPromotions(build80, build60, build50, build40, build30, build20, build10)
    checkBuilds("number:100,defaultFilter:false", getBuildPromotions(build80, build60, build50, build40, build30, build20));
    checkBuilds("number:100,defaultFilter:false" + buildTypeDimension,                getBuildPromotions(build80, build60, build50, build40, build30, build20));
    checkBuilds("number:100,canceled:any", getBuildPromotions(build60, build30, build20));
    checkBuilds("number:100,canceled:any" + buildTypeDimension, getBuildPromotions(build60, build30, build20));
    checkBuilds("number:100,personal:true", getBuildPromotions(build50));
    checkBuilds("number:100,personal:true" + buildTypeDimension, getBuildPromotions(build50));
    checkBuilds("number:100,personal:any", getBuildPromotions(build50, build30, build20));
    checkBuilds("number:100,personal:any" + buildTypeDimension, getBuildPromotions(build50, build30, build20));
    checkBuilds("number:100,state:any", getBuildPromotions(build80, build30, build20));
    checkBuilds("number:100,state:any" + buildTypeDimension, getBuildPromotions(build80, build30, build20));
    checkBuilds("number:100,running:true", getBuildPromotions(build80));
    checkBuilds("number:100,running:true" + buildTypeDimension, getBuildPromotions(build80));
    checkBuilds("number:100,running:false", getBuildPromotions(build30, build20));
    checkBuilds("number:100,running:false" + buildTypeDimension, getBuildPromotions(build30, build20));

    checkBuilds("count:1,number:378" + buildTypeDimension);
  }

  @Test
  public void testBuildNumberAndBranch() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    SBuild build10 = build().in(buildConf).finish();
    SBuild build20 = build().in(buildConf).number("100").finish();
    SBuild build30 = build().in(buildConf).withBranch("branch").number("100").finish();
    SBuild build40 = build().in(buildConf).withDefaultBranch().number("100").finish();

    final String buildTypeDimension = ",buildType:(id:" + buildConf.getExternalId() + ")";

    checkBuilds("number:100" + buildTypeDimension, getBuildPromotions(build40, build20));
    checkBuilds("number:100,branch:(default:any)" + buildTypeDimension, getBuildPromotions(build40, build30, build20));
    checkBuilds("number:100,branch:(name:branch)" + buildTypeDimension, getBuildPromotions(build30));
    checkBuilds("number:100,defaultFilter:false" + buildTypeDimension, getBuildPromotions(build40, build30, build20));
  }

  @Test
  public void testBuildNumberCondition() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    SBuild buildHello = build().in(buildConf).number("hello").finish();
    SBuild buildWorld = build().in(buildConf).number("hello, world!").finish();
    SBuild buildFriend = build().in(buildConf).number("hello, my dear friend!").finish();

    final String buildTypeDimension = ",buildType:" + buildConf.getExternalId();
    checkBuilds("number:hello" + buildTypeDimension, getBuildPromotions(buildHello));
    checkBuilds("number:(matchType:starts-with,value:hello)" + buildTypeDimension, getBuildPromotions(buildFriend, buildWorld, buildHello));
    checkBuilds("number:(matchType:starts-with,value:hello)", getBuildPromotions(buildFriend, buildWorld, buildHello));
    checkBuilds("number:(matchType:starts-with,value:hello),project:(name:project)", getBuildPromotions(buildFriend, buildWorld, buildHello));
  }

  @Test
  @TestFor(issues = "TW-78993")
  public void testBuildNumberAndProject() {
    setInternalProperty("rest.builds.selectByProjectAndBuildNumberOptimization.enabled", true);
    ProjectEx project = myFixture.createProject("project");
    ProjectEx projectChild = myFixture.createProject("projectChild", project);
    BuildTypeImpl buildConf11 = registerBuildType("buildConf11", project, "Ant");
    BuildTypeImpl buildConf12 = registerBuildType("buildConf12", project, "Ant");
    BuildTypeImpl buildConf111 = registerBuildType("buildConf111", projectChild, "Ant");
    // unrelatedProject
    BuildTypeImpl buildConf21 = registerBuildType("buildConf21", "project2");

    SBuild build21_0 = build().in(buildConf21).number("1").finish();
    SBuild build21_1 = build().in(buildConf21).number("100").finish();
    SBuild build11_1 = build().in(buildConf11).number("1").finish();
    SBuild build11_2 = build().in(buildConf11).number("100").finish();
    SBuild build11_3 = build().in(buildConf11).number("100").finish();
    SBuild build111_1 = build().in(buildConf111).number("100").finish();
    SBuild build12_1 = build().in(buildConf12).number("100").finish();
    SBuild build12_2 = build().in(buildConf11).number("1").finish();

    checkBuilds("number:100,project:project", getBuildPromotions(build12_1, build11_3, build11_2));
    checkBuilds("number:100,affectedProject:project", getBuildPromotions(build12_1, build111_1, build11_3, build11_2));
    checkBuilds("number:100,project:project,count:1", getBuildPromotions(build12_1));
  }

  @Test
  public void testWithProject() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    SBuild build10 = build().in(buildConf).finish();
    SBuild build20 = build().in(buildConf).number("100").finish();
    SBuild build30 = build().in(buildConf).number("100").failed().finish();
    SBuild build40 = build().in(buildConf).number("100").failedToStart().finish();

    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    SBuild build100 = build().in(buildConf2).failed().finish();

    PagedSearchResult<BuildPromotion> promotions = myBuildPromotionFinder.getBuildPromotions(buildConf, null);
    assertEquals(3, promotions.getEntries().size());

    promotions = myBuildPromotionFinder.getBuildPromotions(buildConf, "buildType:$any,status:FAILURE");
    assertEquals(2, promotions.getEntries().size());
  }

  @Test
  public void testParametersCondition() throws IOException {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final SFinishedBuild finishedBuild05 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild05, map());

    final SFinishedBuild finishedBuild10 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild10, map("a", "10", "b", "10", "aa", "15"));

    final SFinishedBuild finishedBuild20 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild20, map("b", "20", "myProp1", "randomValue#mPWh1dEHNVHVPhE17nwzYJng"));

    final SFinishedBuild finishedBuild30 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild30, map("c", "20", "myProp2", "randomValue#mPWh1dEHNVHVPhE17nwzYJng"));

    final SFinishedBuild finishedBuild35 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild35, map("c", "10"));

    final SFinishedBuild finishedBuild40 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild40, map("zzz", "30"));

    final SFinishedBuild finishedBuild50 = build().in(buildConf1).finish();
    writeFinalParameters(finishedBuild50, map("aa", "10", "aaa", "10"));

    checkBuilds("property:(name:a)", getBuildPromotions(finishedBuild10));
    checkBuilds("property:(name:b)", getBuildPromotions(finishedBuild20, finishedBuild10));
    checkBuilds("property:(name:b,value:20)", getBuildPromotions(finishedBuild20));
    checkBuilds("property:(value:randomValue#mPWh1dEHNVHVPhE17nwzYJng)", getBuildPromotions(finishedBuild30, finishedBuild20));

    //"contains" by default
    checkBuilds("property:(value:0,matchType:contains)", getBuildPromotions(finishedBuild50, finishedBuild40, finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10, finishedBuild05));

    //finds all as entire build params map is used for the search and build has a parameter with build conf id
    checkBuilds("property:(value:" + buildConf1.getExternalId() + ")",
                getBuildPromotions(finishedBuild50, finishedBuild40, finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10, finishedBuild05));

    checkBuilds("property:(value:randomValue#mPWh1dEHNVHVPhE17nwzYJng,matchType:equals)", getBuildPromotions(finishedBuild30, finishedBuild20));
    checkBuilds("property:(value:15,matchType:more-than)", getBuildPromotions(finishedBuild40, finishedBuild30, finishedBuild20));
    checkBuilds("property:(name:b,value:15,matchType:more-than)", getBuildPromotions(finishedBuild20));
//this is not reported anyhow from the core (Requirement)    checkExceptionOnBuildsSearch(BadRequestException.class, "property:(value:[,matchType:matches)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "property:(value:10,matchType:Equals)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "property:(value:10,matchType:AAA)");

    checkBuilds("property:(name:(value:([b,c]),matchType:matches))", getBuildPromotions(finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10));
    checkBuilds("property:(name:(value:.,matchType:matches),value:15,matchType:more-than)", getBuildPromotions(finishedBuild30, finishedBuild20));
    checkBuilds("property:(matchScope:any,value:10,matchType:equals)", getBuildPromotions(finishedBuild50, finishedBuild35, finishedBuild10));
//this does not work as all build params are checked, not only custom    checkBuilds("property:(matchScope:all,value:10,matchType:equals)", getBuildPromotions(finishedBuild50, finishedBuild35));
    checkBuilds("property:(name:(value:(a*),matchType:matches),matchScope:all,value:10,matchType:equals)", getBuildPromotions(finishedBuild50));
    checkBuilds("property:(name:(value:(.),matchType:matches),matchScope:any,value:10,matchType:equals)", getBuildPromotions(finishedBuild35, finishedBuild10));
    checkBuilds("property:(name:zzz)", getBuildPromotions(finishedBuild40));
    checkBuilds("property:(name:z.z)");

    //builds with at least one param not equal to 10
    checkBuilds("property:(value:10,matchType:does-not-match)",
                getBuildPromotions(finishedBuild50, finishedBuild40, finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10, finishedBuild05));
    //builds with no param equal to 10
    checkBuilds("property:(matchScope:all,value:10,matchType:does-not-equal)",
                getBuildPromotions(finishedBuild40, finishedBuild30, finishedBuild20, finishedBuild05));
  }

  @Test
  public void testBranchDimension() throws ExecutionException, InterruptedException {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    buildConf1.addVcsRoot(buildConf1.getProject().createVcsRoot("vcs", "extId", "name"));

    final VcsRootInstance vcsRootInstance = buildConf1.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");
    myFixture.getVcsModificationChecker().checkForModifications(buildConf1.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    final BuildPromotion build30 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf1).withDefaultBranch().finish().getBuildPromotion();
    final BuildPromotion build50 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf1).withBranch("branch1").finish().getBuildPromotion();

    final BuildPromotion build65 = build().in(buildConf1).withBranch(Branch.UNSPECIFIED_BRANCH_NAME).finish().getBuildPromotion(); //right way to run unspecified?

    final RunningBuildEx running70 = build().withBranch("branch1").in(buildConf1).run();
    running70.stop(getOrCreateUser("user1"), "cancel comment");
    final BuildPromotion build70 = finishBuild(running70, true).getBuildPromotion();

    final BuildPromotion build80 = build().in(buildConf1).withBranch("branch1").run().getBuildPromotion();
    final BuildPromotion build90 = build().in(buildConf1).withBranch("branch1").addToQueue().getBuildPromotion();

    checkBuilds("defaultFilter:false", build90, build80, build70, build65, build60, build50, build40, build30, build20, build10);
    checkBuilds(null, build40, build30, build10);
    checkBuilds("branch:(default:any)", build65, build60, build50, build40, build30, build20, build10);
    checkBuilds("branch:(default:true)", build40, build30, build10);
    checkBuilds("branch:(default:false)", build65, build60, build50, build20);
    checkBuilds("branch:(branched:true)", build65, build60, build50, build40, build30, build20);
    checkBuilds("branch:(branched:false)",build10);
    checkBuilds("branch:(unspecified:true)", build65);
    checkBuilds("branch:(name:<unspecified>)", build65);
    checkBuilds("branch:(unspecified:false)", build60, build50, build40, build30, build20, build10);
    checkBuilds("branch:(name:branch1)", build60);
    checkBuilds("branch:branch1", build60);
    checkBuilds("branch:Branch1", build60);
    checkBuilds("branch:(name:master)", build40, build30);
    checkBuilds("branch:(name:Master)", build40, build30);

    checkBuilds("branch:(name:<default>)", build40, build30);
    checkBuilds("branch:(name:<Default>)", build40, build30);
    checkBuilds("branch:<default>", build40, build30);
    checkBuilds("branch:<Default>", build40, build30);
    checkBuilds("branch:(name:<any>)", build65, build60, build50, build40, build30, build20, build10);
    checkBuilds("branch:<any>", build65, build60, build50, build40, build30, build20, build10);

    checkBuilds("branch:(name:branch1)", build60);
    checkBuilds("branch:(name:master)", build40, build30);
    checkBuilds("branch:(name:(value:branch1))", build60);
    checkBuilds("branch:(name:(value:Branch1))");
    checkBuilds("branch:(name:(value:branch1,matchType:equals))", build60);
    checkBuilds("branch:branch", build50, build20);
    checkBuilds("branch:(name:(value:branch,matchType:starts-with))", build60, build50, build20);
    checkBuilds("branch:(name:(<default>))", build40, build30);
    checkBuilds("branch:(name:(value:<default>))"); //when full value condition syntax is used, <default> has no special meaning as we compare displayName, use "default" dimension instead
    checkBuilds("branch:(name:(value:<Default>))");
    checkBuilds("branch:(name:(value:<any>))");

    checkBuilds("branch:(buildType:(id:" + buildConf1.getExternalId() + "),policy:ALL_BRANCHES)", build65, build60, build50, build40, build30, build20, build10);
    checkBuilds("branch:(buildType:(id:" + buildConf1.getExternalId() + "),policy:ALL_BRANCHES),branched:true", build65, build60, build50, build40, build30, build20);
    checkBuilds("branch:(buildType:(id:" + buildConf1.getExternalId() + "),policy:VCS_BRANCHES)", build60, build40, build30, build10);
    checkBuilds("branch:(buildType:(id:" + buildConf1.getExternalId() + "),policy:VCS_BRANCHES),branched:true", build60, build40, build30);

    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(policy:ALL_BRANCHES)", build65, build60, build50, build40, build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(policy:ALL_BRANCHES),branched:true", build65, build60, build50, build40, build30, build20);
    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(policy:VCS_BRANCHES)", build60, build40, build30, build10);
    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(policy:VCS_BRANCHES),branched:true", build60, build40, build30);
    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(default:true)", build40, build30, build10);

    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(policy:ALL_BRANCHES)"); //policy is not supported for the build's branch locator
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(aaa:bbb)");

    // check that no filtering is done when not necessary
    assertEquals(ItemFilterUtil.empty(), myBranchFinder.getFilter(new Locator("<any>")));
    assertEquals(ItemFilterUtil.empty(), myBranchFinder.getFilter(new Locator("default:any")));
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  @Test //related issue: TW-51368
  public void testBranchDimensionWithSymbols() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf1).withBranch("b").finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).withBranch("b(1)").finish().getBuildPromotion();
    final BuildPromotion build25 = build().in(buildConf1).withBranch("(b(1))").finish().getBuildPromotion();
    final BuildPromotion build27 = build().in(buildConf1).withBranch("b(1)c").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).withBranch("b(1").finish().getBuildPromotion();

    checkBuilds("defaultFilter:false", build30, build27, build25, build20, build10);

    String altLocatorPart = "buildType:(id:" + buildConf1.getExternalId() + "),";
    {
      String branchName = "b";
      BuildPromotion build = build10;
      checkBuilds(BuildPromotionFinder.getLocator(buildConf1, new BranchImpl(branchName), null), build);

      checkBuilds("branch:(" + branchName + ")", build);
      checkBuilds("branch:((" + branchName + "))", build); //this is strange, but this is how it works currently due to nested locator support
      checkBuilds("branch:(name:(" + branchName + "))", build);
      checkBuilds("branch:(name:$base64:" + base64(branchName) + ")", build);
      checkBuilds("branch:(name:$base64:" + base64("value:(" + branchName + ")") + ")", build); //this should actually search for build with branch name "value:(b)", but documenting current behavior
      checkBuilds("branch:(name:(value:(" + branchName + ")))", build);
      checkBuilds("branch:(name:(value:$base64:" + base64(branchName) + "))", build);
      checkBuilds("branch:($base64:" + base64("name:(" + branchName + ")") + ")", build); //not quite right (no nested base64 unescaping should occur), but documenting current behavior
      checkBuilds("branch:($base64:" + base64("name:$base64:" + base64(branchName) + "") + ")", build); //documenting current behavior

      //when buildType is present in the locator, the processing logic might differ
      checkBuilds(altLocatorPart + "branch:(name:(value:(" + branchName + ")),policy:ALL_BRANCHES)", build);
    }

    {
      String branchName = "b(1)";
      BuildPromotion build = build20;
      checkBuilds(BuildPromotionFinder.getLocator(buildConf1, new BranchImpl(branchName), null), build);

      checkExceptionOnBuildsSearch(BadRequestException.class, "branch:(" + branchName + ")");
      checkBuilds("branch:((" + branchName + "))", build); //documenting current behavior
      checkExceptionOnBuildsSearch(BadRequestException.class, "branch:(name:(" + branchName + "))"); //documenting current behavior
      checkExceptionOnBuildsSearch(BadRequestException.class, "branch:(name:$base64:" + base64(branchName) + ")"); //documenting current behavior
      checkBuilds("branch:(name:(value:(" + branchName + ")))", build);
      checkBuilds("branch:(name:(value:$base64:" + base64(branchName) + "))", build);
      checkExceptionOnBuildsSearch(BadRequestException.class, "branch:($base64:" + base64("name:(" + branchName + ")") + ")"); //documenting current behavior
      checkBuilds("branch:($base64:" + base64("name:($base64:" + base64("value:($base64:" + base64(branchName) + ")") + ")") + ")", build); //documenting current behavior

      checkBuilds(altLocatorPart + "branch:(name:(value:(" + branchName + ")),policy:ALL_BRANCHES)", build);
    }

    {
      String branchName = "(b(1))";
      BuildPromotion build = build25;
      checkBuilds(BuildPromotionFinder.getLocator(buildConf1, new BranchImpl(branchName), null), build);

      checkBuilds("branch:(" + branchName + ")", build20); //documenting current behavior
      checkBuilds("branch:((" + branchName + "))", build); //documenting current behavior
      checkBuilds("branch:(name:(" + branchName + "))", build20); //documenting current behavior
      checkBuilds("branch:(name:((" + branchName + ")))", build); //documenting current behavior
      checkBuilds("branch:(name:$base64:" + base64(branchName) + ")", build20); //documenting current behavior
      checkBuilds("branch:(name:$base64:" + base64("(" + branchName + ")") + ")", build);
      checkBuilds("branch:(name:(value:(" + branchName + ")))", build);
      checkBuilds("branch:(name:(value:$base64:" + base64(branchName) + "))", build);
      checkBuilds("branch:($base64:" + base64("name:((" + branchName + "))") + ")", build); //documenting current behavior

      checkBuilds(altLocatorPart + "branch:(name:(value:(" + branchName + ")),policy:ALL_BRANCHES)", build);
    }

    {
      String branchName = "b(1)c";
      BuildPromotion build = build27;
      checkBuilds(BuildPromotionFinder.getLocator(buildConf1, new BranchImpl(branchName), null), build);

      checkExceptionOnBuildsSearch(LocatorProcessException.class, "branch:(" + branchName + ")");
      checkBuilds("branch:((" + branchName + "))", build); //documenting current behavior
      checkExceptionOnBuildsSearch(BadRequestException.class, "branch:(name:(" + branchName + "))"); //documenting current behavior
      checkBuilds("branch:(name:$base64:" + base64("(" + branchName + ")") + ")", build); //documenting current behavior
      checkBuilds("branch:(name:(value:(" + branchName + ")))", build);
      checkBuilds("branch:(name:(value:$base64:" + base64(branchName) + "))", build);

      checkBuilds(altLocatorPart + "branch:(name:(value:(" + branchName + ")),policy:ALL_BRANCHES)", build);
    }

    {
      String branchName = "b(1";
      BuildPromotion build = build30;
      checkBuilds(BuildPromotionFinder.getLocator(buildConf1, new BranchImpl(branchName), null), build);

      checkExceptionOnBuildsSearch(LocatorProcessException.class, "branch:(" + branchName + ")");
      checkExceptionOnBuildsSearch(LocatorProcessException.class, "branch:((" + branchName + "))");
      checkExceptionOnBuildsSearch(LocatorProcessException.class, "branch:(name:(" + branchName + "))");
      checkBuilds("branch:(name:$base64:" + base64(branchName) + ")", build);
      checkExceptionOnBuildsSearch(LocatorProcessException.class, "branch:(name:(value:(" + branchName + ")))");
      checkBuilds("branch:(name:(value:$base64:" + base64(branchName) + "))", build);
      checkExceptionOnBuildsSearch(LocatorProcessException.class,"branch:($base64:" + base64("name:(" + branchName + ")") + ")");

      checkBuilds(altLocatorPart + "branch:(name:(value:($base64:" + base64(branchName) + ")),policy:ALL_BRANCHES)", build);
    }
  }

  String base64(String text) {
    return new String(Base64.getEncoder().encode(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
  }

  @Test
  public void testBranchLocator() {
    BuildTypeEx buildType = createProject("p1").createBuildType("bt10", "bt10");
    assertEquals("a:b,branch:(name:(ignoreCase:false,matchType:equals,value:brnch)),buildType:(id:bt10)", BuildPromotionFinder.getLocator(buildType, new BranchImpl("brnch"), "a:b"));
    assertEquals("a:b,branch:(name:(ignoreCase:false,matchType:equals,value:(brnch(1)))),buildType:(id:bt10)", BuildPromotionFinder.getLocator(buildType, new BranchImpl("brnch(1)"), "a:b"));
    assertEquals("a:b,branch:(name:(ignoreCase:false,matchType:equals,value:($base64:" + base64("brnch(") + "))),buildType:(id:bt10)", BuildPromotionFinder.getLocator(buildType, new BranchImpl("brnch("), "a:b"));
  }

  @Test
  public void testBranchDimensionWithSeveralRoots() throws ExecutionException, InterruptedException {
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();
    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    SVcsRoot vcsRoot10 = buildConf2.getProject().createVcsRoot("vcs", "extId10", "name10");
    buildConf2.addVcsRoot(vcsRoot10);
    SVcsRoot vcsRoot20 = buildConf2.getProject().createVcsRoot("vcs", "extId20", "name20");
    buildConf2.addVcsRoot(vcsRoot20);

    final VcsRootInstance vcsRootInstance10 = buildConf2.getVcsRootInstanceForParent(vcsRoot10);
    assert vcsRootInstance10 != null;
    final VcsRootInstance vcsRootInstance20 = buildConf2.getVcsRootInstanceForParent(vcsRoot20);
    assert vcsRootInstance20 != null;

    collectChangesPolicy.setCurrentState(vcsRootInstance10, createVersionState("refs/heads/master", map("refs/heads/master", "a1",
                                                                                                        "refs/heads/branch1", "a2",
                                                                                                        "refs/heads/branch2", "a3")));
    collectChangesPolicy.setCurrentState(vcsRootInstance20, createVersionState("refs/heads/master", map("refs/heads/master", "b1",
                                                                                                        "refs/heads/branch1", "b2",
                                                                                                        "refs/heads/branch2", "b3")));
    setBranchSpec(vcsRootInstance10, "+:refs/heads/(*)");
    setBranchSpec(vcsRootInstance20, "+:refs/heads/(*)");

    myFixture.getVcsModificationChecker().checkForModifications(buildConf2.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    final BuildPromotion build30 = build().in(buildConf2).finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf2).withDefaultBranch().finish().getBuildPromotion();
    final BuildPromotion build50 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion(); //not existing branch
    final BuildPromotion build60 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build70 = build().in(buildConf2).withBranch("master").finish().getBuildPromotion(); //not existing branch

    checkBuilds(null, build40, build30);
    checkBuilds("branch:(default:any)", build70, build60, build50, build40, build30);
    checkBuilds("branch:(name:<default>)", build40, build30);
    checkBuilds("buildType:(id:" + buildConf2.getExternalId() + "),branch:(name:<default>)", build40, build30);
    checkBuilds("branch:(name:master)", build70, build40, build30);
    checkBuilds("branch:(name:master,default:true)", build40, build30);
    checkBuilds("branch:(name:master,default:false)", build70);
    checkBuilds("buildType:(id:" + buildConf2.getExternalId() + "),branch:(name:master)", build70, build40, build30);
    checkBuilds("branch:(name:branch1)", build60);


    final BuildTypeImpl buildConf3 = registerBuildType("buildConf3", "project");

    SVcsRoot vcsRoot30 = buildConf3.getProject().createVcsRoot("vcs", "extId30", "name30");
    buildConf3.addVcsRoot(vcsRoot30);
    SVcsRoot vcsRoot40 = buildConf3.getProject().createVcsRoot("vcs", "extId40", "name40");
    buildConf3.addVcsRoot(vcsRoot40);

    final VcsRootInstance vcsRootInstance30 = buildConf3.getVcsRootInstanceForParent(vcsRoot30);
    assert vcsRootInstance30 != null;
    final VcsRootInstance vcsRootInstance40 = buildConf3.getVcsRootInstanceForParent(vcsRoot40);
    assert vcsRootInstance40 != null;

    collectChangesPolicy.setCurrentState(vcsRootInstance30, createVersionState("refs/heads/master", map("refs/heads/master", "a1",
                                                                                                        "refs/heads/branch1", "a2",
                                                                                                        "refs/heads/branch2", "a3")));
    collectChangesPolicy.setCurrentState(vcsRootInstance40, createVersionState("refs/heads/branch1", map("refs/heads/master", "b1",
                                                                                                         "refs/heads/branch1", "b2",
                                                                                                         "refs/heads/branch2", "b3"))); //different default branch
    setBranchSpec(vcsRootInstance30, "+:refs/heads/(*)");
    setBranchSpec(vcsRootInstance40, "+:refs/heads/(*)");

    myFixture.getVcsModificationChecker().checkForModifications(buildConf3.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    final BuildPromotion build230 = build().in(buildConf3).finish().getBuildPromotion();
    final BuildPromotion build240 = build().in(buildConf3).withDefaultBranch().finish().getBuildPromotion();
    final BuildPromotion build250 = build().in(buildConf3).withBranch("branch").finish().getBuildPromotion(); //not existing branch
    final BuildPromotion build260 = build().in(buildConf3).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build270 = build().in(buildConf3).withBranch("master").finish().getBuildPromotion(); //not existing branch

    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + ")", build240, build230);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(default:any)", build270, build260, build250, build240, build230);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:<default>)", build240, build230);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:<Default>)", build240, build230);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:(value:<default>))", build240, build230);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:(value:<Default>))"); //case sensitive in this syntax
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:master)", build270);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:Master)", build270);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:master,default:true)");
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:master,default:false)", build270);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:(value:master))", build270);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:(value:Master))");
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:branch)", build250);
    checkBuilds("buildType:(id:" + buildConf3.getExternalId() + "),branch:(name:branch1)", build260);
  }

  @Test
  public void testOrderDimension() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final BuildPromotion build01 = build().in(buildConf).finish().getBuildPromotion();
    final BuildPromotion build1 = build().in(buildConf2).finish().getBuildPromotion();
    final BuildPromotion build02 = build().in(buildConf).failed().finish().getBuildPromotion();
    final BuildPromotion build2 = build().in(buildConf2).failed().finish().getBuildPromotion();

    final RunningBuildEx running3 = build().in(buildConf2).run();
    running3.stop(getOrCreateUser("user1"), "cancel comment");
    final BuildPromotion build3 = finishBuild(running3, true).getBuildPromotion();

    final BuildPromotion build4 = build().in(buildConf2).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build5 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build6 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build7 = build().in(buildConf2).withBranch("branch2").finish().getBuildPromotion();
    final BuildPromotion build8 = build().in(buildConf2).finish().getBuildPromotion();
    final BuildPromotion build9 = build().in(buildConf2).withBranch("branch2").finish().getBuildPromotion();
    final BuildPromotion build10 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();

    final RunningBuildEx running11 = build().in(buildConf2).withBranch("branch").run();
    running11.stop(getOrCreateUser("user1"), "cancel comment");
    final BuildPromotion build11 = finishBuild(running11, true).getBuildPromotion();

    final BuildPromotion runningBuild5 = build().in(buildConf2).withBranch("branch").run().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf2).withBranch("branch").addToQueue().getBuildPromotion();

    checkBuilds("ordered:(from:(id:" + build4.getId() + "))", build8);
    checkBuilds("ordered:(to:(id:" + build4.getId() + "))", build2, build1);
    checkBuilds("ordered:(to:(id:" + build4.getId() + ")),defaultFilter:false", build3, build2, build1);
    checkBuilds("ordered:(from:(id:" + build6.getId() + ")),defaultFilter:false", build10, build11, runningBuild5);
    checkBuilds("ordered:(to:(id:" + build6.getId() + "))", build5, build2, build1);
    checkBuilds("ordered:(to:(id:" + build6.getId() + ")),defaultFilter:false", build5, build4, build3, build2, build1);
    checkBuilds("ordered:(to:(id:" + runningBuild5.getId() + "))", build10, build6, build5, build2, build1);
    checkBuilds("ordered:(to:(id:" + build7.getId() + ")),equivalent:(id:" + build5.getId() + ")", build2, build1);
    checkBuilds("ordered:(to:(id:" + build10.getId() + "))", build6, build5, build2, build1);
    checkBuilds("ordered:(to:(id:" + build10.getId() + ")),equivalent:(id:" + build5.getId() + ")", build6, build2, build1);
  }

  @Test
  public void testItemDimension() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).parameter("a", "10").parameter("b", "10").parameter("aa", "15").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).failed().parameter("b", "20").finish().getBuildPromotion();

    checkBuilds("item:(id:" + build10.getId() + ")", build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(id:" + build10.getId() + ")", build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(id:" + build10.getId() + "),unique:false", build10, build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(id:" + build10.getId() + "),unique:true", build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(id:" + build20.getId() + ")", build10, build20);
    checkBuilds("item:(id:" + build20.getId() + "),item:(id:" + build10.getId() + ")", build20, build10);
    checkBuilds("item:(id:" + build20.getId() + "),item:(id:" + build10.getId() + "),count:1", build20);
    checkBuilds("item:(id:" + build20.getId() + "),item:(id:" + build10.getId() + "),start:1", build10);
    checkBuilds("item:(status:SUCCESS),item:(status:FAILURE)", build20, build10, build30);
    checkBuilds("item:(status:SUCCESS),item:(status:FAILURE),item:(id:" + build10.getId() + "),unique:true", build20, build10, build30);

    /* todo: improve performance of count and item processing
    checkBuilds("count:1,item:(id:" + build20.getId() + "),item:(id:" + build10.getId() + ")", 1, new BuildPromotion[]{build20});
    checkBuilds("count:1,item:(parameter:(b)),item:(id:" + build10.getId() + ")", 1, new BuildPromotion[]{build20});
    checkBuilds("count:2,item:(id:" + build10.getId() + "),item:(parameter:(b))", 2, new BuildPromotion[]{build10, build20});
    */
  }

  @Test
  public void testLogicDimensions() throws IOException {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).failed().finish().getBuildPromotion();
    writeFinalParameters(build20.getAssociatedBuild(), map("a", "10", "b", "10", "aa", "15"));

    final BuildPromotion build30 = build().in(buildConf1).failed().finish().getBuildPromotion();
    writeFinalParameters(build30.getAssociatedBuild(), map("a", "20"));

    final BuildPromotion build40 = build().in(buildConf1).finish().getBuildPromotion();
    writeFinalParameters(build40.getAssociatedBuild(), map("a", "30", "b", "20"));

    checkBuilds(null, build40, build30, build20, build10);

    checkBuilds("id:" + build10.getId(), build10);
    checkBuilds("or:(id:" + build10.getId() + ")", build10);
    checkBuilds("and:(id:" + build10.getId() + ")", build10);
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "id:" + build10.getId() + ",id:" + build20.getId());
    check("id:" + build10.getId() + ",taskId:" + build20.getId());
    checkBuilds("and:(id:" + build10.getId() + ",taskId:" + build20.getId() + ")");
    checkBuilds("or:(id:" + build10.getId() + ",id:" + build20.getId() + ")", build20, build10);
    checkBuilds("or:(id:" + build20.getId() + ",id:" + build10.getId() + ")", build20, build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(" + build20.getId() + ")", build10, build20);
    checkBuilds("item:(id:" + build20.getId() + "),item:(" + build10.getId() + ")", build20, build10);
    checkBuilds("or:(id:" + build20.getId() + ",id:" + build10.getId() + "),id:" + build20.getId(), build20);
    checkBuilds("and:(or:(id:" + build20.getId() + "),id:" + build10.getId() + ")");
    checkBuilds("or:(id:" + build20.getId() + "),id:" + build10.getId());
    checkBuilds("or:(id:" + build20.getId() + "),id:" + build20.getId(), build20);
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "and:(or:(id:" + build20.getId() + "),or:(id:" + build10.getId() + "))"); //multiple ORs are not supported so far
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "or:(id:" + build20.getId() + "),or:(id:" + build10.getId() + ")"); //multiple ORs are not supported so far
//    checkBuilds("and:(or(id:" + build20.getId() + ",id:" + build10.getId() + "),or(id:" + build30.getId() + ",id:" + build20.getId() + "))", build20);

    checkBuilds("status:FAILURE", build30, build20);
    checkBuilds("property:(name:b)", build40, build20);
    checkBuilds("property:(name:a)", build40, build30, build20);
    checkBuilds("or:(id:" + build30.getId() + ",id:" + build20.getId() + "),property:(name:a)", build30, build20);
    checkBuilds("or:(id:" + build30.getId() + ",id:" + build20.getId() + "),property:(name:a),start:1", build20);
    checkBuilds("or:(id:" + build20.getId() + ",id:" + build10.getId() + "),id:" + build20.getId() + ",unique:false", build20);

    checkBuilds("or:(id:" + build10.getId() + ",id:" + build10.getId() + ")", build10);
    checkBuilds("item:(status:SUCCESS),item:(status:FAILURE)", build40, build10, build30, build20);
    checkBuilds("or:(property:(name:b)),item:(status:FAILURE)", build20);
    checkBuilds("property:(name:b),status:FAILURE", build20);
    checkBuilds("property:(name:b),item:(status:FAILURE)", build20);
    checkBuilds("or:(property:(name:b),status:FAILURE)", build40, build30, build20);
    checkBuilds("not:(status:FAILURE)", build40, build10);
    checkBuilds("property:(name:b),not:(status:FAILURE)", build40);
    checkBuilds("status:FAILURE,not:(status:FAILURE)");

    checkBuilds("status:FAILURE,start:1", build20);
    checkMultipleBuilds("item:(status:FAILURE),property:(name:a,value:10)", build20); //search by item, filter by top-level
    checkBuilds("item:(status:FAILURE,start:1),property:(name:a,value:10)", build20); //search by item, filter by top-level
    checkBuilds("or:(status:FAILURE),property:(name:a,value:10)", build20); //search by top level, filter by or
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "or:(status:FAILURE,start:1),property:(name:a,value:10)"); //start in logic filtering is not supported
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "and:(status:FAILURE,start:1),property:(name:a,value:10)"); //start in logic filtering is not supported
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "not:(status:FAILURE,start:1),property:(name:a,value:10)"); //start in logic filtering is not supported

    assertEquals(Long.valueOf(1), checkMultipleBuilds("item:(id:" + build20.getId() + "),status:FAILURE", build20).getActuallyProcessedCount());
    assertEquals(Long.valueOf(4), checkMultipleBuilds("or:(id:" + build20.getId() + "),status:FAILURE", build20).getActuallyProcessedCount());
  }

  @Test
  public void testStrobDimension() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");
    final BuildTypeEx buildConf2 = (BuildTypeEx)project.createBuildType("buildConf2", "buildConf2");
    project.createBuildTypeTemplate("template1", "template1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf2).failed().finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).failed().finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf2).finish().getBuildPromotion();

    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")))", build30, build40);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),locator:(count:10))", build30, build15, build10, build40, build20);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),locator:(status:SUCCESS))", build15, build40);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),locator:(status:SUCCESS,count:10))", build15, build10, build40);

    ((ProjectImpl)project).setOwnBuildTypesOrder(Arrays.asList(buildConf2.getId(), buildConf1.getId()));
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")))", build40, build30);
  }

  @Test
  public void testStrobBranchedDimension() throws ExecutionException, InterruptedException {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");
    final BuildTypeEx buildConf2 = (BuildTypeEx)project.createBuildType("buildConf2", "buildConf2");

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    buildConf1.addVcsRoot(buildConf1.getProject().createVcsRoot("vcs", "extId", "name"));

    final VcsRootInstance vcsRootInstance = buildConf1.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");

    myFixture.getVcsModificationChecker().checkForModifications(buildConf1.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf1).withDefaultBranch().finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build25 = build().in(buildConf1).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build35 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf1).finish().getBuildPromotion();

    final BuildPromotion build50 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();

    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")))", build40);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),branch:(policy:ALL_BRANCHES))", build40, build35, build25, build60);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),branch:(policy:VCS_BRANCHES))", build40, build25);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),branch:(default:any))", build40, build35, build25, build60);
    checkBuilds("strob:(branch:(default:any,buildType:(id:" + buildConf1.getExternalId() + ")),locator:(buildType:(id:" + buildConf1.getExternalId() + ")))", build40, build35, build25);
    checkExceptionOnBuildsSearch(BadRequestException.class, "strob:(branch:(default:any),locator:(buildType:(id:" + buildConf1.getExternalId() + ")))");

    checkBuilds("strob:(branch:(default:any,buildType:(id:" + buildConf2.getExternalId() + ")),locator:(buildType:(id:" + buildConf1.getExternalId() + ")))", build40, build25);
  }

  @Test
  public void testDefaultsLegacy() {
    setInternalProperty("rest.buildPromotionFinder.varyingDefaults", "false"); //turn on pre-TeamCity 10 mode
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");
    SUser user1 = createUser("user1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf1).personalForUser("user1").finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).cancel(user1).getBuildPromotion();
    SFinishedBuild finishedBuild40 = build().in(buildConf1).withBranch("branch1").tag("tag").finish();
    finishedBuild40.setPinned(true, null, null);
    final BuildPromotion build40 = finishedBuild40.getBuildPromotion();
    final BuildPromotion build45 = build().in(buildConf1).withBranch("branch1").personalForUser("user1").finish().getBuildPromotion();
    final BuildPromotion build50 = build().in(buildConf1).withBranch("branch1").failedToStart().by(user1).finish().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf1).withBranch("branch1").cancel(user1).getBuildPromotion();

    final BuildPromotion build100 = build().in(buildConf1).run().getBuildPromotion();
    myFixture.createEnabledAgent("agent2", "runner1");
    myFixture.createEnabledAgent("agent3", "runner1");
    final BuildPromotion build110 = build().in(buildConf1).withBranch("branch1").by(user1).run().getBuildPromotion();
    final BuildPromotion build120 = build().in(buildConf1).withBranch("branch1").personalForUser("user1").failedToStart().tag("tag").run().getBuildPromotion();

    final BuildPromotion build130 = build().in(buildConf1).addToQueue().getBuildPromotion();
    final BuildPromotion build140 = build().in(buildConf1).withBranch("branch1").addToQueue().getBuildPromotion();
    final BuildPromotion build150 = build().in(buildConf1).withBranch("branch1").personalForUser("user1").addToQueue().getBuildPromotion();

    check("defaultFilter:false", build130, build140, build150, build120, build110, build100, build60, build50, build45, build40, build30, build20, build15, build10);
    check(null, build10);
    check("state:finished", build10);
    check("state:(finished:true)", build10);
    check("running:false", build10);
//bug here with state processing    check("state:(finished:false)", build130, build140, build150, build120, build110, build100);
//bug here with state processing    check("state:(finished:any)", build130, build140, build150, build120, build110, build100, build60, build50, build45, build40, build30, build20, build15, build10);
    check("state:(finished:true,running:true)", build100, build10);
    check("state:(finished:true,queued:true)", build130, build10);
    check("running:true", build100);
    check("state:(running)", build100);
    check("state:(running:true)", build100);

    check("branch:(branch1)", build40);
    check("branch:(branch1),running:true", build110);

    check("agent:(id:" + build100.getAssociatedBuild().getAgent().getId() + ")", build10);
    check("agent:(id:" + build110.getAssociatedBuild().getAgent().getId() + ")");
    check("agent:(id:" + build100.getAssociatedBuild().getAgent().getId() + "),running:any", build100, build10);
    check("agent:(id:" + build100.getAssociatedBuild().getAgent().getId() + "),running:true", build100);
    check("agent:(id:" + build110.getAssociatedBuild().getAgent().getId() + "),running:true");
    check("agent:(id:" + build120.getAssociatedBuild().getAgent().getId() + "),running:true");
    check("agentName:" + build120.getAssociatedBuild().getAgent().getName() + ",running:true");

    check("user:id:" + user1.getId());
    check("user:id:" + user1.getId() + ",state:any");

    check("id:" + build50.getId(), build50);
    check("id:" + build60.getId(), build60);
    check("id:" + build120.getId(), build120);
    check("id:" + build120.getId(), build120);

    check("state:queued", build130);
    check("state:(queued:true,running:true)", build130, build100);

    check("buildType:" + buildConf1.getExternalId(), build10);
    check("buildType:" + buildConf1.getExternalId() + ",running:any", build100, build10);
//bug here with state processing    check("buildType:" + buildConf1.getExternalId() + ",state:(queued:any)", build130, build100, build10);
    check("buildType:" + buildConf1.getExternalId() + ",state:(queued:true,running:true,finished:true)", build130, build100, build10);
    check("tag:tag");
    check("tag:tag,running:true");
    check("pinned:true");
  }

  @Test
  public void testAgentFilteringAfterNameChange() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildPromotion build1 = build().in(buildConf).finish().getBuildPromotion();

    final MockBuildAgent agent = myFixture.createEnabledAgent("originalName", "smth");
    registerAndEnableAgent(agent);

    final BuildPromotion build2 = build().in(buildConf).on(agent).failed().finish().getBuildPromotion();

    final MockBuildAgent agent2 = myFixture.createEnabledAgent("smth2");
    registerAndEnableAgent(agent2);
    final BuildPromotion build3 = build().in(buildConf).on(agent2).failed().finish().getBuildPromotion();

    final BuildPromotion build4 = build().in(buildConf).on(agent2).failed().finish().getBuildPromotion();
    final BuildPromotion build5 = build().in(buildConf).number("10").on(agent).failed().finish().getBuildPromotion();
    final BuildPromotion build6 = build().in(buildConf).on(myBuildAgent).finish().getBuildPromotion();
    final BuildPromotion build7 = build().in(buildConf).on(agent).failed().finish().getBuildPromotion();
    final BuildPromotion build8 = build().in(buildConf).on(myBuildAgent).finish().getBuildPromotion();
    final BuildPromotion build9 = build().in(buildConf).on(agent2).failed().finish().getBuildPromotion();

    final BuildPromotion build25 = build().in(buildConf).on(agent2).run().getBuildPromotion();

    final BuildPromotion build30 = build().in(buildConf).on(agent).addToQueue().getBuildPromotion();
    final BuildPromotion build35 = build().in(buildConf).on(agent2).addToQueue().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf).on(agent).parameter("a", "prevent reuse 40").addToQueue().getBuildPromotion();

    check("agent:(name:" + agent.getName() + "),defaultFilter:false", build30, build40, build7, build5, build2);
    check("agentName:" + agent.getName() + ",defaultFilter:false", build30, build40, build7, build5, build2);

    check("number:10,buildType:(id:" + buildConf.getExternalId()+ "),agent:(id:"+ agent.getId() + ")", build5);
    check("number:10,buildType:(id:" + buildConf.getExternalId()+ "),agentName:"+ agent.getName(), build5);
    check("number:10,buildType:(id:" + buildConf.getExternalId()+ "),agentTypeId:"+ agent.getAgentTypeId(), build5);


    unregisterAgent(agent.getId());

    MockBuildAgent agentReplacement = myFixture.createMockBuildAgent("smth");
    agentReplacement.setName("newName");
    agentReplacement.setPort(9081);
    agentReplacement.setId(agent.getId());
    agentReplacement.setAuthorizationToken(agent.getAuthorizationToken());
    myFixture.getBuildAgentManager().registerAgent(agentReplacement, -1);


    assertEquals(agent.getId(), agentReplacement.getId());
    assertEquals(agent.getAgentTypeId(), agentReplacement.getAgentTypeId());
    assertTrue(agentReplacement.isAuthorized());

    check("agent:(id:" + agentReplacement.getId() + "),defaultFilter:false", build30, build40, build7, build5, build2);
    check("agent:(typeId:" + agentReplacement.getAgentTypeId() + "),defaultFilter:false", build30, build40, build7, build5, build2);
    check("agentTypeId:" + agentReplacement.getAgentTypeId() + ",defaultFilter:false", build30, build40, build7, build5, build2);

    check("agent:(name:" + agentReplacement.getName() + "),defaultFilter:false", build30, build40, build7, build5, build2);
    check("agentName:" + agentReplacement.getName() + ",defaultFilter:false", build30, build40); //only queued, finished use another agent name
    check("agent:(name:" + agentReplacement.getName() + ",authorized:true),defaultFilter:false", build30, build40, build7, build5, build2); //not only name is specified: search by actual agent
    checkExceptionOnBuildsSearch(NotFoundException.class, "agent:(name:" + agent.getName() + "),defaultFilter:false"); //agnet is not existent anymore
    check("agentName:" + agent.getName() + ",defaultFilter:false", build7, build5, build2); //can still find finished builds by the name of the agent they were run on


    unregisterAgent(agentReplacement.getId());
    agentReplacement.setAuthorized(false, null, "");
    myAgentManager.removeAgent(agentReplacement, null);

    checkExceptionOnBuildsSearch(NotFoundException.class, "agent:(id:" + agentReplacement.getId() + "),defaultFilter:false"); //No agents are found by locator 'id:2'
    checkExceptionOnBuildsSearch(NotFoundException.class, "agent:(typeId:" + agentReplacement.getAgentTypeId() + "),defaultFilter:false");
    check("agentTypeId:" + agentReplacement.getAgentTypeId() + ",defaultFilter:false", build7, build5, build2);

    checkExceptionOnBuildsSearch(NotFoundException.class,"agent:(name:" + agentReplacement.getName() + "),defaultFilter:false");
    checkExceptionOnBuildsSearch(NotFoundException.class,"agent:(name:" + agent.getName() + "),defaultFilter:false");
    check("agentName:" + agentReplacement.getName() + ",defaultFilter:false");
    check("agentName:" + agent.getName() + ",defaultFilter:false", build7, build5, build2);

    check("agentTypeId:" + agentReplacement.getAgentTypeId() + ",agentName:" + agentReplacement.getName() + ",defaultFilter:false");
    check("agentTypeId:" + agentReplacement.getAgentTypeId() + ",agentName:" + agent.getName() + ",defaultFilter:false", build7, build5, build2);

    check("agentTypeId:" + 1000);
    check("agentName:" + "abra");
  }

  @Test
  public void testDefaults() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");
    SUser user1 = createUser("user1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf1).personalForUser("user1").finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).cancel(user1).getBuildPromotion();
    SFinishedBuild finishedBuild40 = build().in(buildConf1).withBranch("branch1").tag("tag").finish();
    finishedBuild40.setPinned(true, null, null);
    final BuildPromotion build40 = finishedBuild40.getBuildPromotion();
    final BuildPromotion build45 = build().in(buildConf1).withBranch("branch1").personalForUser("user1").finish().getBuildPromotion();
    final BuildPromotion build50 = build().in(buildConf1).withBranch("branch1").failedToStart().by(user1).finish().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf1).withBranch("branch1").cancel(user1).getBuildPromotion();

    final BuildPromotion build100 = build().in(buildConf1).run().getBuildPromotion();
    myFixture.createEnabledAgent("agent2", "runner1");
    myFixture.createEnabledAgent("agent3", "runner1");
    final BuildPromotion build110 = build().in(buildConf1).withBranch("branch1").by(user1).run().getBuildPromotion();
    final BuildPromotion build120 = build().in(buildConf1).withBranch("branch1").personalForUser("user1").failedToStart().tag("tag").run().getBuildPromotion();

    final BuildPromotion build130 = build().in(buildConf1).addToQueue().getBuildPromotion();
    final BuildPromotion build140 = build().in(buildConf1).withBranch("branch1").addToQueue().getBuildPromotion();
    final BuildPromotion build150 = build().in(buildConf1).withBranch("branch1").personalForUser("user1").addToQueue().getBuildPromotion();

    check("defaultFilter:false", build130, build140, build150, build120, build110, build100, build60, build50, build45, build40, build30, build20, build15, build10);
    check(null, build10);
    check("state:finished", build10);
    check("state:(finished:true)", build10);
    check("running:false", build10);
//bug here with state processing    check("state:(finished:false)", build130, build140, build150, build120, build110, build100);
//bug here with state processing    check("state:(finished:any)", build130, build140, build150, build120, build110, build100, build60, build50, build45, build40, build30, build20, build15, build10);
    check("state:(finished:true,running:true)", build100, build10);
    check("state:(finished:true,running:true),defaultFilter:false", build120, build110, build100, build60, build50, build45, build40, build30, build20, build15, build10);
    check("state:(finished:true,running:true),defaultFilter:true", build100, build10);
    check("state:(finished:true,queued:true)", build130, build10);
    check("running:true,defaultFilter:true", build100);
    check("running:true", build120, build110, build100);
    check("state:(running)", build120, build110, build100);
    check("state:(running:true)", build120, build110, build100);

    check("branch:(branch1)", build40);
    check("branch:(branch1),running:true", build120, build110);

    check("agent:(id:" + build100.getAssociatedBuild().getAgent().getId() + ")", build60, build50, build45, build40, build30, build20, build15, build10);
    check("agent:(id:" + build110.getAssociatedBuild().getAgent().getId() + ")");
    check("agent:(id:" + build100.getAssociatedBuild().getAgent().getId() + "),running:any", build100, build60, build50, build45, build40, build30, build20, build15, build10);
    check("agent:(id:" + build100.getAssociatedBuild().getAgent().getId() + "),running:true", build100);
    check("agent:(id:" + build110.getAssociatedBuild().getAgent().getId() + "),running:true", build110);
    check("agent:(id:" + build120.getAssociatedBuild().getAgent().getId() + "),running:true", build120);
    check("agentName:" + build120.getAssociatedBuild().getAgent().getName() + ",running:true", build120);

    check("user:id:" + user1.getId(), build50, build45, build15);
    check("user:id:" + user1.getId() + ",state:any", build150, build120, build110, build50, build45, build15);

    check("triggered:(user:id:" + user1.getId() + ")", build50);
    check("triggered:(user:id:" + user1.getId() + "),state:any", build110, build50);

    check("id:" + build50.getId(), build50);
    check("id:" + build60.getId(), build60);
    check("id:" + build120.getId(), build120);
    check("id:" + build150.getId(), build150);

    check("state:queued", build130, build140, build150);
    check("state:(queued:true,running:true)", build130, build140, build150, build120, build110, build100);

    check("buildType:" + buildConf1.getExternalId(), build10);
    check("buildType:" + buildConf1.getExternalId() + ",running:any", build100, build10);
    check("buildType:" + buildConf1.getExternalId() + ",running:any,defaultFilter:false", build120, build110, build100, build60, build50, build45, build40, build30, build20, build15, build10);
//bug here with state processing    check("buildType:" + buildConf1.getExternalId() + ",state:(queued:any)", build130, build100, build10);
    check("buildType:" + buildConf1.getExternalId() + ",state:(queued:true,running:true,finished:true)", build130, build100, build10);
    check("buildType:" + buildConf1.getExternalId() + ",state:(queued:true,running:true,finished:true),defaultFilter:false", build130, build140, build150, build120,
          build110, build100, build60, build50, build45, build40, build30, build20, build15, build10);
    check("tag:tag");
    check("tag:tag,running:true", build120);
    check("pinned:true");
  }

  @Test
  public void testMultipleTags() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).tag("tag1").tag("tag2").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).tag("tag1").tag("tag3").finish().getBuildPromotion();

    check(null, build30, build20, build10);
    check("tag:tag1", build30, build20);
    check("tag:tag1,tag:tag2", build20);
    check("tag:(name:tag1),tag:tag2", build20);
    check("tag:tag1,tag:tag1", build30, build20);
    check("or(tag:tag2,tag:tag3)", build30, build20);
  }

  @Test
  public void testCaseInTags() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).tag("aaa").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).tag("aAa").finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf1).tag("some").finish().getBuildPromotion();
    build40.setPrivateTags(Collections.singletonList("aaa"), getOrCreateUser("user1"));

    check(null, build40, build30, build20, build10);
    //this is case sensitive
    check("tag:aaa", build20);
    check("tag:aAa", build30);
    check("tag:AAA");

    //this is case-insensitive
    check("tag:(name:aaa)", build30, build20);
    check("tag:(name:aAa)", build30, build20);
    check("tag:(name:AAA)", build30, build20);
    
    check("tag:(condition:(value:aaa,matchType:(equals)))", build20);
    check("tag:(condition:(value:aaa,matchType:(equals),ignoreCase:false))", build20);
    check("tag:(condition:(value:aaa,matchType:(equals),ignoreCase:true))", build30, build20);

    check("tag:(name:aaa,private:true)", build40);
    check("tag:(name:aaa,private:false)", build30, build20);
    check("tag:(name:aaa,private:any)", build40, build30, build20);
    check("tag:(name:aaa,owner:(user1))"); //might be worth switching to private tags search, but this is the current behavior
    check("tag:(name:aaa,owner:(user1),private:true)", build40);
    check("tag:(name:aaa,owner:(user1),private:false)"); //might be worth reporting an error
    check("tag:(name:aaa,owner:(user1),private:any)", build40, build30, build20);

    check("tag:(private:false,condition:(value:aaa,matchType:(equals),ignoreCase:false))", build20);
    check("tag:(private:true,owner:(user1),condition:(value:aaa,matchType:(equals),ignoreCase:false))", build40);

    check("tag:(private:any,condition:(value:aaa,matchType:(equals),ignoreCase:false))", build40, build20);
    check("tag:(private:any,owner:(user1),condition:(value:aaa,matchType:(equals),ignoreCase:false))", build40, build20);

    getOrCreateUser("user2");
    check("tag:(private:any,owner:(user2),condition:(value:aaa,matchType:(equals),ignoreCase:false))", build20);

    //is effective, does not scan all the builds
    checkCounts("tag:aaa", 1, 1);
    checkCounts("tag:(condition:(value:aaa,matchType:(equals),ignoreCase:false))", 1, 1);
    checkCounts("tag:(private:false,condition:(value:aaa,matchType:(equals),ignoreCase:false))", 1, 1);
    checkCounts("tag:(private:true,owner:(user1),condition:(value:aaa,matchType:(equals),ignoreCase:false))", 1, 1);
  }

  @Test
  public void testPivateTags() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).tag("aaa").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).tag("aAa").finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf1).finish().getBuildPromotion();
    SUser user1 = getOrCreateUser("user1");
    build40.setPrivateTags(Collections.singletonList("bbb"), user1);

    final BuildPromotion build45 = build().in(buildConf1).finish().getBuildPromotion();
    build45.setPrivateTags(Collections.singletonList("aAa"), user1);

    final BuildPromotion build50 = build().in(buildConf1).finish().getBuildPromotion();
    SUser user2 = getOrCreateUser("user2");
    build50.setPrivateTags(Collections.singletonList("bbb"), user2);

    check(null, build50, build45, build40, build30, build20, build10);

    String userFilter = ",owner:(id:" + user1.getId() + ")";

    check("tag:(name:aaa,private:true)", build45);
    check("tag:(name:aaa,private:false)", build30, build20);
    check("tag:(name:aaa,private:any)", build45, build30, build20);
    check("tag:(name:aaa" + userFilter + ")"); //might be worth switching to private tags search, but this is the current behavior
    check("tag:(name:aaa" + userFilter + ",private:true)", build45);
    check("tag:(name:aaa" + userFilter + ",private:false)"); //might be worth reporting an error
    check("tag:(name:aaa" + userFilter + ",private:any)", build45, build30, build20);

    check("tag:(private:any)", build50, build45, build40, build30, build20);
    check("tag:(private:any" + userFilter + ")", build45, build40, build30, build20);
    check("tag:(" + userFilter.substring(1) + ")"); //might be worth switching to private tags search, but this is the current behavior
    check("tag:(private:true" + userFilter + ")", build45, build40);
    check("tag:(private:false" + userFilter + ")"); //might be worth reporting an error
  }

  @Test
  public void testEffectiveTagsSearch() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");
    final BuildTypeEx buildConf2 = (BuildTypeEx)project.createBuildType("buildConf2", "buildConf2");
    SUser user = createUser("user1");

    build().in(buildConf2).finish().getBuildPromotion();
    List<BuildPromotion> builds = IntStream.range(0, 10).mapToObj(i -> build().in(buildConf1).finish().getBuildPromotion()).collect(Collectors.toList());
    build().in(buildConf2).finish().getBuildPromotion().setTags(Arrays.asList("a"));

    builds.get(3).setTags(Arrays.asList("a", "b"));
    builds.get(5).setPrivateTags(Arrays.asList("a"), user);
    builds.get(7).setTags(Arrays.asList("a"));

    checkCounts("tag:a", 3, 3);
    checkCounts("tag:c", 0, 0);
    String bt = ",buildType:(id:" + buildConf1.getExternalId() + ")";

    // We exepect finder to get prefiltered items using build type, so maxProcessedCount = 2
    setInternalProperty("rest.request.builds.prefilterByTag", "false");
    checkCounts("tag:a" + bt, 2, 2);

    // We exepect finder to get prefiltered items using exact tag value.
    // This means that we filter by build type after, which cases 1 extra build to be checked, so maxProcessedCount = 3
    setInternalProperty("rest.request.builds.prefilterByTag", "true");
    checkCounts("tag:a" + bt, 2, 3);

    String userFilter = ",owner:(id:" + user.getId() + ")";
    checkCounts("tag:(condition:(value:a,matchType:(equals),ignoreCase:false),private:true" + userFilter + ")", 1, 1);
    checkCounts("tag:(condition:(value:a,matchType:(equals),ignoreCase:false),private:true" + userFilter + ")" + bt, 1, 1);
  }

  @Test
  public void testEffectiveTagsSearch2() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildType = (BuildTypeEx) project.createBuildType("buildConf1", "buildConf1");

    // One finished build without tags
    build().in(buildType).finish();

    // One finished build with tags
    BuildPromotion finishedBuild = build().in(buildType).finish().getBuildPromotion();
    finishedBuild.setTags(Arrays.asList("a"));

    // One running build without tags
    createRunningBuild(buildType, new String[]{}, new String[]{});

    // One queued build without tags
    addToQueue(buildType);

    // When disabled, we exepect BuildPromotionFinder to filter through queue and running builds,
    // without prefiltering by tag.
    setInternalProperty("rest.request.builds.prefilterByTag", "false");
    checkCounts("state:any,tag:a", 1, 3);

    // When enabled, we exepect BuildPromotionFinder to prefilter through queue and running builds,
    // so that they do not propagate into into final filtering.
    setInternalProperty("rest.request.builds.prefilterByTag", "true");
    checkCounts("state:any,tag:a", 1,  1);

    finishAllBuilds();
  }

  @Test
  public void testTagWithSpecialCharacters() throws UnsupportedEncodingException {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).tag("text(aaa)").finish().getBuildPromotion();

    check("tag:(name:$base64:" + Base64.getEncoder().encodeToString("text(aaa)".getBytes("UTF-8")) + ")", build20);
    check("tag:(name:(text(aaa)))", build20);
    checkExceptionOnItemsSearch(LocatorProcessException.class, "tag:(text(aaa))"); //documenting current beahvior. Fixing this is hard as
  }

  @Test
  public void testUnusedLocatorDimensionLogging() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();

    final TestingLogAppender testAppender = new TestingLogAppender("testAppender");

    LogInitializer.reconfigureLog4j((loggerContext, configuration) -> {
      LoggerConfig loggerConfig = configuration.getLoggerConfig("jetbrains.buildServer.server.rest");
      loggerConfig.setLevel(Level.INFO);
      loggerConfig.addAppender(testAppender, Level.INFO, null);
    });

    check(null, build10);
    assertEmpty(testAppender.getLoggedMessages());
    testAppender.reset();

    check("buildType:buildConf1", build10);
    assertEmpty(testAppender.getLoggedMessages());
    testAppender.reset();

    checkExceptionOnBuildsSearch(LocatorProcessException.class, "a:b");
    assertEmpty(testAppender.getLoggedMessages());
    testAppender.reset();


    setInternalProperty("rest.report.unused.locator", "reportKnownButNotReportedDimensions");

    check(null, build10);
    assertEmpty(testAppender.getLoggedMessages());
    testAppender.reset();


    //cleanup logging settings
    LogInitializer.reconfigureLog4j((loggerContext, configuration) -> {
      LoggerConfig loggerConfig = configuration.getLoggerConfig("jetbrains.buildServer.server.rest");
      loggerConfig.setLevel(null);
      loggerConfig.removeAppender(testAppender.getName());
    });
  }

  @Test
  public void testUserDimension() throws Throwable {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf = (BuildTypeEx)project.createBuildType("buildConf", "buildConf");
    SUser user1 = createUser("user1"); user1.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().getProjectViewerRole());
    SUser user2 = createUser("user2"); user2.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().getProjectViewerRole());

    BuildPromotion build10 = build().in(buildConf).number("1").finish().getBuildPromotion();
    BuildPromotion build20 = build().in(buildConf).number("1").personalForUser(user1.getUsername()).finish().getBuildPromotion();
    BuildPromotion build30 = build().in(buildConf).number("1").by(user1).finish().getBuildPromotion();
    BuildPromotion build40 = build().in(buildConf).number("1").personalForUser(user2.getUsername()).by(user1).finish().getBuildPromotion();
    BuildPromotion build50 = build().in(buildConf).personalForUser(user1.getUsername()).addToQueue().getBuildPromotion();

    assertNull(build20.getAssociatedBuild().getTriggeredBy().getUser());

    //searches with "number:1" execute only filter part of the logic, but shold result in the same set of builds

    {
      check("state:any,defaultFilter:false", build50, build40, build30, build20, build10);
      check("state:any,defaultFilter:false,number:1", build40, build30, build20, build10);
      check("state:any,defaultFilter:false,user:user1", build50, build30, build20);
      check("state:any,defaultFilter:false,user:user1,number:1", build30, build20);
      check("state:any,defaultFilter:false,user:user2", build40);
      check("state:any,defaultFilter:false,user:user2,number:1", build40);
      check("state:any,defaultFilter:false,user:user1,personal:true", build50, build20);
      check("state:any,defaultFilter:false,user:user1,personal:true,number:1", build20);
      check("state:any,defaultFilter:false,user:user2,personal:true", build40);
      check("state:any,defaultFilter:false,user:user2,personal:true,number:1", build40);
      check("state:any,defaultFilter:false,user:user1,personal:false", build30);
      check("state:any,defaultFilter:false,user:user1,personal:false,number:1", build30);
      check("state:any,defaultFilter:false,user:user2,personal:false");
      check("state:any,defaultFilter:false,user:user2,personal:false,number:1");

      check("state:any,defaultFilter:false,triggered:(user:user1)", build40, build30);
      check("state:any,defaultFilter:false,triggered:(user:user1),user:user2", build40);
      check("state:any,defaultFilter:false,user:user1,not:(triggered:(user:user1))", build50, build20);
      check("state:any,defaultFilter:false,triggered:(type:user)", build40, build30);
    }

    {
      // this is needed to include build40, as it is personal for another user
      user1.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "true");

      myFixture.getSecurityContext().runAs(user1, () -> {
        check("state:any,defaultFilter:false", build50, build40, build30, build20, build10);
        check("state:any,defaultFilter:false,number:1", build40, build30, build20, build10);
        check("state:any,defaultFilter:false,user:user1", build50, build30, build20);
        check("state:any,defaultFilter:false,user:user1,number:1", build30, build20);
        check("state:any,defaultFilter:false,user:user2", build40);
        check("state:any,defaultFilter:false,user:user2,number:1", build40);
        check("state:any,defaultFilter:false,user:user1,personal:true", build50, build20);
        check("state:any,defaultFilter:false,user:user1,personal:true,number:1", build20);
        check("state:any,defaultFilter:false,user:user2,personal:true", build40);
        check("state:any,defaultFilter:false,user:user2,personal:true,number:1", build40);
        check("state:any,defaultFilter:false,user:user1,personal:false", build30);
        check("state:any,defaultFilter:false,user:user1,personal:false,number:1", build30);
        check("state:any,defaultFilter:false,user:user2,personal:false");
        check("state:any,defaultFilter:false,user:user2,personal:false,number:1");

        check("state:any,defaultFilter:false,triggered:(user:user1)", build40, build30);
        check("state:any,defaultFilter:false,triggered:(user:user1),user:user2", build40);
        check("state:any,defaultFilter:false,user:user1,not:(triggered:(user:user1))", build50, build20);
        check("state:any,defaultFilter:false,triggered:(type:user)", build40, build30);
      });
    }

    {
      // build20 and build50 should be excluded almost always except for cases when we specifically look for
      // personal builds of the user1
      user2.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "false");

      myFixture.getSecurityContext().runAs(user2, () -> {
        check("state:any,defaultFilter:false", build40, build30, build10);
        check("state:any,defaultFilter:false,number:1", build40, build30, build10);
        check("state:any,defaultFilter:false,user:user1", build50, build30, build20);
        check("state:any,defaultFilter:false,user:user1,number:1", build30, build20);
        check("state:any,defaultFilter:false,user:user2", build40);
        check("state:any,defaultFilter:false,user:user2,number:1", build40);
        check("state:any,defaultFilter:false,user:user1,personal:true", build50, build20);
        check("state:any,defaultFilter:false,user:user1,personal:true,number:1", build20);
        check("state:any,defaultFilter:false,user:user2,personal:true", build40);
        check("state:any,defaultFilter:false,user:user2,personal:true,number:1", build40);
        check("state:any,defaultFilter:false,user:user1,personal:false", build30);
        check("state:any,defaultFilter:false,user:user1,personal:false,number:1", build30);
        check("state:any,defaultFilter:false,user:user2,personal:false");
        check("state:any,defaultFilter:false,user:user2,personal:false,number:1");

        check("state:any,defaultFilter:false,triggered:(user:user1)", build40, build30);
        check("state:any,defaultFilter:false,triggered:(user:user1),user:user2", build40);
        check("state:any,defaultFilter:false,user:user1,not:(triggered:(user:user1))", build50, build20);
        check("state:any,defaultFilter:false,triggered:(type:user)", build40, build30);
      });
    }
  }

  @Test
  public void testDefaultFilterWithShowAllPersonalPreference() throws Throwable {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf = (BuildTypeEx)project.createBuildType("buildConf", "buildConf");
    SUser user1 = createUser("user1"); user1.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().getProjectViewerRole());
    SUser user2 = createUser("user2"); user2.addRole(RoleScope.projectScope(project.getProjectId()), getTestRoles().getProjectViewerRole());

    BuildPromotion regularFinishedBuild  = build().in(buildConf).number("1").finish().getBuildPromotion();
    BuildPromotion cancelledBuild  = build().in(buildConf).number("1").cancel(user1).getBuildPromotion();
    BuildPromotion personalBuild1 = build().in(buildConf).number("1").personalForUser(user1.getUsername()).finish().getBuildPromotion();
    BuildPromotion personalBuild2 = build().in(buildConf).number("1").personalForUser(user2.getUsername()).finish().getBuildPromotion();

    user1.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "true");
    myFixture.getSecurityContext().runAs(user1, () -> {
      // Eqvivalent to defaultFilter:true, so no cancelled or personal builds
      check("buildType:buildConf", regularFinishedBuild);

      check("defaultFilter:true,personal:any,buildType:buildConf", personalBuild2, personalBuild1, regularFinishedBuild);

      check("defaultFilter:false,buildType:buildConf", personalBuild2, personalBuild1, cancelledBuild, regularFinishedBuild);
    });

    user1.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "false");
    myFixture.getSecurityContext().runAs(user1, () -> {
      // Eqvivalent to defaultFilter:true, so no cancelled or personal builds
      check("buildType:buildConf", regularFinishedBuild);

      check("defaultFilter:true,personal:any,buildType:buildConf", personalBuild1,regularFinishedBuild);

      // Should return all builds except personal for another user
      check("defaultFilter:false,buildType:buildConf", personalBuild1, cancelledBuild, regularFinishedBuild);
    });
  }

  @Test(dataProvider = "true,false")
  public void testSnapshotDependenciesProblems(boolean preemptiveBuildsStartFailureEnabled) {
    setInternalProperty("teamcity.queueDistribution.preemptiveBuildsStartFailure.enabled", String.valueOf(preemptiveBuildsStartFailureEnabled));

    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConfA = (BuildTypeEx)project.createBuildType("buildConfA", "buildConfA");
    final BuildTypeEx buildConfB1 = (BuildTypeEx)project.createBuildType("buildConfB1", "buildConfB1");
    final BuildTypeEx buildConfB2 = (BuildTypeEx)project.createBuildType("buildConfB2", "buildConfB2");
    final BuildTypeEx buildConfC = (BuildTypeEx)project.createBuildType("buildConfC", "buildConfC");
    addDependency(buildConfB1, buildConfA).setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.RUN_ADD_PROBLEM);
    addDependency(buildConfB2, buildConfA).setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.RUN_ADD_PROBLEM);
    addDependency(buildConfC, buildConfB1).setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.RUN_ADD_PROBLEM);
    addDependency(buildConfC, buildConfB2).setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.RUN_ADD_PROBLEM);

    {
      BuildPromotion buildA = build().in(buildConfA).withProblem(BuildProblemData.createBuildProblem("problem1", "problemType", "problem descr"))
                                     .finish().getBuildPromotion();
      BuildPromotion buildB1 = build().in(buildConfB1).withProblem(BuildProblemData.createBuildProblem("problem21", "problemType", "problem descr2"))
                                      .snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildB2 = build().in(buildConfB2).withProblem(BuildProblemData.createBuildProblem("problem22", "problemType", "problem descr2"))
                                      .snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildC = build().in(buildConfC).withProblem(BuildProblemData.createBuildProblem("problem3", "problemType", "problem descr3"))
                                     .snapshotDepends(buildB1, buildB2).finish().getBuildPromotion();

      check(null, buildC, buildB2, buildB1, buildA);
      check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))", buildB2, buildB1, buildA);
      check("snapshotDependencyProblem:(from:(id:" + buildB1.getId() + "))", buildC);
      check("snapshotDependencyProblem:(from:(id:" + buildA.getId() + "))", buildC, buildB2, buildB1);
      checkProblemOccurrences("build:(snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))),type:(snapshotDependencyProblem:false)",
                              "problem22", "problem21", "problem1");
      buildC.getAssociatedBuild().muteBuildProblems(createUser("u"), true, "");
      check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))");
      checkProblemOccurrences("build:(snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))),type:(snapshotDependencyProblem:false)");
    }

    {
      BuildPromotion buildA = build().in(buildConfA).withProblem(BuildProblemData.createBuildProblem("problem1", "problemType", "problem descr"))
                                     .finish().getBuildPromotion();
      BuildPromotion buildB1 = build().in(buildConfB1).withProblem(BuildProblemData.createBuildProblem("problem21", "problemType", "problem descr2"))
                                      .snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildB2 = build().in(buildConfB2).snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildC = build().in(buildConfC).snapshotDepends(buildB1, buildB2).finish().getBuildPromotion();

      check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))", buildB2, buildB1, buildA);
      check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "),includeInitial:true)", buildC, buildB2, buildB1, buildA);
      check("id:" + buildB2.getId() + ",snapshotDependencyProblem:(to:(id:" + buildC.getId() + "),includeInitial:true)", buildB2);
      checkProblemOccurrences("build:(snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))),type:(snapshotDependencyProblem:false)",
                              "problem21", "problem1");
    }

    {
      BuildPromotion buildA = build().in(buildConfA).withProblem(BuildProblemData.createBuildProblem("problem1", "problemType", "problem descr"))
                                     .finish().getBuildPromotion();
      BuildPromotion buildB1 = build().in(buildConfB1).snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildB2 = build().in(buildConfB2).snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildC = build().in(buildConfC).snapshotDepends(buildB1, buildB2).finish().getBuildPromotion();

      check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))", buildB2, buildB1, buildA);
      checkProblemOccurrences("build:(snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))),type:(snapshotDependencyProblem:false)",
                              "problem1");
    }

    Dependency depCB2 = buildConfC.getDependencies().stream().filter(d -> buildConfB2.equals(d.getDependOn())).findAny().get();
    depCB2.setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.RUN);

    {
      BuildPromotion buildA = build().in(buildConfA).withProblem(BuildProblemData.createBuildProblem("problem1", "problemType", "problem descr"))
                                     .finish().getBuildPromotion();
      BuildPromotion buildB1 = build().in(buildConfB1).snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildB2 = build().in(buildConfB2).snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildC = build().in(buildConfC).snapshotDepends(buildB1, buildB2).finish().getBuildPromotion();

      check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))", buildB1, buildA);
      checkProblemOccurrences("build:(snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))),type:(snapshotDependencyProblem:false)",
                              "problem1");
    }

    depCB2.setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.MAKE_FAILED_TO_START);

    {
      BuildPromotion buildA = build().in(buildConfA).withProblem(BuildProblemData.createBuildProblem("problem1", "problemType", "problem descr"))
                                     .finish().getBuildPromotion();
      BuildPromotion buildB1 = build().in(buildConfB1).snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildB2 = build().in(buildConfB2).snapshotDepends(buildA).finish().getBuildPromotion();
      BuildPromotion buildC = preemptiveBuildsStartFailureEnabled
                              ? build().in(buildConfC).snapshotDepends(buildB1, buildB2).expectTerminated().getBuildPromotion()
                              : build().in(buildConfC).snapshotDepends(buildB1, buildB2).finish().getBuildPromotion();

      if (preemptiveBuildsStartFailureEnabled) {
        check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))", buildB2, buildA);
      } else {
        check("snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))", buildB2, buildB1, buildA);
      }
      checkProblemOccurrences("build:(snapshotDependencyProblem:(to:(id:" + buildC.getId() + "))),type:(snapshotDependencyProblem:false)",
                              "problem1");
    }
  }

  @Test(dataProvider = "all-build-states-locator-dim")
  public void test_personal_dimension_with_user_property(String buildState) throws Throwable {
    SUser user = createUser("user"); user.addRole(RoleScope.globalScope(), getTestRoles().getProjectViewerRole());
    SUser secondUser = createUser("seconduser");

    if("finished".equals(buildState)) {
      createPersonalBuild(myBuildType, user, new String[0], new String[0]);
      createPersonalBuild(myBuildType, secondUser, new String[0], new String[0]);
      build().in(myBuildType).finish();
    }

    if("running".equals(buildState)) {
      createTwoAdditionalAgents();

      build().in(myBuildType).run();
      build().in(myBuildType).personalForUser(secondUser.getUsername()).run();
      build().in(myBuildType).personalForUser(user.getUsername()).run();
    }

    if("queued".equals(buildState)) {
      build().in(myBuildType).addToQueue();
      build().in(myBuildType).personalForUser(secondUser.getUsername()).addToQueue();
      build().in(myBuildType).personalForUser(user.getUsername()).addToQueue();
    }


    PagedSearchResult<BuildPromotion> result;
    {
      String locator = "defaultFilter:false,buildType:(id:" + myBuildType.getExternalId() + "),state:" + buildState + ",personal:any";

      user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "true");
      result = myFixture.getSecurityContext().runAs(user, () -> myBuildPromotionFinder.getItems(locator));
      assertEquals(
        "All personal builds must be included when user property value is set to true.",
        3, result.getEntries().size()
      );
      user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "false");
      result = myFixture.getSecurityContext().runAs(user, () -> myBuildPromotionFinder.getItems(locator));
      assertEquals(
        "Only current user's personal build must be included when user property value is set to false.",
        2, result.getEntries().size()
      );
    }


    {
      String locator = "defaultFilter:false,buildType:(id:" + myBuildType.getExternalId() + "),state:" + buildState + ",personal:true";

      user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "true");
      result = myFixture.getSecurityContext().runAs(user, () -> myBuildPromotionFinder.getItems(locator));
      assertEquals(
        "Both personal builds must be included when user property value is set to true.",
        2, result.getEntries().size()
      );
      user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "false");
      result = myFixture.getSecurityContext().runAs(user, () -> myBuildPromotionFinder.getItems(locator));
      assertEquals(
        "Only current user's personal build must be included when user property value is set to false.",
        1, result.getEntries().size()
      );
    }


    {
      String locator = "defaultFilter:false,buildType:(id:" + myBuildType.getExternalId() + "),state:" + buildState + ",personal:false";

      user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "true");
      result = myFixture.getSecurityContext().runAs(user, () -> myBuildPromotionFinder.getItems(locator));
      assertEquals(
        "None personal builds must be included when dimension is set to false",
        1, result.getEntries().size()
      );
      user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "false");
      result = myFixture.getSecurityContext().runAs(user, () -> myBuildPromotionFinder.getItems(locator));
      assertEquals(
        "None personal builds must be included when dimension is set to false.",
        1, result.getEntries().size()
      );
    }
  }

  @Test(dataProvider = "allBooleans")
  public void test_computePersonalBuildsRuling_includePersonal(boolean showAllPersonalBuildsSetting) throws Throwable {
    SUser user = createUser("user");
    user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, Boolean.toString(showAllPersonalBuildsSetting));

    Locator locator = Locator.locator("personal:true");
    BuildPromotionFinder.IncludePersonalBuildsRuling ruling = myFixture.getSecurityContext().runAs(user, () ->
      myBuildPromotionFinder.computePersonalBuildsRuling(locator)
    );

    assertTrue(
      "Personal builds must be included when dimension is explicitely set regardless of the user property value.",
      ruling.isIncludePersonal()
    );
    if(showAllPersonalBuildsSetting) {
      assertNull(ruling.getOwner());
    } else {
      assertEquals(user, ruling.getOwner());
    }
  }

  @Test(dataProvider = "allBooleans")
  public void test_computePersonalBuildsRuling_includePersonalForUser(boolean showAllPersonalBuildsSetting) throws Throwable {
    SUser user = createUser("user");
    user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, Boolean.toString(showAllPersonalBuildsSetting));

    Locator locator = Locator.locator("personal:true,user:user");
    BuildPromotionFinder.IncludePersonalBuildsRuling ruling = myFixture.getSecurityContext().runAs(user, () ->
      myBuildPromotionFinder.computePersonalBuildsRuling(locator)
    );

    assertTrue(
      "Personal builds must be included when dimension is explicitely set regardless of the user property value.",
      ruling.isIncludePersonal()
    );
    assertEquals(
      "User must be set when dimension is set.",
      user, ruling.getOwner()
    );
  }

  @Test(dataProvider = "allBooleans")
  public void test_computePersonalBuildsRuling_excludePersonal(boolean showAllPersonalBuildsSetting) throws Throwable {
    SUser user = createUser("user");
    user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, Boolean.toString(showAllPersonalBuildsSetting));

    Locator locator = Locator.locator("personal:false");
    BuildPromotionFinder.IncludePersonalBuildsRuling ruling = myFixture.getSecurityContext().runAs(user, () ->
      myBuildPromotionFinder.computePersonalBuildsRuling(locator)
    );

    assertFalse(
      "Personal builds must be excluded when dimension is explicitely set regardless of the user property value.",
      ruling.isIncludePersonal()
    );
    assertNull(ruling.getOwner());
  }

  @Test(dataProvider = "allBooleans")
  public void test_computePersonalBuildsRuling_excludePersonalWithUser(boolean showAllPersonalBuildsSetting) throws Throwable {
    SUser user = createUser("user");
    user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, Boolean.toString(showAllPersonalBuildsSetting));

    Locator locator = Locator.locator("personal:false,user:user");
    BuildPromotionFinder.IncludePersonalBuildsRuling ruling = myFixture.getSecurityContext().runAs(user, () ->
      myBuildPromotionFinder.computePersonalBuildsRuling(locator)
    );

    assertFalse(
      "Personal builds must be excluded when dimension is explicitely set regardless of the user property value.",
      ruling.isIncludePersonal()
    );
    assertNull(ruling.getOwner());
  }

  @Test(dataProvider = "allBooleans")
  public void test_computePersonalBuildsRuling_defaultPersonal(boolean showAllPersonalBuildsSetting) throws Throwable {
    SUser user = createUser("user");
    user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, Boolean.toString(showAllPersonalBuildsSetting));

    Locator locator = Locator.locator("fake:dimension");
    BuildPromotionFinder.IncludePersonalBuildsRuling ruling = myFixture.getSecurityContext().runAs(user, () ->
      myBuildPromotionFinder.computePersonalBuildsRuling(locator)
    );

    assertTrue("Personal builds must be set according to the user property value when dimension value is not present.", ruling.isIncludePersonal());
    if(showAllPersonalBuildsSetting) {
      assertNull("Personal builds of all users are intertesting.", ruling.getOwner());
    } else {
      assertEquals("Only personal builds of the current user are intertesting.", user, ruling.getOwner());
    }
  }

  @Test
  public void test_computePersonalBuildsRuling_enablePersonalWhenNoUser() {
    Locator locator = Locator.locator("fake:dimension");

    BuildPromotionFinder.IncludePersonalBuildsRuling ruling = myBuildPromotionFinder.computePersonalBuildsRuling(locator);

    assertTrue(
      "Personal builds must be returned when requesting user can not be determined (e.g. request is coming from the build).",
      ruling.isIncludePersonal()
    );
    assertNull(ruling.getOwner());
  }

  @Test(dataProvider = "allBooleans")
  public void test_computePersonalBuildsRuling_includePersonalForOtherUser(boolean showAllPersonalBuildsSetting) throws Throwable {
    SUser user = createUser("user");
    SUser secondUser = createUser("secondUser");
    user.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, Boolean.toString(showAllPersonalBuildsSetting));

    Locator locator = Locator.locator("user:secondUser");
    BuildPromotionFinder.IncludePersonalBuildsRuling ruling = myFixture.getSecurityContext().runAs(user, () ->
      myBuildPromotionFinder.computePersonalBuildsRuling(locator)
    );

    assertTrue(
      "Personal builds must be returned when requesting user can not be determined (e.g. request is coming from the build).",
      ruling.isIncludePersonal()
    );
    assertEquals(secondUser, ruling.getOwner());
  }

  @Test
  public void test_getBuildPromotionsWithLegacyFallback_LegacyFilteringDisabled() {
    PagedSearchResult<BuildPromotion> result = myBuildPromotionFinder.getBuildPromotionsWithLegacyFallback(myBuildType, null);

  }

  @Test
  @TestFor(issues = "TW-82114")
  public void test_getPromotionWithNumberContainingParenthesis() {
    SFinishedBuild build = build().in(myBuildType).number("0.1.2.3 (master)").finish();

    BuildPromotion result = myBuildPromotionFinder.getItems("number:(0.1.2.3 (master))").getEntries().get(0);

    assertEquals(build.getBuildPromotion(), result);
  }

  @Test
  @TestFor(issues = "TW-83663")
  public void queuedBuildCanBeFoundByParameter() {
    // Prevent build from starting by including a non-existing runner
    SBuildType bt = registerBuildType("some_bt", "some_project", "non_exsisting_runner");
    build().in(bt).parameter("test", "value").addToQueue();

    List<BuildPromotion> result = myBuildPromotionFinder.getItems("property:(name:test),state:queued").getEntries();
    assertEquals(1, result.size());
  }

  @Test
  @TestFor(issues = "TW-83663")
  public void failedToStartBuildCanBeFoundByParameter() {
    build().in(myBuildType).parameter("test", "value").failedToStart().finish();

    List<BuildPromotion> result = myBuildPromotionFinder.getItems("property:(name:test),failedToStart:true").getEntries();
    assertEquals(1, result.size());
  }

  @NotNull
  @DataProvider(name = "all-build-states-locator-dim")
  public String[][] getAllBuildStates() {
    return new String[][] {{"running"}, {"finished"}, {"queued"}};
  }

  private void checkProblemOccurrences(final String locator, final String... problemIds) {
    check(locator, (id, buildProblem) -> id.equals(buildProblem.getBuildProblemData().getIdentity()), myProblemOccurrenceFinder, problemIds);

  }

  //==================================================

  public void checkBuilds(final String locator, BuildPromotion... builds) {
    checkMultipleBuilds(locator, builds);

    //check single build retrieve
    if (locator != null) {
      if (builds.length == 0) {
        checkNoBuildFound(locator);
      } else {
        checkBuild(locator, builds[0]);
      }
    }
  }

  private void checkBuilds(final String locator, final long actuallyProcessedLimit, final BuildPromotion[] builds) {
    final PagedSearchResult<BuildPromotion> result = checkMultipleBuilds(locator, builds);
    //noinspection ConstantConditions
    assertTrue("Actually processed count (" + result.getActuallyProcessedCount() + ") is more than expected limit " + actuallyProcessedLimit,
               (long)result.getActuallyProcessedCount() <= actuallyProcessedLimit);

  }

  private PagedSearchResult<BuildPromotion> checkMultipleBuilds(final String locator, final BuildPromotion... builds) {
    final PagedSearchResult<BuildPromotion> searchResult = myBuildPromotionFinder.getItems(locator);
    final List<BuildPromotion> result = searchResult.getEntries();
    final String expected = getPromotionsDescription(Arrays.asList(builds));
    final String actual = getPromotionsDescription(result);
    assertEquals("For locator \"" + locator + "\"\n" +
                 "Expected:\n" + expected + "\n\n" +
                 "Actual:\n" + actual, builds.length, result.size());
    for (int i = 0; i < builds.length; i++) {
      if (!builds[i].equals(result.get(i))) {
        fail("Wrong build found for locator \"" + locator + "\" at position " + (i + 1) + "/" + builds.length + "\n" +
             "Expected:\n" + expected + "\n" +
             "\nActual:\n" + actual);
      }
    }
    return searchResult;
  }

  protected void checkBuild(final String locator, @NotNull BuildPromotion build) {
    BuildPromotion result = myBuildPromotionFinder.getItem(locator);

    if (!build.equals(result)) {
      fail("For single build locator \"" + locator + "\"\n" +
           "Expected: " + LogUtil.describeInDetail(build) + "\n" +
           "Actual: " + LogUtil.describeInDetail(result));
    }
  }

  protected void checkNoBuildsFound(final String locator) {
    final List<BuildPromotion> result = myBuildPromotionFinder.getItems(locator).getEntries();
    if (!result.isEmpty()) {
      fail("For locator \"" + locator + "\" expected NotFoundException but found " + LogUtil.describe(result) + "");
    }
  }

  protected void checkNoBuildFound(final String singleBuildLocator) {
    checkExceptionOnBuildSearch(NotFoundException.class, singleBuildLocator);
  }

  public static String getPromotionsDescription(final List<BuildPromotion> result) {
    return LogUtil.describe(CollectionsUtil.convertCollection(
      result,
      source -> LogUtil.appendDescription(LogUtil.describeInDetail(source), "startTime: " + LogUtil.describe(source.getServerStartDate()))
    ), "\n", "", "");
  }

  public <E extends Throwable> void checkExceptionOnBuildSearch(final Class<E> exception, final String singleBuildLocator) {
    checkException(
      exception,
      () -> myBuildPromotionFinder.getItem(singleBuildLocator),
      "searching single build with locator \"" + singleBuildLocator + "\""
    );
  }

  public <E extends Throwable> void checkExceptionOnBuildsSearch(final Class<E> exception, final String multipleBuildsLocator) {
    checkException(
      exception,
      () -> myBuildPromotionFinder.getItems(multipleBuildsLocator),
      "searching builds with locator \"" + multipleBuildsLocator + "\""
    );
  }

  @NotNull
  public static BuildPromotion[] getBuildPromotions(final BuildPromotionOwner... builds) {
    final BuildPromotion[] buildPromotions = new BuildPromotion[builds.length];
    for (int i = 0; i < builds.length; i++) {
      buildPromotions[i] = builds[i].getBuildPromotion();
    }
    return buildPromotions;
  }

  private void createTwoAdditionalAgents() {
    // We call createEnabledAgent twice here instead of calling myFixture.createEnabledAgents("ant", 2) to avoid
    // licence checks as LicenceManager is not available for REST plugin. We won't be able to create more than two
    // though, as TC core still internally checks licences when authorizing an agent.

    myFixture.createEnabledAgent("Ant");
    myFixture.createEnabledAgent("Ant");
  }

  private static class TestingLogAppender extends AbstractAppender {
    private final List<String> myLoggedMessages = new ArrayList<>();

    public TestingLogAppender(String name) {
      super(name, null, null);
    }

    @Override
    public void append(LogEvent event) {
      myLoggedMessages.add(event.getMessage().getFormattedMessage());
    }

    public List<String> getLoggedMessages() {
      return myLoggedMessages;
    }

    public void reset() {
      myLoggedMessages.clear();
    }
  }
}
