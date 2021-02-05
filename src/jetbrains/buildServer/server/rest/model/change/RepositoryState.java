/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.data.BranchData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 28/06/2016
 */
@XmlRootElement(name = "repositoryState")
@XmlType(name = "repositoryState")
@ModelDescription("Represents the list of the repository Branch entities with their recent revisions.")
@ModelBaseType(
    value = ObjectType.LIST,
    baseEntity = "BranchVersion"
)
public class RepositoryState {
  @XmlAttribute
  public String timestamp;

  @XmlAttribute
  public Integer count;

  @XmlElement(name = "branch")
  public List<BranchVersion> branches;

  public RepositoryState() {
  }

  public RepositoryState(@NotNull final jetbrains.buildServer.vcs.RepositoryState repositoryState, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    this.timestamp = ValueWithDefault.decideDefault(fields.isIncluded("timestamp"), Util.formatTime(repositoryState.getCreateTimestamp()));

    Map<String, String> branchRevisions = repositoryState.getBranchRevisions();
    String defaultBranchName = repositoryState.getDefaultBranchName();
    if (branchRevisions.isEmpty()) {
      this.branches =
        ValueWithDefault.decideDefault(fields.isIncluded("branch", false, true), () ->
          Collections.singletonList(new BranchVersion(new Branch(defaultBranchName, defaultBranchName), repositoryState.getDefaultBranchRevision(),
                                                      fields.getNestedField("branch", Fields.NONE, Fields.LONG), beanContext)));
    } else {
      this.branches =
        ValueWithDefault.decideDefault(fields.isIncluded("branch", false, true), () ->
          CollectionsUtil.convertCollection(branchRevisions.entrySet(), entry -> new BranchVersion(new Branch(entry.getKey(), defaultBranchName), entry.getValue(),
                                                                                                   fields.getNestedField("branch", Fields.NONE, Fields.LONG), beanContext)));
    }
    this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), branchRevisions.size());
  }

  private static class Branch extends BranchData {
    @NotNull private final String myName;
    private final boolean myDefaultBranch;

    public Branch(@NotNull final String name, @NotNull final String defaultName) {
      super(name);
      myName = name;
      myDefaultBranch = defaultName.equals(myName);
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myName;
    }

    @Override
    public boolean isDefaultBranch() {
      return myDefaultBranch;
    }
  }
}

