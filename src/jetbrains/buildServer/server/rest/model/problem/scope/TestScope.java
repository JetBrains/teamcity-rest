/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "testScope")
@XmlType(name = "testScope")
public class TestScope {
  private Fields myFields;
  private BeanContext myContext;
  private jetbrains.buildServer.server.rest.data.problem.scope.TestScope myRealTestScope;

  public TestScope() { }

  public TestScope(@NotNull jetbrains.buildServer.server.rest.data.problem.scope.TestScope testScope, @NotNull Fields fields, @NotNull BeanContext context, @Nullable PagerData pagerData) {
    myRealTestScope = testScope;
    myFields = fields;
    myContext = context;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("name"), myRealTestScope.getName());
  }

  @XmlAttribute(name = "suite")
  public String getSuite() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("suite"), myRealTestScope.getSuite());
  }

  @XmlAttribute(name = "package")
  public String getPackage() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("package"), myRealTestScope.getPackage());
  }

  @XmlAttribute(name = "class")
  public String getClass1() {
    return ValueWithDefault.decideIncludeByDefault(myFields.isIncluded("class"), myRealTestScope.getClass1());
  }

  @XmlElement(name = "testOccurrences")
  public TestOccurrences getOccurrences() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("testOccurrences"),
      // TODO: add pager data
      new TestOccurrences(myRealTestScope.getTestRuns(), null, null, null, myFields.getNestedField("testOccurrences"), myContext)
    );
  }
}