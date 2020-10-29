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

package jetbrains.buildServer.server.rest.model;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 01/03/2016
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "metaData")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a named Entries entity."))
public class NamedData {
  @XmlAttribute
  public String id;

  @XmlElement(name = "entries")
  public Entries entries;

  public NamedData() {
  }

  public NamedData(@NotNull final String nameP, @NotNull final Map<String, String> properties, @NotNull final Fields fields) {
    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), nameP);
    entries = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("entries"), new ValueWithDefault.Value<Entries>() {
      @Nullable
      @Override
      public Entries get() {
        return new Entries(properties, fields.getNestedField("entries"));
      }
    });
  }
}
