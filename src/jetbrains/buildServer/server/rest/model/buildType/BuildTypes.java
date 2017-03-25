/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildTypes")
@XmlType(name = "buildTypes")
public class BuildTypes {
  @XmlAttribute
  public Integer count;

  @XmlAttribute
  @Nullable
  public String href;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  @XmlElement(name = "buildType")
  public List<BuildType> buildTypes;

  public BuildTypes() {
  }

  public BuildTypes(@NotNull final List<BuildTypeOrTemplate> items, @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    if (fields.isIncluded("buildType", false, true)){
      this.buildTypes = new ArrayList<BuildType>(items.size());
      for (BuildTypeOrTemplate buildType : items) {
        this.buildTypes.add(new BuildType(buildType, fields.getNestedField("buildType"), beanContext));
      }
    }
    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getHref()));
      nextHref = ValueWithDefault
        .decideDefault(fields.isIncluded("nextHref"), pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null);
      prevHref = ValueWithDefault
        .decideDefault(fields.isIncluded("prevHref"), pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null);
    }
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), items.size());
  }

  @NotNull
  public List<BuildTypeOrTemplate> getFromPosted(@NotNull final BuildTypeFinder buildTypeFinder) {
    if (buildTypes == null){
      return Collections.emptyList();
    }
    final ArrayList<BuildTypeOrTemplate> result = new ArrayList<BuildTypeOrTemplate>(buildTypes.size());
    for (BuildType buildType : buildTypes) {
      result.add(buildType.getBuildTypeFromPosted(buildTypeFinder));
    }
    return result;
  }

  @NotNull
  public static List<BuildTypeOrTemplate> fromBuildTypes(Collection<SBuildType> source){
    return CollectionsUtil.convertCollection(source, new Converter<BuildTypeOrTemplate, SBuildType>() {
      public BuildTypeOrTemplate createFrom(@NotNull final SBuildType source) {
        return new BuildTypeOrTemplate(source);
      }
    });
  }

  @NotNull
  public static List<BuildTypeOrTemplate> fromTemplates(Collection<BuildTypeTemplate> source){
    return CollectionsUtil.convertCollection(source, new Converter<BuildTypeOrTemplate, BuildTypeTemplate>() {
      public BuildTypeOrTemplate createFrom(@NotNull final BuildTypeTemplate source) {
        return new BuildTypeOrTemplate(source);
      }
    });
  }
}
