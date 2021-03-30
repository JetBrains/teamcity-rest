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

import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "scopes")
@XmlType(name = "scopes")
@ModelBaseType(ObjectType.PAGINATED)
public class Scopes {
  private List<jetbrains.buildServer.server.rest.data.problem.scope.Scope> myScopes;
  private Fields myFields;
  private BeanContext myContext;
  private UriInfo myUriInfo;
  private PagerData myPagerData;

  public Scopes() { }

  public Scopes(@NotNull List<jetbrains.buildServer.server.rest.data.problem.scope.Scope> scopes, @NotNull Fields fields, @Nullable final PagerData pagerData, @Nullable UriInfo uriInfo, @NotNull BeanContext beanContext) {
    myScopes = scopes;
    myFields = fields;
    myContext = beanContext;
    myUriInfo = uriInfo;
    myPagerData = pagerData;
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count"), myScopes.size());
  }

  @XmlElement(name = "scope")
  public List<Scope> getScopes() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("scope"),
      () -> myScopes.stream().map(s -> new Scope(s, myFields.getNestedField("scope"), myContext, myPagerData)).collect(Collectors.toList())
    );
  }
}
