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

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.controllers.project.VersionedSettingsConfigUpdater;
import jetbrains.buildServer.controllers.project.VersionedSettingsDslContextParameters;
import jetbrains.buildServer.controllers.project.VersionedSettingsTokensControllerHelper;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.data.versionedSettings.VersionedSettingsBeanCollector;
import jetbrains.buildServer.server.rest.request.VersionedSettingsRequest;
import jetbrains.buildServer.server.rest.service.impl.versionedSettings.VersionedSettingsConfigsServiceImpl;
import jetbrains.buildServer.server.rest.service.impl.versionedSettings.VersionedSettingsDslParametersServiceImpl;
import jetbrains.buildServer.server.rest.service.impl.versionedSettings.VersionedSettingsTokensServiceImpl;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsConfigsService;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsDslParametersService;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsTokensService;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.ProjectPersistingHandler;
import jetbrains.buildServer.serverSide.ProjectVcsRoots;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.auth.SecuredProjectManager;
import jetbrains.buildServer.serverSide.impl.auth.SecuredVersionedSettingsManager;
import jetbrains.buildServer.serverSide.impl.projects.ProjectCredentialsStorage;
import jetbrains.buildServer.serverSide.impl.versionedSettings.*;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.WaitFor;
import jetbrains.buildServer.vcs.ChangesCheckingService;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsSupportContext;
import jetbrains.buildServer.vcs.impl.RepositoryStateManager;
import jetbrains.vcs.api.impl.VcsContextLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;

public class VersionedSettingsRequestBaseTestCase extends BaseFinderTest<SProject> {

  protected VersionedSettingsRequest myRequest;
  protected MockVcsSupport myMockVcsSupport;
  protected VersionedSettingsManager myVersionedSettingsManager;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    SecuredProjectManager securedProjectManager = new SecuredProjectManager(myServer.getSecurityContext());
    securedProjectManager.setDelegate(myProjectManager);
    myProjectManager = securedProjectManager;
    initFinders();
    myVersionedSettingsManager = new SecuredVersionedSettingsManager(myFixture.getSingletonService(VersionedSettingsManager.class), myServer.getSecurityContext());
    VersionedSettingsBeanCollector versionedSettingsBeanCollector = new VersionedSettingsBeanCollector(
      myVersionedSettingsManager,
      myFixture.getSingletonService(CurrentVersionTracker.class),
      myFixture.getSingletonService(VersionedSettingsStatusTracker.class),
      myFixture.getVcsManager(),
      new VcsContextLocator() {
        @Nullable
        @Override
        public VcsSupportContext findVcsSupportContext(@NotNull String vcsName) {
          return myMockVcsSupport;
        }
      },
      myProjectManager,
      myFixture.getSingletonService(ConverterChangesStorage.class),
      myFixture.getSingletonService(OutdatedProjectSettingsHealthReport.class)
    );
    VersionedSettingsTokensService versionedSettingsTokensService = new VersionedSettingsTokensServiceImpl(
      new VersionedSettingsTokensControllerHelper(myFixture.getSingletonService(ProjectCredentialsStorage.class)),
      myPermissionChecker
    );
    VersionedSettingsConfigUpdater versionedSettingsConfigUpdater = new VersionedSettingsConfigUpdater(
      myProjectManager,
      myFixture.getBuildPromotionManager(),
      myFixture.getConfigActionFactory(),
      myVersionedSettingsManager,
      myFixture.getSingletonService(VersionedSettingsStatusTracker.class),
      myFixture.getSingletonService(ChangesCheckingService.class),
      myFixture.getSingletonService(RepositoryStateManager.class),
      myFixture.getSingletonService(ProjectVcsRoots.class),
      myFixture.getVcsRootInstancesManager(),
      myFixture.getSingletonService(VersionedSettingsUpdater.class),
      myFixture.getSingletonService(VersionedSettingsOptions.class),
      myFixture.getSingletonService(VersionedSettingsCommitErrorHealthReport.class),
      myFixture.getSingletonService(SettingsCommitStrategy.class),
      myFixture.getSingletonService(ProjectPersistingHandler.class),
      myFixture.getSingletonService(VersionedSettingsPendingDeletes.class),
      myFixture.getVcsAccessFactory()
    );
    VersionedSettingsConfigsService versionedSettingsConfigProvider = new VersionedSettingsConfigsServiceImpl(
      versionedSettingsConfigUpdater,
      versionedSettingsBeanCollector,
      myPermissionChecker
    );
    VersionedSettingsDslContextParameters versionedSettingsDslContextParameters = new VersionedSettingsDslContextParameters(
      myVersionedSettingsManager,
      myFixture.getSingletonService(RepositoryStateManager.class),
      myFixture.getSingletonService(VersionedSettingsStatusTracker.class),
      myFixture.getSingletonService(VersionedSettingsUpdater.class),
      myFixture.getServerAccessChecker()
    );
    VersionedSettingsDslParametersService versionedSettingsDslParametersService = new VersionedSettingsDslParametersServiceImpl(
      versionedSettingsDslContextParameters,
      versionedSettingsBeanCollector
    );
    myRequest = new TestVersionedSettingsRequest(myFixture,
                                                 myProjectFinder,
                                                 versionedSettingsBeanCollector,
                                                 myPermissionChecker,
                                                 versionedSettingsTokensService,
                                                 versionedSettingsConfigProvider,
                                                 versionedSettingsDslParametersService);
    myMockVcsSupport = vcsSupport().withName("vcs_support").register();
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);
  }

  protected void completePendingTransactions(@NotNull final VcsRootInstance root) {
    final VersionedSettingsPendingTransactions pendingTransactions = myFixture.getSingletonService(VersionedSettingsPendingTransactions.class);
    completePendingTransactions(pendingTransactions, root);
  }

  protected void completePendingTransactions(@NotNull final VersionedSettingsPendingTransactions pendingTransactions, @NotNull final VcsRootInstance root) {
    myFixture.waitForPersistTasksCompletion();
    new WaitFor() {
      @Override
      protected boolean condition() {
        return !pendingTransactions.hasPendingTransactions(root);
      }
    };
  }

  protected void loginAsUserWithOnlyViewProjectPermission() {
    SUser user = createUser("user");
    user.addRole(RoleScope.projectScope(myProject.getProjectId()), getTestRoles().getProjectViewerRole());
    makeLoggedIn(user);
  }

  protected class TestVersionedSettingsRequest extends VersionedSettingsRequest {

    public TestVersionedSettingsRequest(@NotNull ServiceLocator serviceLocator,
                                        @NotNull ProjectFinder projectFinder,
                                        @NotNull VersionedSettingsBeanCollector versionedSettingsBeanCollector,
                                        @NotNull PermissionChecker permissionChecker,
                                        @NotNull VersionedSettingsTokensService versionedSettingsTokensService,
                                        @NotNull VersionedSettingsConfigsService versionedSettingsConfigProvider,
                                        @NotNull VersionedSettingsDslParametersService versionedSettingsDslParametersService) {
      initForTests(new ApiUrlBuilder(p -> p), serviceLocator, new BeanFactory(null), projectFinder, versionedSettingsBeanCollector,
                   permissionChecker, versionedSettingsTokensService, versionedSettingsConfigProvider, versionedSettingsDslParametersService);
    }
  }
}
