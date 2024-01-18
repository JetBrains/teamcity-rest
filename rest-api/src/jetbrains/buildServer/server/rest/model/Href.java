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

package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "href")
@XmlType(name = "HReference")
@ModelDescription("Represents a href link.")
public class Href {
  protected String href;

  public Href() {
  }

  public Href(@NotNull final String shortHref, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    href = apiUrlBuilder.transformRelativePath(shortHref);
  }

  @NotNull
  @XmlAttribute(name = "href")
  public String getHref() {
    return href;
  }
}