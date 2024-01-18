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

package jetbrains.buildServer.server.rest.model.project;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 21/06/2016
 */
@XmlType
@ModelDescription("Represents a project state field (as of now, limited to the read-only state of project).")
public class StateField {
  // see also BooleanStatus
  @XmlAttribute
  public Boolean value;
  @XmlAttribute
  public Boolean inherited;

  public StateField() {
  }

  @Nullable
  public static StateField create(@NotNull final Boolean value, @Nullable final Boolean inherited, @NotNull final Fields fields) {
    StateField result = new StateField();
    result.value = ValueWithDefault.decideDefault(fields.isIncluded("value"), value);
    result.inherited = ValueWithDefault.decideDefault(fields.isIncluded("inherited"), inherited);
    if (result.value == null && result.inherited == null) return null;
    return result;
  }
}