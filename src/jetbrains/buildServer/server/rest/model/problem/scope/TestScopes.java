/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.problem.scope;

import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.TestCounters;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "testScopes")
@XmlType(name = "testScopes")
@ModelBaseType(ObjectType.PAGINATED)
public class TestScopes {
  private List<jetbrains.buildServer.server.rest.data.problem.scope.TestScope> myTestScopes;
  private Fields myFields;
  private BeanContext myContext;
  private UriInfo myUriInfo;
  private PagerData myPagerData;

  public TestScopes() { }

  public TestScopes(@NotNull List<jetbrains.buildServer.server.rest.data.problem.scope.TestScope> testScopes, @NotNull Fields fields, @Nullable final PagerData pagerData, @Nullable UriInfo uriInfo, @NotNull BeanContext beanContext) {
    myTestScopes = testScopes;
    myFields = fields;
    myContext = beanContext;
    myUriInfo = uriInfo;
    myPagerData = pagerData;
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count"), myTestScopes.size());
  }

  @XmlElement(name = "testScope")
  public List<TestScope> getScopes() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("testScope"),
      () -> myTestScopes.stream().map(s -> new TestScope(s, myFields.getNestedField("testScope"), myContext, myPagerData)).collect(Collectors.toList())
    );
  }

  @XmlElement(name = "testCounters")
  public TestCounters getTestCounters() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("testCounters"),
      () -> {
        Fields testCounters = myFields.getNestedField("testCounters");
        List<STestRun> runs = myTestScopes.stream().flatMap(scope -> scope.getTestRuns().stream()).collect(Collectors.toList());
        // Will just calculate all counters for simplicity
        TestCountersData data = new TestCountersData(runs);

        return new TestCounters(testCounters, data);
      }
    );
  }

  @XmlAttribute(name = "href")
  @Nullable
  public String getHref() {
    return myPagerData.getHref();
  }

  @XmlAttribute(name = "nextHref")
  @Nullable
  public String getNextHref() {
    return myPagerData.getNextHref();
  }

  @XmlAttribute(name = "prevHref")
  @Nullable
  public String getPrevHref() {
    return myPagerData.getPrevHref();
  }
}
