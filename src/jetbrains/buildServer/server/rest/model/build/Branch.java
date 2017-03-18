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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.12
 */
@XmlRootElement(name = "branch")
@XmlType(name = "branch", propOrder = {"name", "internalName", "default", "unspecified", "active", "lastActivity"})
public class Branch {
  private BranchData myBranch;
  private Fields myFields;

  public Branch() {
  }

  public Branch(@NotNull BranchData branch, @NotNull final Fields fields) {
    myBranch = branch;
    myFields = fields;
  }

  @XmlAttribute(name = "name")
  public String getName(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("name"), () -> myBranch.getDisplayName());
  }

  /**
   * experimental support only
   * @return same as "name", but always "<default>" for the default branch: this is faster than calculating true name value
   */
  @XmlAttribute(name = "internalName")
  public String getInternalName(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("internalName", false, false), () -> myBranch.getName());
  }

  @XmlAttribute(name = "default")
  public Boolean isDefault(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("default"), () -> myBranch.isDefaultBranch());
  }

  @XmlAttribute(name = "unspecified")
  public Boolean isUnspecified(){
    return ValueWithDefault.decideDefault(myFields.isIncluded("unspecified"), () -> myBranch.isUnspecifiedBranch());
  }

  @XmlAttribute(name = "active")
  public Boolean isActive() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("active", false), () -> myBranch.isActive());
  }

  @XmlAttribute(name = "lastActivity")
  public String getLastActivity() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("lastActivity", false), () -> Util.formatTime(myBranch.getActivityTimestamp()));
  }
}
