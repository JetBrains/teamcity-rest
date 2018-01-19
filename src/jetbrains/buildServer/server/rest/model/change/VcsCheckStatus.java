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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.OperationRequestor;
import jetbrains.buildServer.vcs.VcsRootStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 29/06/2016
 */
@XmlRootElement(name = "VcsCheckStatus")
@XmlType(name = "VcsCheckStatus")
@SuppressWarnings("PublicField")
public class VcsCheckStatus {
  @XmlAttribute
  public String status;

  @XmlAttribute
  public String requestorType;

  @XmlAttribute
  public String timestamp;

  public VcsCheckStatus() {
  }

  public VcsCheckStatus(@NotNull final VcsRootStatus status, @Nullable final OperationRequestor requestor, final @NotNull Fields fields) {
    this.status = ValueWithDefault.decideDefault(fields.isIncluded("status"), status.getType().toString().toLowerCase());
    this.timestamp = ValueWithDefault.decideDefault(fields.isIncluded("timestamp"), Util.formatTime(status.getTimestamp()));
    this.requestorType = requestor == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("requestorType"), requestor.name().toLowerCase());
  }
}