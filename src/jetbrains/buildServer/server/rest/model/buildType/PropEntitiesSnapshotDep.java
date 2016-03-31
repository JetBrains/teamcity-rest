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

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "snapshot-dependencies")
@SuppressWarnings("PublicField")
public class PropEntitiesSnapshotDep {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "snapshot-dependency")
  public List<PropEntitySnapshotDep> propEntities;

  public PropEntitiesSnapshotDep() {
  }

  public PropEntitiesSnapshotDep(@NotNull final BuildTypeSettings buildType, @NotNull final Fields fields, @NotNull final BeanContext context) {
    final List<Dependency> dependencies = buildType.getDependencies();
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("snapshot-dependency"), new ValueWithDefault.Value<List<PropEntitySnapshotDep>>() {
      @Nullable
      public List<PropEntitySnapshotDep> get() {
        return CollectionsUtil.convertCollection(dependencies, new Converter<PropEntitySnapshotDep, Dependency>() {
          public PropEntitySnapshotDep createFrom(@NotNull final Dependency source) {
            return new PropEntitySnapshotDep(source, fields.getNestedField("snapshot-dependency", Fields.NONE, Fields.LONG), context);
          }
        });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), dependencies.size());
  }

  /**
   * @return true if buildTypeSettings is modified
   */
  public boolean setToBuildType(final @NotNull BuildTypeSettings buildTypeSettings, final @NotNull ServiceLocator serviceLocator) {
    final List<Dependency> originalDependencies = buildTypeSettings.getDependencies();
    try {
      removeAllDependencies(buildTypeSettings);
      if (propEntities != null) {
        for (PropEntitySnapshotDep entity : propEntities) {
          entity.addSnapshotDependency(buildTypeSettings, serviceLocator);
        }
      }
      return true; // cannot actually determine if modified or not
    } catch (Exception e) {
      //restore original settings
      removeAllDependencies(buildTypeSettings);
      for (Dependency dependency : originalDependencies) {
        buildTypeSettings.addDependency(dependency);
      }
      throw new BadRequestException("Error setting snapshot dependencies", e);
    }
  }

  public static void removeAllDependencies(@NotNull final BuildTypeSettings buildType) {
    for (Dependency originalDependency : buildType.getDependencies()) {
      buildType.removeDependency(originalDependency);
    }
  }
}
