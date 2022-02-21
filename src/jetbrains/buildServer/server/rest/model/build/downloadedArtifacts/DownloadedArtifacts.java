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

package jetbrains.buildServer.server.rest.model.build.downloadedArtifacts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.Build;
import jetbrains.buildServer.server.rest.data.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.data.FilterConditionChecker;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ArtifactInfo;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.impl.Lazy;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "downloadedArtifacts")
@ModelBaseType(ObjectType.LIST)
@ModelDescription("Collection of artifacts metadata which were downloaded from dependencies of this build or delivered to ones depending on this build.")
public class DownloadedArtifacts implements DefaultValueAware {
  private jetbrains.buildServer.serverSide.DownloadedArtifacts myArtifacts;
  private final Lazy<Map<SBuild, List<ArtifactInfo>>> myFilteredInfo = new Lazy<Map<SBuild, List<ArtifactInfo>>>() {
    @Override
    protected Map<SBuild, List<ArtifactInfo>> createValue() {
      return getFilteredInfo();
    }
  };

  private Fields myFields;
  private BeanContext myBeanContext;

  public DownloadedArtifacts() {}

  public DownloadedArtifacts(@NotNull jetbrains.buildServer.serverSide.DownloadedArtifacts artifacts, @NotNull Fields fields, @NotNull BeanContext context) {
    myArtifacts = artifacts;
    myFields = fields;
    myBeanContext = context;
  }

  @XmlAttribute(name = "unfilteredCount")
  public Integer getUnfilteredCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("unfilteredCount", false, false), myArtifacts.getActualNumberOfBuilds());
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("count"), myFilteredInfo.get().size());
  }

  @XmlElement(name="downloadInfo")
  public List<BuildArtifactsDownloadInfo> getDownloadInfo() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("downloadInfo"),
      () -> {
        Fields inner = myFields.getNestedField("downloadInfo");
        return myFilteredInfo.get().entrySet().stream()
                             .map(pair -> new BuildArtifactsDownloadInfo(pair.getKey(), pair.getValue(), inner, myBeanContext))
                             .collect(Collectors.toList());
      }
    );
  }

  @NotNull
  private Map<SBuild, List<ArtifactInfo>> getFilteredInfo() {
    Map<SBuild, List<ArtifactInfo>> result = new HashMap<>();
    String buildLocatorText = myFields.getLocator();
    FilterConditionChecker<BuildPromotion> buildFilter;
    if(StringUtil.isNotEmpty(buildLocatorText)) {
      Locator buildLocator = Locator.locator(buildLocatorText);
      buildFilter = myBeanContext.getSingletonService(BuildPromotionFinder.class).getFilter(buildLocator);
      buildLocator.checkLocatorFullyProcessed();
    } else {
      buildFilter = b -> true;
    }

    for(Map.Entry<Build, List<ArtifactInfo>> buildArtifacts : myArtifacts.getArtifacts().entrySet()) {
      SBuild build = (SBuild) buildArtifacts.getKey();
      if(!buildFilter.isIncluded(build.getBuildPromotion())) {
        continue;
      }

      result.put(build, buildArtifacts.getValue());
    }

    return result;
  }

  @Override
  public boolean isDefault() {
    return myArtifacts.getActualNumberOfBuilds() == 0;
  }
}
