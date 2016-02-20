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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="artifact-dependencies")
@SuppressWarnings("PublicField")
public class PropEntitiesArtifactDep implements DefaultValueAware {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "artifact-dependency")
  public List<PropEntityArtifactDep> propEntities;

  public PropEntitiesArtifactDep() {
  }

  public PropEntitiesArtifactDep(@NotNull final List<SArtifactDependency> artifactDependencies, @NotNull final Fields fields, @NotNull final BeanContext context) {
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("artifact-dependency", true), new ValueWithDefault.Value<List<PropEntityArtifactDep>>() {
      @Nullable
      public List<PropEntityArtifactDep> get() {
        final ArrayList<PropEntityArtifactDep> result = new ArrayList<PropEntityArtifactDep>(artifactDependencies.size());
        int orderNumber = 0;
        for (SArtifactDependency dependency : artifactDependencies) {
          result.add(new PropEntityArtifactDep(dependency, orderNumber, fields.getNestedField("artifact-dependency", Fields.NONE, Fields.LONG), context));
          orderNumber++;
        }
        ;
        return result;
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), artifactDependencies.size());
  }

  @NotNull
  public List<SArtifactDependency> getFromPosted(@Nullable final List<SArtifactDependency> originalCollection, @NotNull final ServiceLocator serviceLocator) {
    boolean replaceOriginal = originalCollection != null && submittedReplace != null ? submittedReplace : true;
    if (propEntities == null){
      return replaceOriginal ? Collections.emptyList() : new ArrayList<SArtifactDependency>(originalCollection);
    }
    final ArrayList<SArtifactDependency> result =
      replaceOriginal ? new ArrayList<SArtifactDependency>(propEntities.size()) : new ArrayList<SArtifactDependency>(originalCollection);
    for (PropEntityArtifactDep entity : propEntities) {
      result.add(entity.createDependency(serviceLocator));
    }
    return result;
  }

  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, propEntities);
  }

  /**
   * This is used only when posting the entity
   * Whether to patch existing entities with submitted ones or replace them. "true" (replace) by default
   */
  @XmlAttribute
  public String getReplace() {
    return null;
  }

  public Boolean submittedReplace;

  public void setReplace(String value) {
    submittedReplace = Boolean.valueOf(value);
  }
}
