/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.text.SimpleDateFormat;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class Comment {
  private jetbrains.buildServer.serverSide.comments.Comment myBuildComment;

  public Comment() {
  }

  public Comment(jetbrains.buildServer.serverSide.comments.Comment buildComment) {
    myBuildComment = buildComment;
  }

  @XmlAttribute
  public UserRef getUser() {
    return new UserRef(myBuildComment.getUser());
  }

  @XmlElement
  public String getTimestamp() {
    return (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(myBuildComment.getTimestamp());
  }

  @XmlElement
  public String getText() {
    return myBuildComment.getComment();
  }
}
