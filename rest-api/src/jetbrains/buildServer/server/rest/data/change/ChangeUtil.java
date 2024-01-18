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

package jetbrains.buildServer.server.rest.data.change;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.change.SnapshotDependencyLink;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.DummyBuild;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;

public class ChangeUtil {
  public static List<CommiterData> getUniqueCommiters(@NotNull Stream<SVcsModification> modifications) {
    Set<String> seenUsernames = new HashSet<>();

    List<CommiterData> result = new ArrayList<>();

    modifications.forEach(m -> {
      final String username = m.getUserName();
      if(username == null)
        return;

      boolean notSeenBefore = seenUsernames.add(username);
      if(notSeenBefore) {
        result.add(new CommiterData(username, m.getCommitters()));
      }
    });

    return result;
  }

  public static SnapshotDependencyLink getSnapshotDependencyLink(@NotNull ChangeDescriptor changeDescriptor, @NotNull Fields fields, @NotNull BeanContext context) {
    Object data = changeDescriptor.getAssociatedData().get(ChangeDescriptorConstants.SNAPSHOT_DEPENDENCY_PROMOTION);
    if (data instanceof BuildPromotion) {
      BuildPromotion p = (BuildPromotion) data;
      SBuild build = p.getAssociatedBuild();
      if (build != null && !(build instanceof DummyBuild)) {
        return SnapshotDependencyLink.build(build, fields, context);
      }

      SQueuedBuild queueBuild = p.getQueuedBuild();
      if (queueBuild != null) {
        return SnapshotDependencyLink.queuedBuild(queueBuild, fields, context);
      }

      SBuildType buildType = p.getBuildType();
      if (buildType != null) {
        Branch branch = p.getBranch();
        String branchName = branch != null ? branch.getName() : null;
        return SnapshotDependencyLink.buildType(buildType, branchName, fields, context);
      }

      return SnapshotDependencyLink.unknown(fields, context);
    } else {
      return SnapshotDependencyLink.unknown(fields, context);
    }
  }
}