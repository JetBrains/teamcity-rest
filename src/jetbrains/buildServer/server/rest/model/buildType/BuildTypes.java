/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildTypes")
@XmlType(name = "buildTypes")
public class BuildTypes {
  @XmlElement(name = "buildType", namespace = "ref")
  public List<BuildTypeRef> buildTypes;

  public BuildTypes() {
  }

  public static BuildTypes createFromBuildTypes(List<SBuildType> buildTypesObjects,
                                                @NotNull final DataProvider dataProvider,
                                                final ApiUrlBuilder apiUrlBuilder){
    final BuildTypes result = new BuildTypes();
    result.buildTypes = new ArrayList<BuildTypeRef>(buildTypesObjects.size());
    for (SBuildType buildType : buildTypesObjects) {
      result.buildTypes.add(new BuildTypeRef(buildType, dataProvider, apiUrlBuilder));
    }
    return result;
  }

  public static BuildTypes createFromTemplates(final List<BuildTypeTemplate> buildTypeTemplates,
                                  final DataProvider dataProvider,
                                  final ApiUrlBuilder apiUrlBuilder) {
    final BuildTypes result = new BuildTypes();
    result.buildTypes = new ArrayList<BuildTypeRef>(buildTypeTemplates.size());
    for (BuildTypeTemplate buildType : buildTypeTemplates) {
      result.buildTypes.add(new BuildTypeRef(buildType, dataProvider, apiUrlBuilder));
    }
    return result;
  }
}
