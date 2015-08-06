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

import java.util.HashMap;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 24/07/2015
 */
public class BuildTest extends BaseServerTestCase {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    initializeFinders();
  }

  private void initializeFinders() {
    final PermissionChecker permissionChecker = new PermissionChecker(myServer.getSecurityContext());
    myFixture.addService(permissionChecker);

    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager, permissionChecker, myServer);
    myFixture.addService(projectFinder);

    final AgentFinder agentFinder = new AgentFinder(myAgentManager);
    myFixture.addService(agentFinder);

    final BuildTypeFinder buildTypeFinder = new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, permissionChecker, myFixture);
    myFixture.addService(buildTypeFinder);

    final UserFinder userFinder = new UserFinder(myFixture);
    myFixture.addService(userFinder);
  }

  @Test
  public void testBuildTriggering1() {
    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    build.setBuildType(buildType);

    final SUser triggeringUser = getOrCreateUser("user");
    final SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
    assertEquals(myBuildType, queuedBuild.getBuildPromotion().getBuildType());
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void testBuildTriggering2() {
    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    buildType.setPaused(true); //this generates BadRequestException
    build.setBuildType(buildType);

    final SUser triggeringUser = getOrCreateUser("user");

    build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
  }
}
