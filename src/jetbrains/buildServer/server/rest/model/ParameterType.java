/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.serverSide.ControlDescription;
import jetbrains.buildServer.serverSide.parameters.ParameterDescriptionFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 24.01.14
 */
@XmlRootElement(name = "parameterType")
@XmlType(name="parameterType", propOrder = {"rawValue"})
public class ParameterType {
  @XmlAttribute
  public String rawValue;

  public ParameterType() {
  }

  public ParameterType(@NotNull ControlDescription typeSpec, @NotNull final Fields fields, @NotNull final ServiceLocator serviceLocator) {
    final String specString = serviceLocator.getSingletonService(ParameterDescriptionFactory.class).serializeSpec(typeSpec);
    rawValue = !fields.isIncluded("spec", true, true) ? null : specString;
    }
  }