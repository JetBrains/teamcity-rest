/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.vcs.VcsFileModification;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class FileChanges {
  @XmlElement(name = "file")
  public List<FileChange> files;

  public FileChanges() {
  }

  public FileChanges(final List<VcsFileModification> fileChanges) {
    files = new ArrayList<FileChange>(fileChanges.size());
    for (VcsFileModification file : fileChanges) {
      files.add(new FileChange(file));
    }
  }
}
