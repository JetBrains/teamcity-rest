/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author Yegor.Yarko
 *         Date: 28.07.12
 */
@XmlRootElement(name = "branch")
@XmlType(name = "branch", propOrder = {"name", "default", "unspecified"})
public class Branch {
  private jetbrains.buildServer.serverSide.Branch myBranch;

  public Branch() {
  }

  public Branch(jetbrains.buildServer.serverSide.Branch branch) {
    myBranch = branch;
  }

  @XmlAttribute(name = "name")
  String getName(){
    return myBranch.getDisplayName();
  }

  @XmlAttribute(name = "default")
  Boolean isDefault(){
    return myBranch.isDefaultBranch() ? Boolean.TRUE : null;
  }

  @XmlAttribute(name = "unspecified")
  Boolean isUnspecified(){
    return jetbrains.buildServer.serverSide.Branch.UNSPECIFIED_BRANCH_NAME.equals(myBranch.getName()) ? Boolean.TRUE : null;
  }
}
