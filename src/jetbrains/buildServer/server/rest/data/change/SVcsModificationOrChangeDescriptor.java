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

package jetbrains.buildServer.server.rest.data.change;

import jetbrains.buildServer.serverSide.ChangeDescriptor;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SVcsModificationOrChangeDescriptor {
  private final SVcsModification myModification;
  private final ChangeDescriptor myChangeDescriptor;

  public SVcsModificationOrChangeDescriptor(@NotNull SVcsModification modification) {
    myChangeDescriptor = null;
    myModification = modification;
  }

  /**
   * descriptor.getRelatedVcsChange must be non-null;
   *
   * @param descriptor
   */
  public SVcsModificationOrChangeDescriptor(@NotNull ChangeDescriptor descriptor) {
    if(descriptor.getRelatedVcsChange() == null)
      throw new IllegalArgumentException();

    myChangeDescriptor = descriptor;
    myModification = descriptor.getRelatedVcsChange();
  }

  @NotNull
  public SVcsModification getSVcsModification() {
    return myModification;
  }

  @Nullable
  public ChangeDescriptor getChangeDescriptor() {
    return myChangeDescriptor;
  }

  @Override
  public String toString() {
    if(myChangeDescriptor != null) {
      return "SVMOCD{descriptor= " + myChangeDescriptor + " }";
    }
    return "SVMOCD{mod= " + myModification + " }";
  }
}
