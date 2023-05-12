/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.versionedSettings;

import jetbrains.buildServer.controllers.project.VersionedSettingsBean;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.impl.versionedSettings.ConverterChangesStorage;
import jetbrains.buildServer.serverSide.impl.versionedSettings.CurrentVersionTracker;
import jetbrains.buildServer.serverSide.impl.versionedSettings.OutdatedProjectSettingsHealthReport;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatusTracker;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.vcs.VcsRegistry;
import jetbrains.vcs.api.impl.VcsContextLocator;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;


/**
 * This class provides a method for retrieving Versioned Settings beans,
 * which are originally used for the old UI server controllers.
 * Those beans contain all the necessary information about Versioned Settings setup,
 * and, therefore, could be reused for retrieving data
 * that could be mapped to the REST API model to be consistent with data in UI.
 */
@JerseyContextSingleton
@Component
public class VersionedSettingsBeanCollector {

  @NotNull private final VersionedSettingsManager myVersionedSettingsManager;
  @NotNull private final CurrentVersionTracker myCurrentVersionTracker;
  @NotNull private final VersionedSettingsStatusTracker myStatusTracker;
  @NotNull private final VcsRegistry myVcsRegistry;
  @NotNull private final VcsContextLocator myVcsContextLocator;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final ConverterChangesStorage myConverterChangesStorage;
  @NotNull private final OutdatedProjectSettingsHealthReport myOutdatedSettingsReport;

  public VersionedSettingsBeanCollector(@NotNull VersionedSettingsManager versionedSettingsManager,
                                        @NotNull CurrentVersionTracker currentVersionTracker,
                                        @NotNull VersionedSettingsStatusTracker statusTracker,
                                        @NotNull VcsRegistry vcsRegistry,
                                        @NotNull VcsContextLocator vcsContextLocator,
                                        @NotNull ProjectManager projectManager,
                                        @NotNull ConverterChangesStorage converterChangesStorage,
                                        @NotNull OutdatedProjectSettingsHealthReport outdatedProjectSettingsHealthReport) {
    myVersionedSettingsManager = versionedSettingsManager;
    myCurrentVersionTracker = currentVersionTracker;
    myStatusTracker = statusTracker;
    myVcsRegistry = vcsRegistry;
    myVcsContextLocator = vcsContextLocator;
    myProjectManager = projectManager;
    myConverterChangesStorage = converterChangesStorage;
    myOutdatedSettingsReport = outdatedProjectSettingsHealthReport;
  }

  /**
   * Returns Versioned Settings bean with all the necessary information
   * about Versioned Settings setup for current project.
   * @param project the project to return Versioned Settings bean for
   * @return see above
   */
  public VersionedSettingsBean getItem(@NotNull SProject project) {
    return new VersionedSettingsBean(project,
                                     myVersionedSettingsManager,
                                     myCurrentVersionTracker,
                                     myStatusTracker,
                                     myVcsRegistry,
                                     myVcsContextLocator,
                                     myProjectManager,
                                     myConverterChangesStorage,
                                     myOutdatedSettingsReport);
  }
}
