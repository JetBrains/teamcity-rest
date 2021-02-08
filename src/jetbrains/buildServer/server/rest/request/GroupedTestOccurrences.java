/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.util.NamedDataGroup;
import jetbrains.buildServer.web.problems.GroupedTestsBean;
import jetbrains.buildServer.web.problems.STestBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "groupedTestOccurrences")
@XmlType(name = "groupedTestOccurrences", propOrder = {
  "count",
  "testOccurrences"
})
public class GroupedTestOccurrences {
  @Nullable
  private final Map<String, TestOccurrences> myEntries;

  @Used("javax.xml")
  public GroupedTestOccurrences() {
    myEntries = null;
  }

  public GroupedTestOccurrences(@NotNull final List<STestRun> testRuns,
                                @NotNull final Fields fields,
                                @NotNull final BeanContext beanContext,
                                int depth) {
    myEntries = ValueWithDefault.decideDefault(fields.isIncluded("testOccurrences", true, true), () -> {
      final HashMap<String, TestOccurrences> map = new HashMap<>();
      List<? extends NamedDataGroup<STestBean>> groups = GroupedTestsBean.createForTests(testRuns, null).getPackagesRoot().getGroups();
      for (int i = 1; i < depth && !groups.isEmpty(); i++) {
        groups.forEach(g -> map.put(g.getName(), new TestOccurrences(g.getItems().stream().map(STestBean::getRun).collect(Collectors.toList()),
                                                                     null,
                                                                     null,
                                                                     null,
                                                                     fields.getNestedField("testOccurrences"),
                                                                     beanContext)));
        groups = groups.stream().flatMap(sTestBeanNamedDataGroup -> sTestBeanNamedDataGroup.getGroups().stream()).collect(Collectors.toList());
      }
      return map;
    });
  }

  @XmlElement(name = "count")
  public Integer getCount() {
    return myEntries.size();
  }

  @XmlElement(name = "testOccurrences")
  public Map<String, TestOccurrences> getTestOccurrences() {
    return myEntries;
  }
}