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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.model.problem.Mutes;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrence;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "buildProblemEntry")
public class BuildProblemEntry {
  private Mutes myMutes;
  private Build myBuild;
  private ProblemOccurrence myProblemOccurrence;
  private Investigations myInvestigations;

  public BuildProblemEntry() { }

  public BuildProblemEntry(@NotNull jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry problemEntry,
                           @NotNull Fields fields,
                           @NotNull BeanContext beanContext) {
    myProblemOccurrence = ValueWithDefault.decideDefault(
      fields.isIncluded("problemOccurrence"),
      () -> resolveProblemOccurrence(problemEntry.getProblem(), fields.getNestedField("problemOccurrence"), beanContext)
    );
    myInvestigations = ValueWithDefault.decideDefault(
      fields.isIncluded("investigations", false),
      () -> resolveInvestigations(problemEntry.getInvestigations(), fields.getNestedField("investigations"), beanContext)
    );
    myBuild = ValueWithDefault.decideDefault(
      fields.isIncluded("build"),
      () -> resolveBuild(problemEntry.getBuildPromotion(), fields.getNestedField("build"), beanContext)
    );
    myMutes = ValueWithDefault.decideDefault(
      fields.isIncluded("mutes", false),
      () -> resolveMutes(problemEntry.getMuteInfos(), fields.getNestedField("mutes"), beanContext)
    );
  }

  @Nullable
  private Mutes resolveMutes(@Nullable Collection<MuteInfo> muteInfos, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    if(muteInfos == null) {
      return null;
    }

    return new Mutes(muteInfos, null, fields, beanContext);
  }

  @NotNull
  private Build resolveBuild(@NotNull BuildPromotion buildPromotion, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    return new Build(buildPromotion, fields, beanContext);
  }

  @NotNull
  private static ProblemOccurrence resolveProblemOccurrence(@NotNull BuildProblem problem, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    return new ProblemOccurrence(problem, beanContext, fields);
  }

  @Nullable
  private static Investigations resolveInvestigations(@NotNull List<BuildProblemResponsibilityEntry> responsibilityEntries,
                                                      @NotNull Fields fields,
                                                      @NotNull BeanContext beanContext) {
    if(responsibilityEntries.isEmpty()) {
      return null;
    }

    List<InvestigationWrapper> wrappers = responsibilityEntries.stream()
                                                               .map(InvestigationWrapper::new)
                                                               .collect(Collectors.toList());

    return new Investigations(wrappers, null, fields, beanContext);
  }

  @XmlElement(name = "problemOccurrence")
  public ProblemOccurrence getProblemOccurrence() {
    return myProblemOccurrence;
  }

  @XmlElement(name = "investigations")
  public Investigations getInvestigations() {
    return myInvestigations;
  }

  @XmlElement(name = "build")
  public Build getBuild() {
    return myBuild;
  }

  @XmlElement(name = "mutes")
  public Mutes getMutes() {
    return myMutes;
  }
}