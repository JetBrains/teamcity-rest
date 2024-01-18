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
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "testFailuresProblemEntries")
public class TestFailuresProblemEntries {
  private String myPrevHref;
  private String myNextHref;
  private String myHref;
  private Integer myCount;
  private List<TestFailuresProblemEntry> myEntries;

  public TestFailuresProblemEntries() {}

  public TestFailuresProblemEntries(@NotNull List<jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry> data,
                                    @NotNull Fields fields,
                                    @NotNull PagerData pagerData,
                                    @NotNull BeanContext beanContext) {
    myEntries = ValueWithDefault.decideDefault(
      fields.isIncluded("entry", false, true),
      () -> resolveEntries(data, fields.getNestedField("entry"), beanContext)
    );

    myCount = ValueWithDefault.decideDefault(fields.isIncluded("count", true, true), data.size());
    myHref = ValueWithDefault.decideDefault(fields.isIncluded("href"), pagerData.getHref());
    myNextHref = ValueWithDefault.decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref());
    myPrevHref = ValueWithDefault.decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref());
  }

  @NotNull
  private List<TestFailuresProblemEntry> resolveEntries(@NotNull List<jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry> data,
                                                        @NotNull Fields fields,
                                                        @NotNull BeanContext beanContext) {
    return data.stream().map(entry -> new TestFailuresProblemEntry(entry, fields, beanContext)).collect(Collectors.toList());
  }

  @XmlAttribute(name = "count")
  public int getCount() {
    return myCount;
  }

  @XmlElement(name = "entry")
  public List<TestFailuresProblemEntry> getEntries() {
    return myEntries;
  }

  @XmlAttribute(name = "prevHref")
  public String getPrevHref() {
    return myPrevHref;
  }

  @XmlAttribute(name = "nextHref")
  public String getNextHref() {
    return myNextHref;
  }

  @XmlAttribute(name = "href")
  public String getHref() {
    return myHref;
  }
}
