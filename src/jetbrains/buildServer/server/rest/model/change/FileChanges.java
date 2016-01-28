/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.VcsFileModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class FileChanges {
  @XmlAttribute public Integer count;

  @XmlElement(name = "file")
  public List<FileChange> files;

  public FileChanges() {
  }

  public FileChanges(@NotNull final List<VcsFileModification> fileChanges, final @NotNull Fields fields) {
    count = ValueWithDefault.decideDefault(fields.isIncluded("count", true), fileChanges.size());

    files = ValueWithDefault.decideDefault(fields.isIncluded("file", true), new ValueWithDefault.Value<List<FileChange>>() {
      @Nullable
      @Override
      public List<FileChange> get() {
        ArrayList<FileChange> result = new ArrayList<>(files.size());
        int i = 0;
        for (VcsFileModification file : fileChanges) {
          result.add(new FileChange(file, fields.getNestedField("file", Fields.LONG, Fields.LONG).removeRestrictedField("file"))); //Using removeRestrictedField as inside is also a "file"
        }
        return result;
      }
    });
  }
}
