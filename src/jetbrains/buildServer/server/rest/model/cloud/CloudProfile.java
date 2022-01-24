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

package jetbrains.buildServer.server.rest.model.cloud;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.CloudRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 21.08.2019
 */
@XmlRootElement(name = "cloudProfile")
@XmlType(name = "cloudProfile", propOrder = {"id", "name", "cloudProviderId", "href",
"project", "images"})
@SuppressWarnings("PublicField")
@ModelDescription(
    value = "Represents a cloud agent profile.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/agent-cloud-profile.html",
    externalArticleName = "Cloud Profile"
)
public class CloudProfile {

  @NotNull private final jetbrains.buildServer.clouds.CloudProfile myCloudProfile;
  @NotNull private final Fields myFields;
  @NotNull private final BeanContext myBeanContext;

  @SuppressWarnings("ConstantConditions")
  public CloudProfile() {
    myCloudProfile = null;
    myFields = null;
    myBeanContext = null;
  }

  public CloudProfile(@NotNull final jetbrains.buildServer.clouds.CloudProfile cloudProfile, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myCloudProfile = cloudProfile;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlAttribute
  public String getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id", true, true), myCloudProfile::getProfileId);
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("name", true, true), myCloudProfile::getProfileName);
  }

  @XmlAttribute
  public String getCloudProviderId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("cloudProviderId", true, true), myCloudProfile::getCloudCode);
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href", true), () -> myBeanContext.getApiUrlBuilder().transformRelativePath(CloudRequest.getHref(myCloudProfile)));
  }

  @XmlElement(name = "project")
  public Project getProject() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("project", false),
                                          () -> Util.resolveNull(myBeanContext.getSingletonService(CloudUtil.class).getProject(myCloudProfile),
                                                                 p -> new Project(p, myFields.getNestedField("project"), myBeanContext),
                                                                 new Project(null, myCloudProfile.getProjectId(), myFields.getNestedField("project"), myBeanContext)));
  }

  @XmlElement(name = "images")
  public CloudImages getImages() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("images", false, true),
                                          () -> new CloudImages(
                                            CachingValue.simple(() -> new ArrayList<>(myBeanContext.getSingletonService(CloudUtil.class).getImages(myCloudProfile))),
                                            new PagerData(CloudRequest.getImagesHref(myCloudProfile)),
                                            myFields.getNestedField("images", Fields.NONE, Fields.LONG), myBeanContext));
  }
}
