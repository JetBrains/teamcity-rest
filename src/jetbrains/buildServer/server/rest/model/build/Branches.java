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

package jetbrains.buildServer.server.rest.model.build;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "branches")
public class Branches {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "branch")
  public List<Branch> branches;

  public Branches() {
  }

  public Branches(@NotNull final List<jetbrains.buildServer.serverSide.Branch> branchesP, @NotNull final Fields fields) {
    branches = ValueWithDefault.decideDefault(fields.isIncluded("branch"), new ValueWithDefault.Value<List<Branch>>() {
      @Nullable
      public List<Branch> get() {
        return CollectionsUtil.convertCollection(branchesP, new Converter<Branch, jetbrains.buildServer.serverSide.Branch>() {
          public Branch createFrom(@NotNull final jetbrains.buildServer.serverSide.Branch source) {
            return new Branch(source, fields.getNestedField("branch"));
          }
        });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), branchesP.size());
  }
}