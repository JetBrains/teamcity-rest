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
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.request.ProblemOccurrenceRequest;
import jetbrains.buildServer.server.rest.request.ProblemRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problem")
@XmlType(name = "problem", propOrder = {"id", "type", "identity", "href",
  "mutes", "investigations", "problemOccurrences"})
public class Problem {
  @XmlAttribute public String id;
  @XmlAttribute public String type;
  @XmlAttribute public String identity;
  @XmlAttribute public String href;

  @XmlElement public Mutes mutes;
  @XmlElement public Investigations investigations;
  @XmlElement public ProblemOccurrences problemOccurrences;

  public Problem() {
  }

  public Problem(final @NotNull ProblemWrapper problem,
                 final @NotNull Fields fields,
                 final @NotNull BeanContext beanContext) {
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), String.valueOf(problem.getId()));

    type = ValueWithDefault.decideDefault(fields.isIncluded("type"), problem.getType());
    identity = ValueWithDefault.decideDefault(fields.isIncluded("identity"), problem.getIdentity());
    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(ProblemRequest.getHref(problem)));

    mutes = ValueWithDefault.decideDefault(fields.isIncluded("mutes", false), new ValueWithDefault.Value<Mutes>() {
      public Mutes get() {
        return new Mutes(problem.getMutes(), null, null, fields.getNestedField("mutes"), beanContext);
      }
    });
    investigations = ValueWithDefault.decideDefault(fields.isIncluded("investigations", false), new ValueWithDefault.Value<Investigations>() {
      public Investigations get() {
        return new Investigations(problem.getInvestigations(), new Href(InvestigationRequest.getHref(problem), beanContext.getApiUrlBuilder()),
                                  fields.getNestedField("investigations"), null, beanContext);
      }
    });
    problemOccurrences = ValueWithDefault.decideDefault(fields.isIncluded("problemOccurrences", false), new ValueWithDefault.Value<ProblemOccurrences>() {
      public ProblemOccurrences get() {
        //todo: add support for locator + filter here, like for builds in BuildType
        return new ProblemOccurrences(null, null, null, null, null, null, null, ProblemOccurrenceRequest.getHref(problem), null, fields.getNestedField("problemOccurrences"),
                                      beanContext);
      }
    });
  }
}
