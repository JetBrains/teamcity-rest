/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.problem;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.request.ProblemOccurrenceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problemOccurrence")
@XmlType(name = "problemOccurrence")
public class ProblemOccurrence {
  @XmlAttribute public String id;
  @XmlAttribute public String type;
  @XmlAttribute public String identity;
  @XmlAttribute public String href;

  /**
   * Experimental! "true" is the test occurrence was muted, not present otherwise
   */
  @XmlAttribute public Boolean muted;
  /**
   * Experimental! "true" is the test has investigation at the moment of request, not present otherwise
   */
  @XmlAttribute public Boolean currentlyInvestigated;
  /**
   * Experimental! "true" is the test is muted at the moment of request, not present otherwise
   */
  @XmlAttribute public Boolean currentlyMuted;


  @XmlElement public String details;
  @XmlElement public String additionalData;

  @XmlElement public Problem problem;
  @XmlElement public Mute mute;

  @XmlElement public Build build;

  public ProblemOccurrence() {
  }

  public ProblemOccurrence(final @NotNull BuildProblem problemP,
                           final @NotNull BeanContext beanContext,
                           @NotNull final Fields fields) {
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), ProblemOccurrenceFinder.getProblemOccurrenceLocator(problemP));
    type = ValueWithDefault.decideDefault(fields.isIncluded("type"), problemP.getBuildProblemData().getType());
    identity = ValueWithDefault.decideDefault(fields.isIncluded("identity"), problemP.getBuildProblemData().getIdentity());
    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(ProblemOccurrenceRequest.getHref(problemP)));

    final MuteInfo muteInfo = problemP.getMuteInBuildInfo();
    muted = ValueWithDefault.decideDefault(fields.isIncluded("muted"), muteInfo != null);

    currentlyInvestigated = ValueWithDefault.decideDefault(fields.isIncluded("currentlyInvestigated"), new ValueWithDefault.Value<Boolean>() {
      public Boolean get() {
        return !problemP.getAllResponsibilities().isEmpty();
      }
    });

    currentlyMuted = ValueWithDefault.decideDefault(fields.isIncluded("currentlyMuted"), problemP.getCurrentMuteInfo() != null);

    details = ValueWithDefault.decideDefault(fields.isIncluded("details", false), problemP.getBuildProblemData().getDescription());
    additionalData = ValueWithDefault.decideDefault(fields.isIncluded("additionalData", false), problemP.getBuildProblemData().getAdditionalData());

    problem = ValueWithDefault.decideDefault(fields.isIncluded("problem", false), new ValueWithDefault.Value<Problem>() {
      public Problem get() {
        return new Problem(new ProblemWrapper(problemP.getId(), problemP.getBuildProblemData(), beanContext.getServiceLocator()),
                           fields.getNestedField("problem"), beanContext);
      }
    }
    );

    mute = muteInfo == null
           ? null
           : ValueWithDefault.decideDefault(fields.isIncluded("mute", false), new Mute(muteInfo, fields.getNestedField("mute", Fields.NONE, Fields.LONG), beanContext));

    build = ValueWithDefault.decideDefault(fields.isIncluded("build", false), new ValueWithDefault.Value<Build>() {
      public Build get() {
        return new Build(problemP.getBuildPromotion(), fields.getNestedField("build"), beanContext);
      }
    });
  }
}
