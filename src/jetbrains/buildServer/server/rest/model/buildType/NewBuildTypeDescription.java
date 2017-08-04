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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.CopyOptionsDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.identifiers.BuildTypeIdentifiersManager;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 04.01.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "newBuildTypeDescription")
public class NewBuildTypeDescription extends CopyOptionsDescription{

  public NewBuildTypeDescription() {
  }

  public NewBuildTypeDescription(final String name, final String id, final BuildType sourceBuildType, final Boolean copyAllAssociatedSettings,
                                 @NotNull final BeanContext beanContext) {
    super(copyAllAssociatedSettings, null, null, null, beanContext);
    this.name = name;
    this.id = id;
    this.sourceBuildType = sourceBuildType;
  }

  @XmlAttribute public String name;

  /**
   * External id
   */
  @XmlAttribute public String id;
  //todo: would be cool to support specifying internalId here

  /**
   * @deprecated Use 'sourceBuildType' intead.
   */
  @Deprecated
  @XmlAttribute public String sourceBuildTypeLocator;

  @XmlElement(name = "sourceBuildType")
  public BuildType sourceBuildType;

  @Nullable
  public BuildTypeOrTemplate getSourceBuildTypeOrTemplate(@NotNull final ServiceLocator serviceLocator) {
    final BuildTypeFinder buildTypeFinder = serviceLocator.getSingletonService(BuildTypeFinder.class);
    if (sourceBuildType == null) {
      if (StringUtil.isEmpty(sourceBuildTypeLocator)) {
        return null;
      } else {
        return buildTypeFinder.getBuildTypeOrTemplate(null, sourceBuildTypeLocator, false);
      }
    }
    if (!StringUtil.isEmpty(sourceBuildTypeLocator)) {
      throw new BadRequestException("Both 'sourceBuildType' and 'sourceBuildTypeLocator' are specified. Please use only the former.");
    }
    return sourceBuildType.getBuildTypeFromPosted(buildTypeFinder);
  }

  @NotNull
  public String getName() {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Not empty 'name' should be specified.");
    }
    return name;
  }

  @NotNull
  public String getId(@NotNull final ServiceLocator serviceLocator, @NotNull SProject project) {
    if (id == null){
      id = serviceLocator.getSingletonService(BuildTypeIdentifiersManager.class).generateNewExternalId(project.getExternalId(), getName(), null);
    }
    return id;
  }
}
