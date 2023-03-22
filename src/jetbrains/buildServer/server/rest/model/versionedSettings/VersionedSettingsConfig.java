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

package jetbrains.buildServer.server.rest.model.versionedSettings;


import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.controllers.project.VersionedSettingsBean;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "versionedSettingsConfig")
@ModelDescription(
  value = "Represents a Versioned Settings Config.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/storing-project-settings-in-version-control.html",
  externalArticleName = "Storing Project Settings in Version Control"
)
public class VersionedSettingsConfig {

  private SyncronizationMode mySyncronizationMode;

  private String myVcsRootId;

  private Boolean myShowSettingsChanges;

  private BuildSettingsMode myBuildSettingsMode;

  private String myFormat;

  private Boolean myAllowUIEditing;

  private Boolean myStoreSecureValuesOutsideVcs;

  private Boolean myPortableDsl;

  private ImportDecision myImportDecision;


  @SuppressWarnings("unused")
  public VersionedSettingsConfig() {
  }

  public VersionedSettingsConfig(@NotNull VersionedSettingsBean versionedSettingsBean, @NotNull Fields fields) {
    mySyncronizationMode = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("syncronizationMode"),
                                                                   SyncronizationMode.fromBeanString(versionedSettingsBean.getSynchronizationMode()));
    myVcsRootId = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("vcsRootId"), () -> {
      SVcsRoot vcsRoot = versionedSettingsBean.getConfiguredVcsRoot();
      return vcsRoot == null ? null : vcsRoot.getExternalId();
    });
    myShowSettingsChanges = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("showSettingsChanges"), versionedSettingsBean.isShowSettingsChanges());
    myBuildSettingsMode = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("buildSettingsMode"),
                                                                  BuildSettingsMode.fromBeanString(versionedSettingsBean.getBuildSettingsMode()));
    myFormat = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("format"), versionedSettingsBean.getSettingsFormat());
    myAllowUIEditing = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("allowUIEditing"), versionedSettingsBean.isUseTwoWaySynchronization());
    if (versionedSettingsBean.isUseTwoWaySynchronization()) {
      myStoreSecureValuesOutsideVcs = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("storeSecureValuesOutsideVcs"),
                                                                              versionedSettingsBean.isUseCredentialsStorage());
    }
    Collection<VersionedSettingsBean.SettingsFormat> availableSettingsFormats = versionedSettingsBean.getAvailableSettingsFormats();
    Optional<VersionedSettingsBean.SettingsFormat> settingsFormat = availableSettingsFormats.stream()
                                                                                            .filter(it -> it.getId().equals(versionedSettingsBean.getSettingsFormat()))
                                                                                            .findFirst();
    if (settingsFormat.isPresent() && settingsFormat.get().isSupportsRelativeIds() && !versionedSettingsBean.isCheckedRelativeIds()) {
      myPortableDsl = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("portableDsl"), false);
    }
    myImportDecision = null;
  }


  public String getFieldValue(@NotNull String fieldName) {
    switch (fieldName) {
      case "syncronizationMode": return String.valueOf(mySyncronizationMode);
      case "vcsRootId": return String.valueOf(myVcsRootId);
      case "showSettingsChanges": return String.valueOf(myShowSettingsChanges);
      case "buildSettingsMode": return String.valueOf(myBuildSettingsMode);
      case "format": return String.valueOf(myFormat);
      case "allowUIEditing": return String.valueOf(myAllowUIEditing);
      case "storeSecureValuesOutsideVcs": return String.valueOf(myStoreSecureValuesOutsideVcs);
      case "portableDsl": return String.valueOf(myPortableDsl);
      default: throw new NotFoundException("Parameter with name '" + fieldName + "' is not found.");
    }
  }

  public void setFieldValue(@NotNull String fieldName, @Nullable String newValue) {
    switch (fieldName) {
      case "syncronizationMode":
        mySyncronizationMode = newValue == null ? null : SyncronizationMode.valueOf(newValue);
        return;
      case "vcsRootId":
        myVcsRootId = newValue;
        return;
      case "showSettingsChanges":
        myShowSettingsChanges = newValue == null ? null : Boolean.valueOf(newValue);
        return;
      case "buildSettingsMode":
        myBuildSettingsMode = newValue == null ? null : BuildSettingsMode.valueOf(newValue);
        return;
      case "format":
        myFormat = newValue;
        return;
      case "allowUIEditing":
        myAllowUIEditing = newValue == null ? null : Boolean.valueOf(newValue);
        return;
      case "storeSecureValuesOutsideVcs":
        myStoreSecureValuesOutsideVcs = newValue == null ? null : Boolean.valueOf(newValue);
        return;
      case "portableDsl":
        myPortableDsl = newValue == null ? null : Boolean.valueOf(newValue);
        return;
      default:
        throw new NotFoundException("Parameter with name '" + fieldName + "' is not found.");
    }
  }

  @XmlAttribute(name = "syncronizationMode")
  public SyncronizationMode getSyncronizationMode() {
    return mySyncronizationMode;
  }

  public void setSyncronizationMode(SyncronizationMode syncronizationMode) {
    mySyncronizationMode = syncronizationMode;
  }

  @XmlAttribute(name = "vcsRootId")
  public String getVcsRootId() {
    return myVcsRootId;
  }

  public void setVcsRootId(String vcsRootId) {
    myVcsRootId = vcsRootId;
  }

  @XmlAttribute(name = "showSettingsChanges")
  public Boolean getShowSettingsChanges() {
    return myShowSettingsChanges;
  }

  @XmlAttribute(name = "buildSettingsMode")
  public BuildSettingsMode getBuildSettingsMode() {
    return myBuildSettingsMode;
  }

  public void setBuildSettingsMode(BuildSettingsMode buildSettingsMode) {
    myBuildSettingsMode = buildSettingsMode;
  }

  @XmlAttribute(name = "format")
  public String getFormat() {
    return myFormat;
  }

  public void setFormat(String format) {
    myFormat = format;
  }

  @XmlAttribute(name = "allowUIEditing")
  public Boolean getAllowUIEditing() {
    return myAllowUIEditing;
  }

  public void setAllowUIEditing(Boolean allowUIEditing) {
    myAllowUIEditing = allowUIEditing;
  }

  @XmlAttribute(name = "storeSecureValuesOutsideVcs")
  public Boolean getStoreSecureValuesOutsideVcs() {
    return myStoreSecureValuesOutsideVcs;
  }

  public void setStoreSecureValuesOutsideVcs(Boolean storeSecureValuesOutsideVcs) {
    myStoreSecureValuesOutsideVcs = storeSecureValuesOutsideVcs;
  }

  @XmlAttribute(name = "portableDsl")
  public Boolean getPortableDsl() {
    return myPortableDsl;
  }

  public void setPortableDsl(Boolean portableDsl) {
    myPortableDsl = portableDsl;
  }

  @XmlAttribute(name = "importDecision")
  public ImportDecision getImportDecision() {
    return myImportDecision;
  }


  public enum BuildSettingsMode {
    alwaysUseCurrent(jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode.ALWAYS_USE_CURRENT),
    useCurrentByDefault(jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode.PREFER_CURRENT),
    useFromVCS(jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode.PREFER_VCS);

    @NotNull
    private final jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode myBuildSettingsMode;

    BuildSettingsMode(@NotNull jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode buildSettingsMode) {
      myBuildSettingsMode = buildSettingsMode;
    }

    @Nullable
    static BuildSettingsMode fromBeanString(@NotNull String buildSettingsModeString) {
      try {
        jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode buildSettingsMode =
          jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode.valueOf(buildSettingsModeString);
        Optional<BuildSettingsMode> result = Arrays.stream(BuildSettingsMode.values())
                                                   .filter(mode -> mode.getBuildSettingsMode().equals(buildSettingsMode))
                                                   .findFirst();
        return result.orElse(null);
      } catch (Throwable ignored) {
        return null;
      }
    }

    @NotNull
    public jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode getBuildSettingsMode() {
      return myBuildSettingsMode;
    }
  }


  public enum SyncronizationMode {
    useParentProjectSettings("default"),
    disabled("disabled"),
    enabled("enabled");

    @NotNull
    private final String myParamValue;

    SyncronizationMode(@NotNull String paramValue) {
      myParamValue = paramValue;
    }

    @NotNull
    public String getParamValue() {
      return myParamValue;
    }

    @Nullable
    static SyncronizationMode fromBeanString(@NotNull String syncronizationModeString) {
       return Arrays.stream(SyncronizationMode.values())
                    .filter(mode -> mode.myParamValue.equals(syncronizationModeString))
                    .findFirst()
                    .orElse(null);
    }
  }


  public enum ImportDecision {
    overrideInVCS("override"),
    importFromVCS("import");

    @NotNull
    private final String myParamValue;

    ImportDecision(@NotNull String paramValue) {
      myParamValue = paramValue;
    }

    @NotNull
    public String getParamValue() {
      return myParamValue;
    }
  }
}
