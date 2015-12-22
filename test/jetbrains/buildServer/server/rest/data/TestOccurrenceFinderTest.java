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

import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 16/12/2015
 */
public class TestOccurrenceFinderTest extends BaseFinderTest<STestRun> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProject.remove();

    setFinder(myTestOccurrenceFinder);
  }

  @Test
  public void testBasic() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType).withTest("aaa", true).finish();
//todo: why error?    final SFinishedBuild build10 = build().in(myBuildType).withTest("aaa", true).finish();

    checkExceptionOnItemSearch(BadRequestException.class, "No_match");
    checkExceptionOnItemsSearch(BadRequestException.class, "No_match");
    check("build:(id:" + build10.getBuildId() + ")", build10.getFullStatistics().getAllTests().get(0));
  }
}
