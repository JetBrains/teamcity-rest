/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class Comment {
  @NotNull private jetbrains.buildServer.serverSide.comments.Comment myBuildComment;
  private ApiUrlBuilder myApiUrlBuilder;

  public Comment() {
  }

  public Comment(@NotNull jetbrains.buildServer.serverSide.comments.Comment buildComment, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myBuildComment = buildComment;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlElement
  public UserRef getUser() {
    final jetbrains.buildServer.users.User user = myBuildComment.getUser();
    if (user != null) {
      return new UserRef(user, myApiUrlBuilder);
    }
    return null;
  }

  @XmlElement
  public String getTimestamp() {
    return Util.formatTime(myBuildComment.getTimestamp());
  }

  @XmlElement
  public String getText() {
    final String commentText = myBuildComment.getComment();
    return commentText != null ? commentText : "<none>";
  }
}
