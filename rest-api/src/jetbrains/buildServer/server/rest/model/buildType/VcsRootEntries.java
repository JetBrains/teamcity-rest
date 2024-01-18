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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 04.08.2009
 */
@XmlRootElement(name = "vcs-root-entries")
@ModelBaseType(ObjectType.LIST)
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

  public boolean setToBuildType(final BuildTypeOrTemplate buildType, final ServiceLocator serviceLocator) {
    serviceLocator.getSingletonService(PermissionChecker.class).checkCanEditBuildTypeOrTemplate(buildType);
    BuildTypeSettingsEx buildTypeSettings = buildType.getSettingsEx();

    Storage original = new Storage(buildTypeSettings);
    try {
      removeAllFrom(buildTypeSettings);
      if (vcsRootAssignments != null) {
        for (VcsRootEntry entity : vcsRootAssignments) {
          entity.addToInternalUnsafe(buildTypeSettings, serviceLocator.getSingletonService(VcsRootFinder.class));
        }
      }
      return true;
    } catch (Exception e) {
      //restore original settings
      original.applyUnsafe(buildTypeSettings);
      throw new BadRequestException("Error setting VCS roots", e);
    }
  }

  private static void removeAllFrom(final BuildTypeSettings buildType) {
    for (jetbrains.buildServer.vcs.VcsRootEntry entry : buildType.getVcsRootEntries()) {
      buildType.removeVcsRoot((SVcsRoot)entry.getVcsRoot()); //TeamCity open API issue
    }
  }

  public static class Storage {
    private final List<jetbrains.buildServer.vcs.VcsRootEntry> entities;

    public Storage(final @NotNull BuildTypeSettings buildTypeSettings) {
      entities = buildTypeSettings.getVcsRootEntries();
    }

    public void applyUnsafe(final @NotNull BuildTypeSettings buildTypeSettings) {
      removeAllFrom(buildTypeSettings);
      for (jetbrains.buildServer.vcs.VcsRootEntry entity : entities) {
        buildTypeSettings.addVcsRoot((SVcsRoot)entity.getVcsRoot());  //TeamCity open API issue
        buildTypeSettings.setCheckoutRules(entity.getVcsRoot(), entity.getCheckoutRules());
      }
    }
  }
}