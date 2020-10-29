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

import com.intellij.openapi.util.text.StringUtil;
import java.util.Date;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
@XmlType(name= "comment")
@XmlRootElement(name = "comment")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a dated comment by specific user."))
public class Comment {
  @XmlElement(name = "user")
  public jetbrains.buildServer.server.rest.model.user.User user;
  @XmlElement
  public String timestamp;
  @XmlElement
  public String text;

  public Comment() {
  }

  public Comment(@NotNull jetbrains.buildServer.serverSide.comments.Comment buildComment, @NotNull final Fields fields, @NotNull final BeanContext context) {
    init(buildComment.getUser(), buildComment.getTimestamp(), buildComment.getComment(), fields, context);
  }

  public Comment(@Nullable User user, @NotNull Date timestamp, @Nullable String commentText, @NotNull final Fields fields, @NotNull final BeanContext context) {
    init(user, timestamp, commentText, fields, context);
  }

  private void init(@Nullable final User userP,
                    @NotNull final Date timestampP,
                    @Nullable final String commentTextP,
                    @NotNull final Fields fields,
                    @NotNull final BeanContext context) {
    user = userP == null ? null : ValueWithDefault
          .decideDefault(fields.isIncluded("user"), new ValueWithDefault.Value<jetbrains.buildServer.server.rest.model.user.User>() {
            public jetbrains.buildServer.server.rest.model.user.User get() {
              return new jetbrains.buildServer.server.rest.model.user.User(userP, fields.getNestedField("user"), context);
            }
          });
    timestamp = ValueWithDefault.decideDefault(fields.isIncluded("timestamp"), Util.formatTime(timestampP));
    text = ValueWithDefault.decideDefault(fields.isIncluded("text"), StringUtil.isEmpty(commentTextP) ? null : commentTextP);
  }

  @Nullable
  public String getTextFromPosted() {
    return text;
  }
}
