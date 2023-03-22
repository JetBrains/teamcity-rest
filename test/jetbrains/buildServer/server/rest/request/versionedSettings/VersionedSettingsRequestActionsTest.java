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

package jetbrains.buildServer.server.rest.request.versionedSettings;

import java.util.function.Supplier;
import jetbrains.buildServer.controllers.project.VersionedSettingsActions;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.ConfigActionFactoryEx;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsOptions;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatusTracker;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsUpdateConfig;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsUpdater;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.RepositoryStateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Mock;
import org.jmock.core.Invocation;
import org.jmock.core.stub.CustomStub;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class VersionedSettingsRequestActionsTest extends VersionedSettingsRequestBaseTestCase {

  private VersionedSettingsUpdaterProxy myVersionedSettingsUpdaterProxy;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    setInternalProperty("teamcity.versionedSettings.commitDelaySeconds", "0");
    VersionedSettingsUpdater versionedSettingsUpdater = myFixture.getSingletonService(VersionedSettingsUpdater.class);
    myVersionedSettingsUpdaterProxy = new VersionedSettingsUpdaterProxy(versionedSettingsUpdater);
    myFixture.addService(new VersionedSettingsActions(myVersionedSettingsManager,
                                                      myFixture.getSingletonService(RepositoryStateManager.class),
                                                      myFixture.getSingletonService(VersionedSettingsStatusTracker.class),
                                                      myFixture.getSingletonService(VersionedSettingsOptions.class),
                                                      myVersionedSettingsUpdaterProxy,
                                                      myFixture.getSingletonService(ConfigActionFactoryEx.class),
                                                      myFixture.getSingletonService(ChangesCheckingService.class),
                                                      myServer.getSecurityContext()));
  }

  @Test
  public void testCommitSettings() {
    doTestCommitSettings();
  }

  @Test(expectedExceptions = AccessDeniedException.class)
  public void testCommitSettingsWithNoUser() {
    loginAsUserWithOnlyViewProjectPermission();
    doTestCommitSettings();
  }

  private void doTestCommitSettings() {
    boolean[] isInvoked = new boolean[1];
    Mock mock = mock(CommitPatchBuilder.class);
    mock.stubs().method(ANYTHING).withAnyArguments().will(returnValue(null));
    mock.stubs().method("commit").withAnyArguments().will(new CustomStub("commit") {
      @Override
      public Object invoke(Invocation invocation) {
        isInvoked[0] = true;
        return CommitResult.createSuccessResult("1");
      }
    });
    myMockVcsSupport.addCustomVcsExtension(CommitSupport.class, new CommitSupport() {

      @NotNull
      @Override
      public CommitPatchBuilder getCommitPatchBuilder(@NotNull VcsRoot root) {
        return (CommitPatchBuilder) mock.proxy();
      }
    });
    SVcsRoot vcsRoot = myProject.createVcsRoot(myMockVcsSupport.getName(), "ext", "name");
    myFixture.enableVersionedSettings(myProject, vcsRoot);
    completePendingTransactions(resolveInProject(vcsRoot, myProject));

    isInvoked[0] = false;
    myRequest.commitCurrentSettings(myProject.getExternalId());
    completePendingTransactions(resolveInProject(vcsRoot, myProject));
    assertTrue(isInvoked[0]);
  }

  @Test
  public void testLoadSettingsFromVCS() {
    makeLoggedIn(createAdmin("admin"));
    doTestUpdateFromVcs();
  }

  @Test(expectedExceptions = AccessDeniedException.class)
  public void testLoadSettingsFromVCSWithNoUser() {
    loginAsUserWithOnlyViewProjectPermission();
    doTestUpdateFromVcs();
  }

  private void doTestUpdateFromVcs() {
    SVcsRoot vcsRoot = myProject.createVcsRoot(myMockVcsSupport.getName(), "ext", "name");
    myFixture.enableVersionedSettings(myProject, vcsRoot);
    completePendingTransactions(resolveInProject(vcsRoot, myProject));

    Projects projectsToLoad = myRequest.getProjectsToLoad(myProject.getExternalId(), null);
    assertEquals(projectsToLoad.projects.get(0).id, myProject.getExternalId());

    myVersionedSettingsUpdaterProxy.isUpdateCalled = false;
    Projects loadedProjects = myRequest.loadProjectsFromVcs(myProject.getExternalId(), null);
    assertEquals(loadedProjects.projects.get(0).id, myProject.getExternalId());
    assertTrue(myVersionedSettingsUpdaterProxy.isUpdateCalled);
  }

  private class VersionedSettingsUpdaterProxy implements VersionedSettingsUpdater {

    private final VersionedSettingsUpdater myVersionedSettingsUpdater;
    private boolean isUpdateCalled = false;

    VersionedSettingsUpdaterProxy(VersionedSettingsUpdater versionedSettingsUpdater) {
      myVersionedSettingsUpdater = versionedSettingsUpdater;
    }

    @Override
    public void updateFromVcs(@NotNull VersionedSettingsUpdateConfig config, @Nullable Supplier<String> skipReasonSupplier) {
      isUpdateCalled = true;
      myVersionedSettingsUpdater.updateFromVcs(config, skipReasonSupplier);
    }

    @Override
    public void scheduleUpdateFromVcs(@NotNull VersionedSettingsUpdateConfig config, @Nullable Supplier<String> skipReasonSupplier) {
      isUpdateCalled = true;
      myVersionedSettingsUpdater.scheduleUpdateFromVcs(config, skipReasonSupplier);
    }

    @Override
    public boolean isSettingsUpdateInProgress(@NotNull String projectExtId) {
      return myVersionedSettingsUpdater.isSettingsUpdateInProgress(projectExtId);
    }

    @Override
    public void runAsUpdateFromVcs(@NotNull String projectExtId, @NotNull Runnable action) {
      myVersionedSettingsUpdater.runAsUpdateFromVcs(projectExtId, action);
    }
  }
}
