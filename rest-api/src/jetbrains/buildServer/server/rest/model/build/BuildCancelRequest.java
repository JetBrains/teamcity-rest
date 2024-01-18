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

package jetbrains.buildServer.server.rest.model.build;

import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Yegor.Yarko
 *         Date: 03.11.13
 */
@XmlRootElement(name = "buildCancelRequest")
@XmlType(name = "buildCancelRequest")
@ModelDescription("Represents a cancel request for the specific build.")
public class BuildCancelRequest {
  public BuildCancelRequest() {
  }

  public BuildCancelRequest(final String comment, final boolean readdIntoQueue) {
    this.comment = comment;
    this.readdIntoQueue = readdIntoQueue;
  }

  @XmlAttribute(name = "comment")
  public String comment;

  /**
   * When canceling queued builds, should be set to 'false'
   */
  @XmlAttribute(name = "readdIntoQueue")
  public boolean readdIntoQueue;

}