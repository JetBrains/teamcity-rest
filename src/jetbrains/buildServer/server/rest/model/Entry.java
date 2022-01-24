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

package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 24.01.14
 */

@XmlType(propOrder = {"name", "value"})
@XmlRootElement(name = "entry")
@ModelDescription("Represents a single name-value relation.")
public class Entry {
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String value;

  public Entry() {
  }

  public Entry(@Nullable String name, @Nullable String value, final @NotNull Fields fields) {
    this.name =  ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name", true, true), name);
    this.value = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("value", true, true), value);
  }
}


