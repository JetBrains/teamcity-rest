/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.Problems;
import jetbrains.buildServer.server.rest.model.problem.Tests;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType(name = "problemTarget")
@ModelDescription("Represents an investigation target.")
public class ProblemTarget {

  @XmlAttribute public Boolean anyProblem;
  @XmlElement public Tests tests;
  @XmlElement public Problems problems;

  public ProblemTarget() {
  }

  public ProblemTarget(@NotNull final InvestigationWrapper investigation,
                       @NotNull final Fields fields,
                       @NotNull final BeanContext beanContext) {
    if (investigation.isBuildType()) {
      anyProblem = ValueWithDefault.decideDefault(fields.isIncluded("anyProblem"), true);
      return;
    }

    anyProblem = ValueWithDefault.decideDefault(fields.isIncluded("anyProblem"), false);
    if (investigation.getTestRE() != null) {
      tests = ValueWithDefault.decideDefault(fields.isIncluded("tests", false), () -> {
          @NotNull final TestNameResponsibilityEntry testRE = investigation.getTestRE();

          final STest foundTest = beanContext.getSingletonService(TestFinder.class).findTest(testRE.getTestNameId());
          if (foundTest == null) {
            throw new InvalidStateException("Cannot find test for investigation. Test name id: '" + testRE.getTestNameId() + "'.");
          }
          return new Tests(Collections.singletonList(foundTest), null, beanContext, fields.getNestedField("tests", Fields.NONE, Fields.LONG));
      });
    } else if (investigation.getProblemRE() != null) {
      problems = ValueWithDefault.decideDefault(fields.isIncluded("problems", false), () -> {
          final BuildProblemResponsibilityEntry problemRE = investigation.getProblemRE();
          return new Problems(Collections.singletonList(new ProblemWrapper(problemRE.getBuildProblemInfo().getId(), beanContext.getServiceLocator())),
                              null, fields.getNestedField("problems", Fields.NONE, Fields.LONG), beanContext);
      });
    }
  }

  public ProblemTarget(final @NotNull MuteInfo item,
                       @NotNull final Fields fields,
                       @NotNull final BeanContext beanContext) {
    anyProblem = ValueWithDefault.decideDefault(fields.isIncluded("anyProblem"), false);
    tests = ValueWithDefault.decideDefault(fields.isIncluded("tests", false), () -> {
        return new Tests(item.getTests(), null, beanContext, fields.getNestedField("tests", Fields.NONE, Fields.LONG)); //todo: use TestFinder (ensure sorting) and support $locator
    });
    problems = ValueWithDefault.decideDefault(fields.isIncluded("problems", false), () ->
      new Problems(
        ProblemFinder.getProblemWrappers(item.getBuildProblemIds(), beanContext.getServiceLocator()), //todo: use ProblemFinder (ensure sorting) and support $locator
        null,
        fields.getNestedField("problems", Fields.NONE, Fields.LONG), beanContext
      )
    );
  }

  @NotNull
  public ProblemTargetData getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    ProblemTargetData result = new ProblemTargetData(serviceLocator);
    if (tests == null && problems == null && !result.isAnyProblem()) {
      throw new BadRequestException("Invalid 'target' entity: either 'tests' or 'problems' sub-entities should be specified, or 'anyProblem' attribute should be 'true'");
    }
    if (result.isAnyProblem()) {
      if (tests != null || problems != null) {
        throw new BadRequestException("Invalid 'target' entity: when 'anyProblem' is 'true', 'tests' and 'problems' should not be specified");
      }
    } else if (tests != null && problems != null) {
      throw new BadRequestException("Invalid 'target' entity: when 'tests' is specified, 'problems' should not be specified");
    }
    return result;
  }

  public class ProblemTargetData {
    @NotNull private final ServiceLocator myServiceLocator;

    ProblemTargetData(@NotNull final ServiceLocator serviceLocator) {
      myServiceLocator = serviceLocator;
    }

    public boolean isAnyProblem() {
      return anyProblem != null && anyProblem;
    }

    @NotNull
    public List<STest> getTests() {
      return tests == null ? Collections.emptyList() : tests.getFromPosted(myServiceLocator);
    }

    @NotNull
    public List<Long> getProblemIds() {
      return problems == null ? Collections.emptyList() : problems.getFromPosted(myServiceLocator);
    }
  }

  public static final String TEST_TYPE = "test";
  public static final String PROBLEM_TYPE = "problem";
  public static final String ANY_PROBLEM_TYPE = "anyProblem";
  public static final String UNKNOWN_TYPE = "unknown";

  public static List<String> getKnownTypesForInvestigation() {
    return Arrays.asList(ANY_PROBLEM_TYPE, TEST_TYPE, PROBLEM_TYPE, UNKNOWN_TYPE);
  }

  @NotNull
  public static String getType(@NotNull final InvestigationWrapper investigationWrapper) {
    if (investigationWrapper.isBuildType()) return ANY_PROBLEM_TYPE;
    if (investigationWrapper.isTest()) return TEST_TYPE;
    if (investigationWrapper.isProblem()) return PROBLEM_TYPE;
    return UNKNOWN_TYPE;
  }

  public static String[] getKnownTypesForMute() {
    return new String[]{TEST_TYPE, PROBLEM_TYPE};
  }

  @NotNull
  public static String getType(@NotNull final MuteInfo item) {
    if (!item.getTestNameIds().isEmpty()) return TEST_TYPE;
    if (!item.getBuildProblemIds().isEmpty()) return PROBLEM_TYPE;
    return UNKNOWN_TYPE;
  }
}