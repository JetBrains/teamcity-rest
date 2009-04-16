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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.vcs.VcsFileModification;
import jetbrains.buildServer.vcs.VcsModification;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "change")
public class Change {
  @XmlAttribute
  public Long id;
  @XmlAttribute
  public String username;
  @XmlAttribute(name = "version")
  public String displayVersion;
  @XmlAttribute
  public String date;
  @XmlElement(name = "files")
  public FileChanges fileChanges;

  public Change() {
  }

  public Change(jetbrains.buildServer.vcs.VcsModification change) {
    id = change.getId();
    username = change.getUserName();
    displayVersion = change.getDisplayVersion();
    Date vcsDate = change.getVcsDate();
    if (vcsDate != null) {
      date = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(vcsDate);
    }
    fileChanges = new FileChanges(change.getChanges());
  }
}

class FileChanges {
  @XmlElement(name = "file")
  public List<FileChange> files;

  public FileChanges() {
  }

  public FileChanges(final List<VcsFileModification> fileChanges) {
    files = new ArrayList<FileChange>(fileChanges.size());
    for (jetbrains.buildServer.vcs.VcsFileModification file : fileChanges) {
      files.add(new FileChange(file));
    }
  }
}

class FileChange {
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

class ChangeRef {
  @XmlAttribute
  public String version;
  @XmlAttribute
  public String href;

  public ChangeRef() {
  }

  public ChangeRef(VcsModification modification) {
    this.href = "/httpAuth/api/changes/id:" + modification.getId();
    this.version = modification.getDisplayVersion();
  }
}