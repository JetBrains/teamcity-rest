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

package jetbrains.buildServer.server.rest.model.pages.problems;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.pages.problems.SingleTestMuteInfoView;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.model.problem.Mutes;
import jetbrains.buildServer.server.rest.model.problem.Test;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name="testFailuresProblemEntry")
public class TestFailuresProblemEntry {
  private Boolean myNewFailure;
  private BuildTypes myFailingBuildTypes;
  private TestOccurrences myRecentFailures;
  private Test myTest;
  private Investigations myInvestigations;
  private Mutes myMutes;

  public TestFailuresProblemEntry() { }

  public TestFailuresProblemEntry(@NotNull jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry data,
                                  @NotNull Fields fields,
                                  @NotNull BeanContext beanContext) {
    myTest = ValueWithDefault.decideDefault(
      fields.isIncluded("test", true, true),
      () -> resolveTest(data, fields.getNestedField("test"), beanContext)
    );

    myInvestigations = ValueWithDefault.decideDefault(
      fields.isIncluded("investigations", true, true),
      () -> resolveInvestigations(data, fields.getNestedField("investigations"), beanContext)
    );

    myMutes = ValueWithDefault.decideDefault(
      fields.isIncluded("mutes", true, true),
      () -> resolveMutes(data, fields.getNestedField("mutes"), beanContext)
    );

    myRecentFailures = ValueWithDefault.decideDefault(
      fields.isIncluded("recentFailures", true, true),
      () -> resolveRecentFailures(data, fields.getNestedField("recentFailures"), beanContext)
    );

    myFailingBuildTypes = ValueWithDefault.decideDefault(
      fields.isIncluded("failingBuildTypes", false, false),
      () -> resolveFailingBuildTypes(data, fields.getNestedField("failingBuildTypes"), beanContext)
    );

    myNewFailure = ValueWithDefault.decideDefault(
      fields.isIncluded("newFailure", false, false),
      () -> data.isNewFailure()
    );
  }

  @XmlElement(name = "test")
  public Test getTest() {
    return myTest;
  }

  @XmlElement(name = "investigations")
  public Investigations getInvestigations() {
    return myInvestigations;
  }

  @XmlElement(name = "mutes")
  public Mutes getMutes() {
    return myMutes;
  }

  @XmlElement(name = "recentFailures")
  public TestOccurrences getRecentFailures() {
    return myRecentFailures;
  }

  @XmlElement(name = "failingBuildTypes")
  public BuildTypes getFailingBuildTypes() {
    return myFailingBuildTypes;
  }

  @XmlAttribute(name = "newFailure")
  public Boolean getNewFailure() {
    return myNewFailure;
  }

  @NotNull
  private Test resolveTest(@NotNull jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry data,
                           @NotNull Fields fields,
                           @NotNull BeanContext beanContext) {
    return new Test(data.getTest(), beanContext, fields);
  }

  @Nullable
  private Investigations resolveInvestigations(@NotNull jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry data,
                                               @NotNull Fields fields,
                                               @NotNull BeanContext beanContext) {
    if(data.getInvestigations() == null) {
      return null;
    }

    return new Investigations(data.getInvestigations(), null, fields, beanContext);
  }

  @Nullable
  private Mutes resolveMutes(@NotNull jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry data,
                             @NotNull Fields fields,
                             @NotNull BeanContext beanContext) {
    if(data.getMutes() == null) {
      return null;
    }

    List<MuteInfo> extractedMutes = data.getMutes().stream()
                                        .map(SingleTestMuteInfoView::getMuteInfo)
                                        .collect(Collectors.toList());

    return new Mutes(extractedMutes, null, fields, beanContext);
  }


  @Nullable
  private TestOccurrences resolveRecentFailures(@NotNull jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry data,
                                                @NotNull Fields fields,
                                                @NotNull BeanContext beanContext) {
    if(data.getRecentFailures() == null) {
      return null;
    }

    return new TestOccurrences(data.getRecentFailures(), null, null, null, fields, beanContext);
  }

  @Nullable
  private BuildTypes resolveFailingBuildTypes(@NotNull jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry data,
                                              @NotNull Fields fields,
                                              @NotNull BeanContext beanContext) {
    Set<SBuildType> failingBuildTypes = data.getFailingBuildTypes();
    if(failingBuildTypes == null) {
      return null;
    }

    List<BuildTypeOrTemplate> buildTypes = failingBuildTypes.stream()
                                                  .map(BuildTypeOrTemplate::new)
                                                  .collect(Collectors.toList());

    return new BuildTypes(buildTypes, null, fields, beanContext);
  }
}
