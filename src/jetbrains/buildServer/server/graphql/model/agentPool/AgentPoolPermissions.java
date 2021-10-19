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

package jetbrains.buildServer.server.graphql.model.agentPool;

import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;

public class AgentPoolPermissions {
  private final LazyPropWrapper myAuthorizeAgents;
  private final LazyPropWrapper myManageProjects;
  private final LazyPropWrapper myEnableAgents;
  private final LazyPropWrapper myManageAgents;
  private final boolean myManagePool;

  public AgentPoolPermissions(@NotNull BooleanSupplier authorizeAgents,
                              @NotNull BooleanSupplier manageProjects,
                              @NotNull BooleanSupplier enableAgents,
                              @NotNull BooleanSupplier manageAgents, boolean managePool) {
    myAuthorizeAgents = new LazyPropWrapper(authorizeAgents);
    myManageProjects = new LazyPropWrapper(manageProjects);
    myEnableAgents = new LazyPropWrapper(enableAgents);
    myManageAgents = new LazyPropWrapper(manageAgents);
    myManagePool = managePool;
  }

  public boolean isAuthorizeAgents() {
    return myAuthorizeAgents.get();
  }

  public boolean isManageProjects() {
    return myManageProjects.get();
  }

  public boolean isManage() {
    return myManagePool;
  }

  public boolean isEnableAgents() {
    return myEnableAgents.get();
  }

  public boolean isManageAgents() {
    return myManageAgents.get();
  }

  private class LazyPropWrapper {
    private final BooleanSupplier mySupplier;
    private boolean myCreated = false;
    private boolean myValue = false;
    LazyPropWrapper(@NotNull BooleanSupplier valueSupplier) {
      mySupplier = valueSupplier;
    }

    public boolean get() {
      if(myCreated) {
        return myValue;
      }

      myCreated = true;
      return (myValue = mySupplier.getAsBoolean());
    }
  }
}
