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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 20/09/2018
 */
@SuppressWarnings({"PublicField", "WeakerAccess"})
@XmlRootElement(name = "operationResult")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a relation between message and related entity."))
public class OperationResult {
  @XmlElement
  public String message;

  @XmlElement(name ="related")
  public RelatedEntity related;

  @SuppressWarnings("unused")
  public OperationResult() {
  }

  public OperationResult(@NotNull final Data data, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    message = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("text"), data.message);
    related = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("related"),
                                                      () -> new RelatedEntity(data.related, fields.getNestedField("related", Fields.LONG, Fields.LONG), beanContext));
  }

  public static class Data {
    @Nullable private final String message;
    @NotNull private final RelatedEntity.Entity related;

    private Data(@Nullable final String message, @NotNull final RelatedEntity.Entity related) {
      this.message = message;
      this.related = related;
    }

    @NotNull
    public static Data createSuccess(@NotNull final RelatedEntity.Entity related) {
      return new Data(null, related);
    }

    @NotNull
    public static Data createError(@NotNull String errorMessage, @NotNull final RelatedEntity.Entity related) {
      return new Data(errorMessage, related);
    }
  }
}
