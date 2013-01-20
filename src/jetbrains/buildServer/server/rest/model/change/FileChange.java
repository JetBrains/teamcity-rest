/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.vcs.VcsFileModification;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class FileChange {
  @XmlAttribute(name = "before-revision")
  public String before;
  @XmlAttribute(name = "after-revision")
  public String after;
  @XmlAttribute(name = "file")
  public String fileName;
  @XmlAttribute(name = "relative-file")
  public String relativeFileName;

  FileChange() {
  }

  public FileChange(final VcsFileModification vcsFileModification) {
    before = vcsFileModification.getBeforeChangeRevisionNumber();
    after = vcsFileModification.getAfterChangeRevisionNumber();
    fileName = vcsFileModification.getFileName();
    relativeFileName = vcsFileModification.getRelativeFileName();
  }
}
