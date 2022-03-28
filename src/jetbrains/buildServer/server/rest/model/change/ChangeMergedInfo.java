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

package jetbrains.buildServer.server.rest.model.change;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Branches;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelExperimental;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "changeMergedInfo")
@ModelDescription(
  value = "Merged information about change duplicates coming from different VCS roots."
)
@ModelExperimental
public class ChangeMergedInfo {
  private jetbrains.buildServer.vcs.ChangeStatus myChangeStatus;
  private Fields myFields;
  private BeanContext myBeanContext;

  public ChangeMergedInfo() {}

  public ChangeMergedInfo(@NotNull final jetbrains.buildServer.vcs.ChangeStatus changeStatus, @NotNull final Fields fields, @NotNull final BeanContext ctx) {
    myChangeStatus = changeStatus;
    myFields = fields;
    myBeanContext = ctx;
  }

  @XmlElement(name = "status")
  public ChangeStatus getStatus() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("status", false, false),
      () -> new ChangeStatus(myChangeStatus, myFields.getNestedField("status"), myBeanContext)
    );
  }

  @XmlElement(name = "branches")
  public Branches getBranches() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("branches", false),
      () -> new Branches(BranchData.distinctFromBuilds(myChangeStatus.getFirstBuilds().values()), null, myFields.getNestedField("branches"), myBeanContext)
    );
  }

  @XmlElement(name = "changes")
  public Changes getChanges() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("changes", false),
      () -> {
        Stream<SVcsModificationOrChangeDescriptor> changes = myChangeStatus.getMergedVcsModificationInfo().getChanges().stream().map(SVcsModificationOrChangeDescriptor::new);
        return new Changes(null, myFields.getNestedField("changes"), myBeanContext, CachingValue.simple(() -> changes.collect(Collectors.toList())));
      }
    );
  }

  @XmlElement(name = "vcsRootInstances")
  public VcsRootInstances getVcsRootInstances() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("vcsRootInstances", false, false),
      () -> new VcsRootInstances(
        CachingValue.simple(myChangeStatus.getMergedVcsModificationInfo().getVcsRoots()),
        null,
        myFields.getNestedField("vcsRootInstances"),
        myBeanContext
      )
    );
  }
}
