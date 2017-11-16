/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.ProjectFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 25/11/2015
 */
public class VcsRootInstanceFinderTest extends BaseFinderTest<VcsRootInstance> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    setFinder(myVcsRootInstanceFinder);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testSingleDimensions() throws Exception {
    myFixture.registerVcsSupport("svn");
    myFixture.registerVcsSupport("cvs");
    myFixture.registerVcsSupport("custom");

    final ProjectEx project10 = getRootProject().createProject("project10", "Project name 10");
    final ProjectEx project20 = project10.createProject("project20", "Project name 20");
    final ProjectEx project25 = project20.createProject("project25", "Project name 25");
    final ProjectEx project30 = project10.createProject("project30", "Project name 30");
    project30.setArchived(true, null);

    final SVcsRoot vcsRoot10 = getRootProject().createVcsRoot("svn", "id10", "VCS root 10 name");
    final SVcsRoot vcsRoot12 = getRootProject().createVcsRoot("svn", "id12", "VCS root 12 name");
    vcsRoot12.setProperties(CollectionsUtil.asMap("url", "", "aaa", "%ref%"));
    final SVcsRoot vcsRoot15 = getRootProject().createVcsRoot("custom", "id15", "VCS root 15 name");
    vcsRoot15.setProperties(CollectionsUtil.asMap("url", "", "aaa", "3"));
    final SVcsRoot vcsRoot20 = project10.createVcsRoot("cvs", "id20", "VCS root 20 name");
    vcsRoot20.setProperties(CollectionsUtil.asMap("url", "http://xxx", "aaa", "5", "ccc", "ddd"));
    final SVcsRoot vcsRoot30 = project20.createVcsRoot("svn", "id30", "VCS root 30 name");

    final SVcsRoot vcsRoot40 = project30.createVcsRoot("svn", "id40", "VCS root 40 name");
    vcsRoot40.setProperties(CollectionsUtil.asMap("url", "http://xxx", "aaa", "3", "ccc", "ddd"));

    final SVcsRoot vcsRoot50 = project25.createVcsRoot("cvs", "id50", "VCS root 50 name");

    final SBuildType bt10 = project20.createBuildType("id10", "name 10");
    final BuildTypeTemplate templ10 = project20.createBuildTypeTemplate("t_id10", "template name 10");
    templ10.addVcsRoot(vcsRoot10);
    templ10.addVcsRoot(vcsRoot12);
    templ10.setCheckoutRules(vcsRoot10, new CheckoutRules("+:aaa=>bbb"));
    final SBuildType bt20 = project20.createBuildType("id20", "name 20");
    bt20.addParameter(new SimpleParameter("ref", "RESOLVED"));
    bt20.attachToTemplate(templ10);
    VcsRootInstance vInstance10 = bt20.getVcsRootInstanceForParent(vcsRoot10);
    VcsRootInstance vInstance20 = bt20.getVcsRootInstanceForParent(vcsRoot12);
    VcsRootInstance vInstance30 = attachVcsRoot(bt20, vcsRoot20);
    final BuildTypeEx bt25 = project20.createBuildType("id25", "name 25");
    bt25.attachToTemplate(templ10);
    VcsRootInstance vInstance40 = bt25.getVcsRootInstanceForParent(vcsRoot10);
    assertEquals(vInstance10, vInstance40);
    VcsRootInstance vInstance50 = bt25.getVcsRootInstanceForParent(vcsRoot12);
    final SBuildType bt30 = project20.createBuildType("id30", "name 30");
    VcsRootInstance vInstance60 = attachVcsRoot(bt30, vcsRoot10);
    assertEquals(vInstance10, vInstance60);
    final SBuildType bt40 = project20.createBuildType("id40", "name 40");
    VcsRootInstance vInstance70 = attachVcsRoot(bt40, vcsRoot30);

    final BuildTypeEx bt50 = project30.createBuildType("id50", "name 50");
    VcsRootInstance vInstance80 = attachVcsRoot(bt50, vcsRoot40);

    final BuildTypeEx bt60 = project25.createBuildType("id60", "name 60");
    VcsRootInstance vInstance90 = attachVcsRoot(bt60, vcsRoot50);

    check(null, vInstance10, vInstance20, vInstance30, vInstance50, vInstance70, vInstance80, vInstance90);

    check(String.valueOf(vInstance10.getId()), vInstance10);
    check("id:" + vInstance10.getId(), vInstance10);
    check("id:" + vInstance50.getId(), vInstance50);
    check("id:" + vInstance80.getId(), vInstance80);

    check("vcsRoot:(id:" + vcsRoot10.getExternalId() + ")", vInstance10);
    check("vcsRoot:(id:" + vcsRoot12.getExternalId() + ")", vInstance20, vInstance50);
    check("vcsRoot:(type:cvs)", vInstance30, vInstance90);

    check("type:svn", vInstance10, vInstance20, vInstance50, vInstance70, vInstance80);
    check("type:custom");

    check("project:(id:" + project20.getExternalId() + ")", vInstance70); //todo
    check("project:(id:" + project10.getExternalId() + ")", vInstance30); //todo
    check("affectedProject:(id:" + project20.getExternalId() + ")",vInstance10, vInstance20, vInstance30, vInstance50, vInstance70, vInstance90);

    check("buildType:(id:" + bt20.getExternalId() + ")", vInstance10, vInstance20, vInstance30);
    check("buildType:(id:" + templ10.getExternalId() + ",template:true)", vInstance10, vInstance50);

    check("property:(name:aaa,value:RESOLVED)", vInstance20);
    check("property:(name:aaa,value:2,matchType:more-than)", vInstance30, vInstance80);
    check("property:(name:aaa,value:4,matchType:more-than)", vInstance30);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testRepositoryIdString() throws Exception {
    MockVcsSupport svn = myFixture.registerVcsSupport("svn");
    svn.addCustomVcsExtension(VcsPersonalSupport.class, new VcsPersonalSupport() {
      @NotNull
      @Override
      public Collection<String> mapFullPath(@NotNull final VcsRootEntry rootEntry, @NotNull final String fullPath) throws VcsException {
        String prefix = rootEntry.getProperties().get("prefix");
        if (prefix != null && fullPath.startsWith(prefix))  return Collections.singletonList(fullPath.substring(prefix.length()));
        return Collections.emptyList();
      }
    });
    svn.addCustomVcsExtension(VcsClientMappingProvider.class, new VcsRootBasedMappingProvider() {

      @Override
      public Collection<VcsClientMapping> getClientMapping(@NotNull final VcsRoot vcsRoot) throws VcsException {
        String prefix = vcsRoot.getProperties().get("prefix");
        if (prefix != null) return Collections.singletonList(new VcsClientMapping(prefix, ""));
        return Collections.emptyList();
      }
    });

    myFixture.registerVcsSupport("custom");

    final ProjectEx project10 = getRootProject().createProject("project10", "Project name 10");

    final SVcsRoot vcsRoot10 = project10.createVcsRoot("svn", "id10", "VCS root 10 name");
    vcsRoot10.setProperties(CollectionsUtil.asMap("url", "22", "prefix", "000000-0000-1111-000000000001|trunk/path1"));
    final SVcsRoot vcsRoot12 = project10.createVcsRoot("svn", "id12", "VCS root 12 name");
    vcsRoot12.setProperties(CollectionsUtil.asMap("url", "", "prefix", "000000-0000-1111-000000000001|trunk/path2"));
    final SVcsRoot vcsRoot15 = project10.createVcsRoot("custom", "id15", "VCS root 15 name");
    vcsRoot15.setProperties(CollectionsUtil.asMap("url", "", "aaa", "3"));

    final SBuildType bt10 = project10.createBuildType("id10", "name 10");
    VcsRootInstance vInstance10 = attachVcsRoot(bt10, vcsRoot10);
    VcsRootInstance vInstance20 = attachVcsRoot(bt10, vcsRoot12);
    bt10.setCheckoutRules(vcsRoot10, new CheckoutRules("+:aaa=>bbb"));

    check("repositoryIdString:xxx");
    check("repositoryIdString:000000-0000-1111-000000000001|trunk/path1", vInstance10); //pre-TeamCity 10 behavior
    check("repositoryIdString:000000-0000-1111-000000000001|trunk/path1/aaa", vInstance10);  //pre-TeamCity 10 behavior
    check("repositoryIdString:svn://000000-0000-1111-000000000001|trunk/path1", vInstance10);
    check("repositoryIdString:svn://000000-0000-1111-000000000001|trunk/path1/aaa", vInstance10);
    check("repositoryIdString:svn://000000-0000-1111-000000000001|trunk/path2/aaa", vInstance20);

    check("repositoryIdString:svn://000000-0000-1111-000000000001|trunk2");
    check("repositoryIdString:svn://000000-0000-1111-000000000001|trunk/path9");
    check("repositoryIdString:svn://000000-0000-1111-000000000002|trunk/path1");

//does not work for now    check("repositoryIdString:svn://000000-0000-1111-000000000001|", vInstance10, vInstance20);
    check("repositoryIdString:svn://000000-0000-1111-000000000001|trunk", vInstance10, vInstance20);
    check("repositoryIdString:svn://000000-0000-1111-000000000001|trunk/path");
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testCount() throws Exception {
    myFixture.registerVcsSupport("svn");

    final ProjectEx project10 = getRootProject().createProject("project10", "Project name 10");

    for (int i = 0; i < 15; i++) {
      final SVcsRoot vcsRoot = getRootProject().createVcsRoot("svn", "id" + i, "VCS root " + i + " name");
      final SBuildType bt = project10.createBuildType("id" + i, "name " + i);
      attachVcsRoot(bt, vcsRoot);
    }

    assertEquals(3, getFinder().getItems("count:3").myEntries.size());
    assertEquals(15, getFinder().getItems(null).myEntries.size());

    assertEquals(5, getFinder().getItems("start:10").myEntries.size());
    assertEquals(10, getFinder().getItems("lookupLimit:10").myEntries.size());

    setInternalProperty("rest.defaultPageSize", "12");
    assertEquals(12, getFinder().getItems(null).myEntries.size());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testStatusFiltering() throws Exception {
    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);

    myFixture.registerVcsSupport("svn");

    final ProjectEx project10 = getRootProject().createProject("project10", "Project name 10");

    final SVcsRoot vcsRoot = getRootProject().createVcsRoot("svn", "id1", "VCS root 1 name");
    final SBuildType bt = project10.createBuildType("id1", "name 1");
    attachVcsRoot(bt, vcsRoot);

    final SVcsRoot vcsRoot2 = getRootProject().createVcsRoot("svn", "id2", "VCS root 2 name");
    final SBuildType bt2 = project10.createBuildType("id2", "name 2");
    attachVcsRoot(bt2, vcsRoot2);

    assertEquals(2, getFinder().getItems("status:not_monitored").myEntries.size());
    assertEquals(0, getFinder().getItems("status:scheduled").myEntries.size());
    assertEquals(0, getFinder().getItems("status:started").myEntries.size());
    assertEquals(0, getFinder().getItems("status:finished").myEntries.size());
    assertEquals(0, getFinder().getItems("status:unknown").myEntries.size());

    ((VcsRootInstanceEx)bt.getVcsRootInstances().get(0)).setStatus(VcsRootStatus.Type.FINISHED);

    assertEquals(1, getFinder().getItems("status:not_monitored").myEntries.size());
    assertEquals(0, getFinder().getItems("status:scheduled").myEntries.size());
    assertEquals(0, getFinder().getItems("status:started").myEntries.size());
    assertEquals(1, getFinder().getItems("status:finished").myEntries.size());
    assertEquals(0, getFinder().getItems("status:unknown").myEntries.size());

    ((VcsRootInstanceEx)bt2.getVcsRootInstances().get(0)).setStatus(VcsRootStatus.Type.STARTED);

    assertEquals(0, getFinder().getItems("status:not_monitored").myEntries.size());
    assertEquals(0, getFinder().getItems("status:scheduled").myEntries.size());
    assertEquals(1, getFinder().getItems("status:started").myEntries.size());
    assertEquals(1, getFinder().getItems("status:finished").myEntries.size());
    assertEquals(0, getFinder().getItems("status:unknown").myEntries.size());

    checkExceptionOnItemsSearch(BadRequestException.class, "status:aaa");

    assertEquals(1, getFinder().getItems("status:(current:(status:started))").myEntries.size());

    ((VcsRootInstanceEx)bt2.getVcsRootInstances().get(0)).setStatus(VcsRootStatus.Type.FINISHED);

    assertEquals(2, getFinder().getItems("status:(current:(status:finished))").myEntries.size());
    assertEquals(1, getFinder().getItems("status:(previous:(status:started))").myEntries.size());

    time.jumpTo(10);

    assertEquals(0, getFinder().getItems("status:(current:(timestamp:-2s))").myEntries.size());
    assertEquals(2, getFinder().getItems("status:(current:(timestamp:-20s))").myEntries.size());

    assertEquals(0, getFinder().getItems("checkingForChangesFinishDate:-2s").myEntries.size());
    assertEquals(2, getFinder().getItems("checkingForChangesFinishDate:-20s").myEntries.size());
  }

  private VcsRootInstance attachVcsRoot(final SBuildType buildType, final SVcsRoot vcsRoot) {
    buildType.addVcsRoot(vcsRoot);
    return buildType.getVcsRootInstanceForParent(vcsRoot);
  }

  @Test
  public void testVersionedSettingsInstances() throws Exception {
    myFixture.registerVcsSupport("svn");

    final ProjectEx project10 = getRootProject().createProject("project10", "Project name 10");
    final ProjectEx project20 = project10.createProject("project20", "Project name 20");
    final ProjectEx project30 = project20.createProject("project30", "Project name 30");
    final ProjectEx project40 = project30.createProject("project40", "Project name 40");
    final ProjectEx project50 = project40.createProject("project50", "Project name 50");
    final ProjectEx project60 = project40.createProject("project60", "Project name 60");

    final SVcsRoot vcsRoot20 = project20.createVcsRoot("svn", "id10", "VCS root 10 name");
    vcsRoot20.setProperties(CollectionsUtil.asMap("aaa", "%param%"));

    final SVcsRoot vcsRoot30 = project20.createVcsRoot("svn", "id30", "id30");

    ProjectFeatureDescriptorFactory projectFeatureFactory = myFixture.getSingletonService(ProjectFeatureDescriptorFactory.class);
    Map<String, String> params = CollectionsUtil.asMap("buildSettings", "ALWAYS_USE_CURRENT",
                                                       "rootId", vcsRoot20.getExternalId(),
                                                       "showChanges", "false");
    SProjectFeatureDescriptor featureDescriptor30 = projectFeatureFactory.createNewProjectFeature("versionedSettings", params, project30.getProjectId());
    project30.addFeature(featureDescriptor30);

    Map<String, String> params2 = CollectionsUtil.asMap("buildSettings", "ALWAYS_USE_CURRENT",
                                                        "rootId", vcsRoot20.getExternalId(),
                                                        "showChanges", "true");
    SProjectFeatureDescriptor featureDescriptor40 = projectFeatureFactory.createNewProjectFeature("versionedSettings", params2, project40.getProjectId());
    project30.addFeature(featureDescriptor40);

    Map<String, String> params3 = CollectionsUtil.asMap("enabled", "false");
    SProjectFeatureDescriptor featureDescriptor60 = projectFeatureFactory.createNewProjectFeature("versionedSettings", params3, project60.getProjectId());
    project60.addFeature(featureDescriptor60);

    VersionedSettingsManager versionedSettingsManager = myFixture.getSingletonService(VersionedSettingsManager.class);

    {
      VcsRootInstance versionedSettingsVcsRoot_p30 = versionedSettingsManager.getVersionedSettingsVcsRootInstance(project30);
      check(null, versionedSettingsVcsRoot_p30);
      check("affectedProject:(id:" + project20.getExternalId() + ")", versionedSettingsVcsRoot_p30);
      check("affectedProject:(id:" + project30.getExternalId() + ")", versionedSettingsVcsRoot_p30);
      check("affectedProject:(id:" + project40.getExternalId() + ")", versionedSettingsVcsRoot_p30);
    }

    project20.addParameter(new SimpleParameter("param", "p20"));
    project30.addParameter(new SimpleParameter("param", "p30"));
    project40.addParameter(new SimpleParameter("param", "p40"));


    BuildTypeEx p40_bt10 = project40.createBuildType("p40_bt10");
    p40_bt10.addParameter(new SimpleParameter("param", "bt"));
    BuildTypeEx p40_bt20 = project40.createBuildType("p40_bt20");
    BuildTypeEx p40_bt30 = project40.createBuildType("p40_bt30");

    {
      VcsRootInstance versionedSettingsVcsRoot_p30 = versionedSettingsManager.getVersionedSettingsVcsRootInstance(project30);
      VcsRootInstance versionedSettingsVcsRoot_p40 = versionedSettingsManager.getVersionedSettingsVcsRootInstance(project40);
      VcsRootInstance btInstance10 = attachVcsRoot(p40_bt10, vcsRoot20);
      VcsRootInstance btInstance20 = attachVcsRoot(p40_bt20, vcsRoot20);
      VcsRootInstance btInstance30 = attachVcsRoot(p40_bt20, vcsRoot30);
      assert btInstance20.equals(versionedSettingsVcsRoot_p40);

      check(null, versionedSettingsVcsRoot_p30, versionedSettingsVcsRoot_p40, btInstance10, btInstance30);
      check("property:(name:aaa,value:p30)", versionedSettingsVcsRoot_p30);
      check("buildType:(id:" + p40_bt10.getExternalId() + ")", btInstance10);
      check("buildType:(id:" + p40_bt20.getExternalId() + ")", versionedSettingsVcsRoot_p40, btInstance30);
      check("buildType:(id:" + p40_bt30.getExternalId() + ")");

      check("buildType:(id:" + p40_bt10.getExternalId() + "),versionedSettings:any", btInstance10); //documenting current behavior, seems like incorrect
      check("buildType:(id:" + p40_bt10.getExternalId() + "),versionedSettings:false", btInstance10);
      check("buildType:(id:" + p40_bt10.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p40);

      check("buildType:(id:" + p40_bt20.getExternalId() + "),versionedSettings:any", versionedSettingsVcsRoot_p40, btInstance30);
      check("buildType:(id:" + p40_bt20.getExternalId() + "),versionedSettings:false", versionedSettingsVcsRoot_p40, btInstance30);
      check("buildType:(id:" + p40_bt20.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p40);

      check("buildType:(id:" + p40_bt30.getExternalId() + "),versionedSettings:any");  //documenting current behavior, seems like incorrect
      check("buildType:(id:" + p40_bt30.getExternalId() + "),versionedSettings:false");
      check("buildType:(id:" + p40_bt30.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p40);

      check("buildType:(id:" + p40_bt10.getExternalId() + "),vcsRoot:(id:" + vcsRoot20.getExternalId() + "),versionedSettings:false", btInstance10);
      check("buildType:(id:" + p40_bt10.getExternalId() + "),vcsRoot:(id:" + vcsRoot20.getExternalId() + "),versionedSettings:any", btInstance10);
      check("buildType:(id:" + p40_bt10.getExternalId() + "),vcsRoot:(id:" + vcsRoot20.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p40);

      check("buildType:(id:" + p40_bt20.getExternalId() + "),vcsRoot:(id:" + vcsRoot20.getExternalId() + "),versionedSettings:false", versionedSettingsVcsRoot_p40);
      check("buildType:(id:" + p40_bt20.getExternalId() + "),vcsRoot:(id:" + vcsRoot20.getExternalId() + "),versionedSettings:any", versionedSettingsVcsRoot_p40);
      check("buildType:(id:" + p40_bt20.getExternalId() + "),vcsRoot:(id:" + vcsRoot20.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p40);
      check("buildType:(id:" + p40_bt20.getExternalId() + "),vcsRoot:(id:" + vcsRoot30.getExternalId() + "),versionedSettings:false", btInstance30);
      check("buildType:(id:" + p40_bt20.getExternalId() + "),vcsRoot:(id:" + vcsRoot30.getExternalId() + "),versionedSettings:any", btInstance30);
      check("buildType:(id:" + p40_bt20.getExternalId() + "),vcsRoot:(id:" + vcsRoot30.getExternalId() + "),versionedSettings:true"); 

      p40_bt20.removeVcsRoot(vcsRoot30);


//      check("project:(id:" + project20.getExternalId() + ")");
      check("project:(id:" + project30.getExternalId() + ")", versionedSettingsVcsRoot_p30);
      check("project:(id:" + project40.getExternalId() + ")", versionedSettingsVcsRoot_p40);
      check("project:(id:" + project50.getExternalId() + ")", versionedSettingsVcsRoot_p40);
      check("project:(id:" + project60.getExternalId() + ")");

      check("affectedProject:(id:" + project20.getExternalId() + ")", versionedSettingsVcsRoot_p30, versionedSettingsVcsRoot_p40, btInstance10);
      check("affectedProject:(id:" + project30.getExternalId() + ")", versionedSettingsVcsRoot_p30, versionedSettingsVcsRoot_p40, btInstance10);
      check("affectedProject:(id:" + project40.getExternalId() + ")", versionedSettingsVcsRoot_p40, btInstance10);

      check("affectedProject:(id:" + project20.getExternalId() + "),versionedSettings:any", versionedSettingsVcsRoot_p30, versionedSettingsVcsRoot_p40, btInstance10);
      check("affectedProject:(id:" + project20.getExternalId() + "),versionedSettings:false", versionedSettingsVcsRoot_p40, btInstance10);
      check("affectedProject:(id:" + project20.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p30, versionedSettingsVcsRoot_p40);

      final ProjectEx project70 = project40.createProject("project70", "Project name 70");
      project70.addParameter(new SimpleParameter("param", "bt"));
      VcsRootInstance versionedSettingsVcsRoot_p70 = versionedSettingsManager.getVersionedSettingsVcsRootInstance(project70);
      assert versionedSettingsVcsRoot_p70 != null;
      assert versionedSettingsVcsRoot_p70.equals(btInstance10);

      check("buildType:(id:" + p40_bt10.getExternalId() + ")", btInstance10);
      check("project:(id:" + project40.getExternalId() + ")", versionedSettingsVcsRoot_p40);
      check("project:(id:" + project70.getExternalId() + ")", btInstance10);
      check("project:(id:" + project70.getExternalId() + "),versionedSettings:true", btInstance10);
      check("project:(id:" + project70.getExternalId() + "),versionedSettings:false");
      check("affectedProject:(id:" + project40.getExternalId() + ")", versionedSettingsVcsRoot_p40, btInstance10);
      check("affectedProject:(id:" + project40.getExternalId() + "),versionedSettings:any", versionedSettingsVcsRoot_p40, btInstance10);
      check("affectedProject:(id:" + project40.getExternalId() + "),versionedSettings:false", btInstance20, btInstance10);
      check("affectedProject:(id:" + project40.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p40, btInstance10);
      check("affectedProject:(id:" + project70.getExternalId() + "),versionedSettings:false");
      check("affectedProject:(id:" + project70.getExternalId() + "),versionedSettings:true", versionedSettingsVcsRoot_p70);
    }
  }

  @Test
  public void testHelp() throws Exception {
    String message = checkException(LocatorProcessException.class, () -> getFinder().getItems("$help"), null).getMessage();
    assertNotContains(message, "Invalid single value", false);

    message = checkException(LocatorProcessException.class, () -> getFinder().getItem("$help"), null).getMessage();
    assertNotContains(message, "Invalid single value", false);
  }
}
