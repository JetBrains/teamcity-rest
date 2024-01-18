/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

import java.util.*;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.change.CommiterData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "commiters")
@XmlType(name = "commiters")
public class Commiters {
  private Fields myFields;
  private BeanContext myBeanContext;
  private List<CommiterData> myCommiters;

  public Commiters() { }

  public Commiters(@NotNull List<CommiterData> commiters,
                   @NotNull Fields fields,
                   @NotNull final BeanContext beanContext) {
    myFields = fields;
    myBeanContext = beanContext;
    myCommiters = commiters;
  }

  @XmlElement(name = "commiter")
  public List<Commiter> getCommiters() {
    if(!myFields.isIncluded("commiter", false, true))
      return null;

    Fields commiterFields = myFields.getNestedField("commiter");
    return myCommiters.stream()
                      .map(d -> new Commiter(commiterFields, d.getVcsUsername(), d.getUsers(), myBeanContext))
                      .collect(Collectors.toList());
  }

  @XmlAttribute
  public Integer getCount() {
    if(!myFields.isIncluded("count", true, true))
      return null;
    return myCommiters.size();
  }
}