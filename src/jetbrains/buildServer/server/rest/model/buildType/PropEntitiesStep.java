/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "steps")
@ModelBaseType(ObjectType.LIST)
@SuppressWarnings("PublicField")
public class PropEntitiesStep {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "step")
  public List<PropEntityStep> propEntities;

  public PropEntitiesStep() {
  }

  public PropEntitiesStep(@NotNull final BuildTypeSettingsEx buildType, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    final List<SBuildRunnerDescriptor> buildRunners = buildType.getBuildRunners();
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("step"), new ValueWithDefault.Value<List<PropEntityStep>>() {
      @Nullable
      public List<PropEntityStep> get() {
        return CollectionsUtil.convertCollection(buildRunners,
                                                 new Converter<PropEntityStep, SBuildRunnerDescriptor>() {
                                                   public PropEntityStep createFrom(@NotNull final SBuildRunnerDescriptor source) {
                                                     return new PropEntityStep(source, buildType, fields.getNestedField("step", Fields.NONE, Fields.LONG), beanContext);
                                                   }
                                                 });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), buildRunners.size());
  }

  public boolean setToBuildType(@NotNull final BuildTypeOrTemplate buildType, @NotNull final ServiceLocator serviceLocator) {
    serviceLocator.getSingletonService(PermissionChecker.class).checkCanEditBuildTypeOrTemplate(buildType);
    BuildTypeSettingsEx settings = buildType.getSettingsEx();

    Storage original = new Storage(settings);
    try {
      removeAllSteps(settings);
      if (propEntities != null) {
        String[] runnersOrder = new String[propEntities.size()];
        boolean needToChangeOrder = false;
        int i = 0;
        for (PropEntityStep entity : propEntities) {
          SBuildRunnerDescriptor result = entity.addToInternalUnsafe(settings, serviceLocator);
          runnersOrder[i] = result.getId();
          List<SBuildRunnerDescriptor> currentRunners = settings.getBuildRunners();
          if (!needToChangeOrder && (currentRunners.size() < i + 1 || !currentRunners.get(i).getId().equals(result.getId()))) needToChangeOrder = true;
          i++;
        }
        if (needToChangeOrder) settings.applyRunnersOrder(runnersOrder);
      }
      return true;
    } catch (Exception e) {
      //restore original settings
      original.apply(settings);
      throw new BadRequestException("Error replacing items", e);
    }
  }

  public static void removeAllSteps(@NotNull final BuildTypeSettings buildType) {
    for (SBuildRunnerDescriptor entry : buildType.getBuildRunners()) {
      buildType.removeBuildRunner(entry.getId());  //todo: (TeamCity API): why string and not object?
    }
  }

  public static class Storage{
    public final Map<SBuildRunnerDescriptor, Boolean> deps = new LinkedHashMap<>();

    public Storage(final @NotNull BuildTypeSettings buildTypeSettings) {
      for (SBuildRunnerDescriptor entity : buildTypeSettings.getBuildRunners()) {
        deps.put(entity, buildTypeSettings.isEnabled(entity.getId()));
      }
    }

    public void apply(final @NotNull BuildTypeSettings buildTypeSettings){
      removeAllSteps(buildTypeSettings);
      for (Map.Entry<SBuildRunnerDescriptor, Boolean> entry : deps.entrySet()) {
        buildTypeSettings.addBuildRunner(entry.getKey());
        buildTypeSettings.setEnabled(entry.getKey().getId(), entry.getValue());
      }
    }
  }

}
