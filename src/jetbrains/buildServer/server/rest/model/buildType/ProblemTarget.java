/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.Collections;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.Problems;
import jetbrains.buildServer.server.rest.model.problem.Tests;
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
      tests = ValueWithDefault.decideDefault(fields.isIncluded("tests", false), new ValueWithDefault.Value<Tests>() {
        public Tests get() {
          @NotNull final TestNameResponsibilityEntry testRE = investigation.getTestRE();

          final STest foundTest = beanContext.getSingletonService(TestFinder.class).findTest(testRE.getTestNameId());
          if (foundTest == null) {
            throw new InvalidStateException("Cannot find test for investigation. Test name id: '" + testRE.getTestNameId() + "'.");
          }
          return new Tests(Collections.singletonList(foundTest), null, beanContext, fields.getNestedField("tests", Fields.NONE, Fields.LONG));
        }
      });
    } else if (investigation.getProblemRE() != null) {
      problems = ValueWithDefault.decideDefault(fields.isIncluded("problems", false), new ValueWithDefault.Value<Problems>() {
        public Problems get() {
          final BuildProblemResponsibilityEntry problemRE = investigation.getProblemRE();
          return new Problems(Collections.singletonList(new ProblemWrapper(problemRE.getBuildProblemInfo().getId(), beanContext.getServiceLocator())),
                              null, fields.getNestedField("problems", Fields.NONE, Fields.LONG), beanContext);
        }
      });
    }
  }

  public ProblemTarget(final @NotNull MuteInfo item,
                       @NotNull final Fields fields,
                       @NotNull final BeanContext beanContext) {
    anyProblem = ValueWithDefault.decideDefault(fields.isIncluded("anyProblem"), false);
    tests = ValueWithDefault.decideDefault(fields.isIncluded("tests", false), new ValueWithDefault.Value<Tests>() {
      public Tests get() {
        return new Tests(item.getTests(), null, beanContext, fields.getNestedField("tests", Fields.NONE, Fields.LONG));
      }
    });
    problems = ValueWithDefault.decideDefault(fields.isIncluded("problems", false), new ValueWithDefault.Value<Problems>() {
      public Problems get() {
        return new Problems(ProblemFinder.getProblemWrappers(item.getBuildProblemIds(), beanContext.getServiceLocator()),
                            null, fields.getNestedField("problems", Fields.NONE, Fields.LONG), beanContext);
      }
    });
  }
}
