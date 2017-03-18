/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "branches")
public class Branches {
  @XmlAttribute
  public Integer count;

  @XmlAttribute(required = false)
  @Nullable
  public String href;

  @XmlElement(name = "branch")
  public List<Branch> branches;

  public Branches() {
  }

  public Branches(@Nullable final List<BranchData> branchesP, @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    if (branchesP != null) {
      branches = ValueWithDefault.decideDefault(fields.isIncluded("branch"),
                                                () -> branchesP.stream().map(b -> new Branch(b, fields.getNestedField("branch"), beanContext)).collect(Collectors.toList()));
      count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), branchesP.size());
    }
    href = pagerData == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("href"), pagerData.getHref());
  }
}