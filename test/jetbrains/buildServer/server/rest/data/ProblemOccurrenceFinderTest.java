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

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 16/12/2015
 */
public class ProblemOccurrenceFinderTest extends BaseFinderTest<BuildProblem> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProject.remove();

    setFinder(myProblemOccurrenceFinder);
  }

  @Test
  public void testBasic() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType).withProblem(createBuildProblem()).finish();

    checkExceptionOnItemSearch(BadRequestException.class, "No_match");
    checkExceptionOnItemsSearch(BadRequestException.class, "No_match");
    check("build:(id:" + build10.getBuildId() + ")", ((BuildPromotionEx)build10.getBuildPromotion()).getBuildProblems().get(0));
  }

  @Test
  public void testByProblem() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final BuildPromotionEx build10 = (BuildPromotionEx)build().in(buildType)
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type3", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build15 = (BuildPromotionEx)build().in(buildType).finish().getBuildPromotion();
    final BuildPromotionEx build20 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build30 = (BuildPromotionEx)build().in(buildType)
                                                              .withProblem(BuildProblemData.createBuildProblem("id2", "type1", "descr"))
                                                              .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build40 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();

    checkProblem("problem:(id:1)",
                 pd(1, "id1", "type1", build20.getId()),
                 pd(1, "id1", "type1", build10.getId()));
    checkProblem("problem:(currentlyFailing:true)",
                 pd(2, "id1", "type2", build40.getId()),
                 pd(2, "id1", "type2", build30.getId()),
                 pd(2, "id1", "type2", build10.getId()));
    checkProblem("problem:(build:(count:1))",
                 pd(2, "id1", "type2", build40.getId()),
                 pd(2, "id1", "type2", build30.getId()),
                 pd(2, "id1", "type2", build10.getId()));
    checkProblem("problem:(build:(count:2))",
                 pd(2, "id1", "type2", build40.getId()),
                 pd(2, "id1", "type2", build30.getId()),
                 pd(2, "id1", "type2", build10.getId()),
                 pd(4, "id2", "type1", build30.getId()));
  }

  @Test
  public void testByBuild() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final BuildPromotionEx build10 = (BuildPromotionEx)build().in(buildType)
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type3", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build15 = (BuildPromotionEx)build().in(buildType).finish().getBuildPromotion();
    final BuildPromotionEx build20 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build30 = (BuildPromotionEx)build().in(buildType)
                                                              .withProblem(BuildProblemData.createBuildProblem("id2", "type1", "descr"))
                                                              .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build40 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();

    checkProblem("build:(id:" + build40.getId() + ")", pd(2, "id1", "type2", build40.getId()));
    checkProblem("build:(id:" + build10.getId() + ")",
                 pd(1, "id1", "type1", build10.getId()),
                 pd(2, "id1", "type2", build10.getId()),
                 pd(3, "id1", "type3", build10.getId()));
    checkProblem("build:(item:(id:" + build10.getId() + "),item:(id:" + build30.getId() + "))",
                 pd(1, "id1", "type1", build10.getId()),
                 pd(2, "id1", "type2", build10.getId()),
                 pd(3, "id1", "type3", build10.getId()),
                 pd(4, "id2", "type1", build30.getId()),
                 pd(2, "id1", "type2", build30.getId()));
  }

  @Test
  public void testPaging() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final BuildPromotionEx build10 = (BuildPromotionEx)build().in(buildType)
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type3", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build15 = (BuildPromotionEx)build().in(buildType).finish().getBuildPromotion();
    final BuildPromotionEx build20 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build30 = (BuildPromotionEx)build().in(buildType)
                                                              .withProblem(BuildProblemData.createBuildProblem("id2", "type1", "descr"))
                                                              .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build40 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();

    checkProblem("build:(item:(id:" + build10.getId() + "),item:(id:" + build30.getId() + "))",
                 pd(1, "id1", "type1", build10.getId()),
                 pd(2, "id1", "type2", build10.getId()),
                 pd(3, "id1", "type3", build10.getId()),
                 pd(4, "id2", "type1", build30.getId()),
                 pd(2, "id1", "type2", build30.getId()));

    checkProblem("build:(item:(id:" + build10.getId() + "),item:(id:" + build30.getId() + ")),count:2",
                 pd(1, "id1", "type1", build10.getId()),
                 pd(2, "id1", "type2", build10.getId()));

    checkProblem("build:(item:(id:" + build10.getId() + "),item:(id:" + build30.getId() + ")),start:2,count:3",
                 pd(3, "id1", "type3", build10.getId()),
                 pd(4, "id2", "type1", build30.getId()),
                 pd(2, "id1", "type2", build30.getId()));
  }

  @Test
  public void testByIdentity() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final BuildPromotionEx build10 = (BuildPromotionEx)build().in(buildType)
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr"))
                                          .withProblem(BuildProblemData.createBuildProblem("id1", "type3", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build15 = (BuildPromotionEx)build().in(buildType).finish().getBuildPromotion();
    final BuildPromotionEx build20 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type1", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build30 = (BuildPromotionEx)build().in(buildType)
                                                              .withProblem(BuildProblemData.createBuildProblem("id2", "type1", "descr"))
                                                              .withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();
    final BuildPromotionEx build40 = (BuildPromotionEx)build().in(buildType).withProblem(BuildProblemData.createBuildProblem("id1", "type2", "descr")).finish().getBuildPromotion();

    checkProblem("build:(item:(id:" + build30.getId() + "),item:(id:" + build10.getId() + "))",
                 pd(4, "id2", "type1", build30.getId()),
                 pd(2, "id1", "type2", build30.getId()),
                 pd(1, "id1", "type1", build10.getId()),
                 pd(2, "id1", "type2", build10.getId()),
                 pd(3, "id1", "type3", build10.getId()));

    checkProblem("build:(item:(id:" + build30.getId() + "),item:(id:" + build10.getId() + ")),identity:id1",
                 pd(2, "id1", "type2", build30.getId()),
                 pd(1, "id1", "type1", build10.getId()),
                 pd(2, "id1", "type2", build10.getId()),
                 pd(3, "id1", "type3", build10.getId()));
  }

  public void checkProblem(@Nullable final String locator, ProblemData... items) {
    check(locator, new Matcher<ProblemData, BuildProblem>() {
      @Override
      public boolean matches(@NotNull final ProblemData problemData, @NotNull final BuildProblem buildProblem) {
        return problemData.matches(buildProblem);
      }
    }, myProblemOccurrenceFinder, items);
  }

  static ProblemData pd(final int id, final String identity, final String type, final long promotionId){
    return new ProblemData(id, identity, type, promotionId);
  }

  private static class ProblemData {
    private int myId;
    private String myIdentity;
    private String myType;
    private long myPromotionId;

    public ProblemData(final int id, final String identity, final String type, final long promotionId) {
      myId = id;
      myIdentity = identity;
      myType = type;
      myPromotionId = promotionId;
    }

    public boolean matches(final BuildProblem buildProblem) {
      return buildProblem.getId() == myId &&
        buildProblem.getBuildProblemData().getIdentity().equals(myIdentity) &&
        buildProblem.getBuildProblemData().getType().equals(myType) &&
        buildProblem.getBuildPromotion().getId() == myPromotionId
        ;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("ProblemData{");
      sb.append("myId=").append(myId);
      sb.append(", myIdentity='").append(myIdentity).append('\'');
      sb.append(", myType='").append(myType).append('\'');
      sb.append(", myPromotionId=").append(myPromotionId);
      sb.append('}');
      return sb.toString();
    }
  }
}

