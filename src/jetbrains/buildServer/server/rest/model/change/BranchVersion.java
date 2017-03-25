/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Branch;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28/06/2016
 */
@XmlRootElement(name = "branchVersion")
@XmlType(name = "branchVersion")
public class BranchVersion extends Branch {
  private String myVersion;
  private Fields myFields;

  public BranchVersion() {
  }

  public BranchVersion(@NotNull final BranchData branch, @Nullable String version, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    super(branch, fields, beanContext);
    myVersion = version;
    myFields = fields;
  }

  @XmlAttribute(name = "version")
  public String getVersion(){
    return myVersion == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("version"), myVersion);
  }

}
