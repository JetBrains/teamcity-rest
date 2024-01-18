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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "snapshotDependencyLink", propOrder = {
  "build",
  "buildType",
  "buildTypeBranch" /* experimental, it's a bit weird to have a plain string here */
})
public class SnapshotDependencyLink {
  private final Fields myFields;
  private final BeanContext myContext;
  private SBuild myBuild;
  private SQueuedBuild myQueuedBuild;
  private SBuildType myBuildType;
  private String myBuildTypeBranch;

  public static SnapshotDependencyLink build(@NotNull SBuild build, @NotNull Fields fields, @NotNull BeanContext context) {
    return new SnapshotDependencyLink(fields, context).withBuild(build);
  }

  public static SnapshotDependencyLink queuedBuild(@NotNull SQueuedBuild queuedBuild, @NotNull Fields fields, @NotNull BeanContext context) {
    return new SnapshotDependencyLink(fields, context).withQueuedBuild(queuedBuild);
  }

  public static SnapshotDependencyLink buildType(@NotNull SBuildType buildType, @Nullable String branch, @NotNull Fields fields, @NotNull BeanContext context) {
    return new SnapshotDependencyLink(fields, context).withBuildType(buildType, branch);
  }

  public static SnapshotDependencyLink unknown(@NotNull Fields fields, @NotNull BeanContext context) {
    return new SnapshotDependencyLink(fields, context);
  }

  private SnapshotDependencyLink(@NotNull Fields fields, @NotNull BeanContext context) {
    myContext = context;
    myFields = fields;
  }

  @XmlElement(name = "build")
  public Build getBuild() {
    if(myBuild == null && myQueuedBuild == null)
      return null;

    if (myQueuedBuild != null) {
      return ValueWithDefault.decideDefault(
        myFields.isIncluded("build",false, true),
        new Build(myQueuedBuild.getBuildPromotion(), myFields.getNestedField("build"), myContext)
      );
    }

    return ValueWithDefault.decideDefault(
      myFields.isIncluded("build",false, true),
      new Build(myBuild, myFields.getNestedField("build"), myContext)
    );
  }

  @XmlElement(name = "buildType")
  public BuildType getBuildType() {
    if(myBuildType == null)
      return null;

    return ValueWithDefault.decideDefault(
      myFields.isIncluded("buildType",false, true),
      new BuildType(new BuildTypeOrTemplate(myBuildType), myFields.getNestedField("buildType"), myContext)
    );
  }

  @XmlElement(name = "buildTypeBranch")
  public String getBuildTypeBranch() {
    if(myBuildTypeBranch == null)
      return null;

    return ValueWithDefault.decideDefault(myFields.isIncluded("buildTypeBranch",false, false), myBuildTypeBranch);
  }

  private SnapshotDependencyLink withBuildType(@NotNull SBuildType bt, @Nullable String branch) {
    myBuildType = bt;
    myBuildTypeBranch = branch;
    return this;
  }

  private SnapshotDependencyLink withBuild(@NotNull SBuild build) {
    myBuild = build;
    return this;
  }

  private SnapshotDependencyLink withQueuedBuild(@NotNull SQueuedBuild queuedBuild) {
    myQueuedBuild = queuedBuild;
    return this;
  }
}