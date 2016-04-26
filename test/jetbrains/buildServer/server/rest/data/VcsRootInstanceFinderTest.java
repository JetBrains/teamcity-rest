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
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.util.CollectionsUtil;
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

  private VcsRootInstance attachVcsRoot(final SBuildType buildType, final SVcsRoot vcsRoot) {
    buildType.addVcsRoot(vcsRoot);
    return buildType.getVcsRootInstanceForParent(vcsRoot);
  }
}
