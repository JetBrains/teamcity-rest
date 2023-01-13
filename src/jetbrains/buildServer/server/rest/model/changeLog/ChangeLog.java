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

package jetbrains.buildServer.server.rest.model.changeLog;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.controllers.buildType.tabs.Graph;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * Used for pages:
 *  - project change log
 *  - build type change log
 *  - build type pending changes
 *  - build change log
 */
@XmlRootElement(name = "changeLog")
@XmlType(name = "changeLog")
public class ChangeLog {
  private List<jetbrains.buildServer.controllers.buildType.tabs.ChangeLogRow> myRows;
  private Graph myGraph;
  private Fields myFields;
  private BeanContext myBeanContext;
  private ChangeLogPagerData myPagerData;

  public ChangeLog() { }

  public ChangeLog(@NotNull List<jetbrains.buildServer.controllers.buildType.tabs.ChangeLogRow> rows,
                   @NotNull Graph graph,
                   @NotNull Fields fields,
                   @NotNull ChangeLogPagerData pagerData,
                   @NotNull BeanContext beanContext) {
    myRows = rows;
    myGraph = graph;
    myFields = fields;
    myPagerData = pagerData;
    myBeanContext = beanContext;
  }

  @XmlElement(name = "row")
  public List<ChangeLogRow> getRows() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("row"),
      () -> myRows.stream()
                  .map(row -> new ChangeLogRow(row, myFields.getNestedField("row"), myBeanContext))
                  .collect(Collectors.toList())
    );
  }

  @XmlElement(name = "graph")
  public ChangeLogGraph getGraph() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("graph"), () -> new ChangeLogGraph(myGraph));
  }

  @XmlAttribute(name = "nextHref")
  public String getNextHref() {
    return myPagerData.getNextHref();
  }

  @XmlAttribute(name = "href")
  public String getHref() {
    return myPagerData.getHref();
  }

  @XmlAttribute(name = "prevHref")
  public String getPrevHref() {
    return myPagerData.getPrevHref();
  }
}

