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

package jetbrains.buildServer.server.rest.model.agent;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 03/12/2015
 */
@XmlType(name = "booleanStatus")
@XmlRootElement(name = "booleanStatus")
public class BooleanStatus {
  @XmlAttribute(name = "status")
  public Boolean status;
  @XmlElement(name = "comment")
  public Comment comment;

  public BooleanStatus() {
  }

  public BooleanStatus(final boolean statusP,
                       @Nullable final jetbrains.buildServer.serverSide.comments.Comment lastCommentP,
                       @NotNull final Fields fields,
                       @NotNull final BeanContext beanContext) {
    status = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("status"), statusP);
    comment = lastCommentP == null ? null : ValueWithDefault.decideIncludeByDefault(fields.isIncluded("comment", false), new ValueWithDefault.Value<Comment>() {
      @Nullable
      public Comment get() {
        return new Comment(lastCommentP, fields.getNestedField("comment", Fields.NONE, Fields.LONG), beanContext);
      }
    });
  }

  @Nullable
  public String getCommentTextFromPosted() {
    if (comment == null) return null;
    if (comment.text == null) return null;
    return comment.text;
  }

  @Nullable
  public Boolean getStatusFromPosted() {
    return status;
  }
}