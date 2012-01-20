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

package jetbrains.buildServer.server.rest.model.build;

import java.util.Date;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class PinnedInfo {
  private SUser myUser;
  private String myComment;
  private Date myTimestamp;
  private ApiUrlBuilder myApiUrlBuilder;

  public PinnedInfo() {
  }

  public PinnedInfo(final SUser user, final Date timestamp, final String comment, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myUser = user;
    myTimestamp = timestamp;
    myComment = comment;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlElement(name = "user")
  public UserRef getUser() {
    return new UserRef(myUser, myApiUrlBuilder);
  }

  @XmlElement
  public String getTimestamp() {
    return Util.formatTime(myTimestamp);
  }

  @XmlElement
  public String getComment() {
    return myComment;
  }
}
