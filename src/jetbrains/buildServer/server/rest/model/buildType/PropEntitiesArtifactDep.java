/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
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

  /**
   * @param artifactDependencies
   * @param buildType            null if enabled/disabled is not applicable
   * @param fields
   * @param context
   */
  public PropEntitiesArtifactDep(@NotNull final List<SArtifactDependency> artifactDependencies, @Nullable final BuildTypeSettingsEx buildType,
                                 @NotNull final Fields fields, @NotNull final BeanContext context) {
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("artifact-dependency", true), new ValueWithDefault.Value<List<PropEntityArtifactDep>>() {
      @Nullable
      public List<PropEntityArtifactDep> get() {
        final ArrayList<PropEntityArtifactDep> result = new ArrayList<PropEntityArtifactDep>(artifactDependencies.size());
        for (SArtifactDependency dependency : artifactDependencies) {
          result.add(new PropEntityArtifactDep(dependency, buildType, fields.getNestedField("artifact-dependency", Fields.NONE, Fields.LONG), context));
        }
        ;
        return result;
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), artifactDependencies.size());
  }

  public PropEntitiesArtifactDep(@NotNull final BuildTypeSettingsEx buildType, @NotNull final Fields fields, @NotNull final BeanContext context) {
    this(buildType.getArtifactDependencies(), buildType, fields, context);
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
      result.add(entity.createDependency(null, serviceLocator));
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

  /**
   * @return true if buildTypeSettings is modified
   */
  public boolean setToBuildType(final @NotNull BuildTypeSettingsEx buildTypeSettings, final @NotNull ServiceLocator serviceLocator) {
    PropEntitiesArtifactDep.Storage original = new PropEntitiesArtifactDep.Storage(buildTypeSettings);
    try {
      List<SArtifactDependency> deps = new ArrayList<>();
      Map<String, Boolean> enabledData = new HashMap<>();
      if (propEntities != null) {
        for (PropEntityArtifactDep entity : propEntities) {
          SArtifactDependency newDep = entity.addToInternalMain(buildTypeSettings, serviceLocator);
          deps.add(newDep);
          enabledData.put(newDep.getId(), entity.disabled == null || !entity.disabled);
        }
      }
      buildTypeSettings.setArtifactDependencies(deps);
      for (Map.Entry<String, Boolean> entry : enabledData.entrySet()) {
        buildTypeSettings.setEnabled(entry.getKey(), entry.getValue());
      }
      return true; // cannot actually determine if modified or not
    } catch (Exception e) {
      //restore previous state
      original.apply(buildTypeSettings);
      throw new BadRequestException("Error setting artifact dependencies", e);
    }
  }

  public static class Storage{
    public final List<SArtifactDependency> deps = new ArrayList<>();
    public final Map<String, Boolean> enabledData = new HashMap<>();

    public Storage(final @NotNull BuildTypeSettings buildTypeSettings) {
      for (SArtifactDependency dependency : buildTypeSettings.getArtifactDependencies()) {
        deps.add(dependency);
        enabledData.put(dependency.getId(), buildTypeSettings.isEnabled(dependency.getId()));
      }
    }

    public void apply(final @NotNull BuildTypeSettings buildTypeSettings){
      buildTypeSettings.setArtifactDependencies(deps);
      for (Map.Entry<String, Boolean> entry : enabledData.entrySet()) {
        buildTypeSettings.setEnabled(entry.getKey(), entry.getValue());
      }
    }
  }
}
