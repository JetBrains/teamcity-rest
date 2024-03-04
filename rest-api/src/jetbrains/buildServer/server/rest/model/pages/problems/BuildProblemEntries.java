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

import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "buildProblemEntries")
@XmlType(name = "buildProblemEntries")
public class BuildProblemEntries {
  private String myNextHref;
  private String myPrevHref;
  private String myHref;
  private List<BuildProblemEntry> myEntries;
  private Integer myCount;

  public BuildProblemEntries() { }

  public BuildProblemEntries(@NotNull List<jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry> problemEntries,
                             @NotNull Fields fields,
                             @NotNull PagerDataImpl pager,
                             @NotNull BeanContext beanContext) {
    myEntries = ValueWithDefault.decideDefault(
      fields.isIncluded("entry", false),
      () -> resolveEntries(problemEntries, fields.getNestedField("entry"), beanContext)
    );
    myCount = ValueWithDefault.decideDefault(
      fields.isIncluded("count", true),
      problemEntries.size()
    );
    myHref = ValueWithDefault.decideDefault(
      fields.isIncluded("href", false),
      pager.getHref()
    );
    myNextHref = ValueWithDefault.decideDefault(
      fields.isIncluded("nextHref"),
      pager.getNextHref()
    );
    myPrevHref = ValueWithDefault.decideDefault(
      fields.isIncluded("prevHref"),
      pager.getPrevHref()
    );
  }

  public BuildProblemEntries(@NotNull List<jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry> problemEntries,
                             @NotNull Fields fields,
                             @NotNull BeanContext beanContext) {
    myEntries = ValueWithDefault.decideDefault(
      fields.isIncluded("entry", false),
      () -> resolveEntries(problemEntries, fields.getNestedField("entry"), beanContext)
    );
    myCount = ValueWithDefault.decideDefault(
      fields.isIncluded("count", true),
      problemEntries.size()
    );
  }

  @NotNull
  private static List<BuildProblemEntry> resolveEntries(@NotNull List<jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry> problemEntries,
                                                        @NotNull Fields fields,
                                                        @NotNull BeanContext beanContext) {
    return problemEntries.stream()
                         .map(e -> new BuildProblemEntry(e, fields, beanContext))
                         .collect(Collectors.toList());
  }

  @XmlElement(name = "entry")
  public List<BuildProblemEntry> getEntries() {
    return myEntries;
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }

  @XmlAttribute(name = "nextHref")
  public String getNextHref() {
    return myNextHref;
  }

  @XmlAttribute(name = "prevHref")
  public String getPrevHref() {
    return myPrevHref;
  }

  @XmlAttribute(name = "href")
  public String getHref() {
    return myHref;
  }
}
