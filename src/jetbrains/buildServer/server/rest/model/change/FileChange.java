/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.VcsFileModification;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class FileChange {
  @XmlAttribute(name = "before-revision")
  public String before;
  @XmlAttribute(name = "after-revision")
  public String after;
  @XmlAttribute(name = "changeType")
  public String changeType;
  @XmlAttribute(name = "changeTypeComment")
  public String changeTypeComment;
  @XmlAttribute(name = "file")
  public String fileName;
  @XmlAttribute(name = "relative-file")
  public String relativeFileName;
  @XmlAttribute(name = "directory")
  public Boolean directory;

  FileChange() {
  }

  public FileChange(final @NotNull VcsFileModification vcsFileModification, final @NotNull Fields fields) {
    before = ValueWithDefault.decideDefault(fields.isIncluded("before-revision", true), vcsFileModification.getBeforeChangeRevisionNumber());
    after = ValueWithDefault.decideDefault(fields.isIncluded("after-revision", true), vcsFileModification.getAfterChangeRevisionNumber());
    String description = vcsFileModification.getType().getDescription();
    changeType = ValueWithDefault.decideDefault(fields.isIncluded("changeType", true), description);

    boolean commentIsDefault = description != null &&  description.equals(vcsFileModification.getChangeTypeName());
    changeTypeComment = commentIsDefault ? null : ValueWithDefault.decideDefault(fields.isIncluded("changeTypeComment", false, false), vcsFileModification.getChangeTypeName());
    fileName = ValueWithDefault.decideDefault(fields.isIncluded("file", true), vcsFileModification.getFileName());
    relativeFileName = ValueWithDefault.decideDefault(fields.isIncluded("relative-file", true), vcsFileModification.getRelativeFileName());
    boolean isDirectory = vcsFileModification.getType().isDirectory();
    directory = ValueWithDefault.decideDefault(fields.isIncluded("directory", isDirectory, isDirectory), isDirectory);
  }
}
