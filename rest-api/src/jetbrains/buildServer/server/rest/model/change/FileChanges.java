/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsFileModification;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
@ModelBaseType(
    value = ObjectType.LIST,
    baseEntity = "FileChange"
)
@XmlRootElement(name = "fileChanges")
public class FileChanges {
  private static final Logger LOG = Logger.getInstance(FileChanges.class.getName());
  protected static final String REST_BEANS_FILES_NESTED_FILE_ITEMS_LIMIT = "rest.beans.change.files.nestedFileItemsLimit";

  @XmlAttribute public Integer count;

  @XmlElement(name = "file")
  public List<FileChange> files;

  public FileChanges() {
  }

  public FileChanges(@NotNull final List<? extends VcsFileModification> fileChanges, final @NotNull Fields fields) {
    count = ValueWithDefault.decideDefault(fields.isIncluded("count", true), fileChanges.size()); // this can differ from the actual number of sub-elements included

    files = ValueWithDefault.decideDefault(fields.isIncluded("file", true), () -> {
        final int resultSizeLimit = TeamCityProperties.getInteger(REST_BEANS_FILES_NESTED_FILE_ITEMS_LIMIT, 5000);
        int resultSize = Math.min(fileChanges.size(), resultSizeLimit);
        ArrayList<FileChange> result = new ArrayList<>(resultSize);
        int i = 0;
        for (VcsFileModification file : fileChanges) {
          if (i++ == resultSize) {
            LOG.info("List of file changes is truncated from the original value " + fileChanges.size() +
                      " to the limit " + resultSize + " set via '" + REST_BEANS_FILES_NESTED_FILE_ITEMS_LIMIT + "' internal property.");
            break;
          }
          result.add(new FileChange(file, fields.getNestedField("file", Fields.LONG, Fields.LONG).removeRestrictedField("file"))); //Using removeRestrictedField as inside is also a "file"
        }
        return result;
    });
  }
}