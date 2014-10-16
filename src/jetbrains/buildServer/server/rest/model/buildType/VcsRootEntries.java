/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 04.08.2009
 */
@XmlRootElement(name = "vcs-root-entries")
public class VcsRootEntries {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "vcs-root-entry")
  public List<VcsRootEntry> vcsRootAssignments;

  public VcsRootEntries() {
  }

  public VcsRootEntries(@NotNull final BuildTypeOrTemplate buildType, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    final List<jetbrains.buildServer.vcs.VcsRootEntry> vcsRootEntries = buildType.get().getVcsRootEntries();
    vcsRootAssignments = ValueWithDefault.decideDefault(fields.isIncluded("vcs-root-entry", true, true), new ValueWithDefault.Value<List<VcsRootEntry>>() {
      @Nullable
      public List<VcsRootEntry> get() {
        ArrayList<VcsRootEntry> items = new ArrayList<VcsRootEntry>(vcsRootEntries.size());
        for (jetbrains.buildServer.vcs.VcsRootEntry entry : vcsRootEntries) {
          items.add(new VcsRootEntry((SVcsRoot)entry.getVcsRoot(), buildType, fields.getNestedField("vcs-root-entry", Fields.LONG, Fields.LONG), beanContext));
        }
        return items;
      }
    });

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), vcsRootEntries.size());
  }

}
