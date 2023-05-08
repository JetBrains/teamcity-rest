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

import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.data.CloudInstanceData;
import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.data.finder.impl.CloudImageFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.agent.AgentType;
import jetbrains.buildServer.server.rest.request.CloudRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelExperimental;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.util.impl.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 21.08.2019
 */
@XmlRootElement(name = "cloudImage")
@XmlType(
  name = "cloudImage",
  propOrder = {"id", "name", "href", "profile", "instances", "errorMessage", "agentTypeId", "agentType", "agentPoolId", "operatingSystemName",
    "locator" /* never returned. used only for POST */,
  }
)
@ModelDescription(
    value = "Represents a cloud instance image saved with a profile.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/agent-cloud-profile.html",
    externalArticleName = "Cloud Profile"
)
public class CloudImage {

  @NotNull private final jetbrains.buildServer.clouds.CloudImage myCloudImage;
  @NotNull private final Fields myFields;
  @NotNull private final BeanContext myBeanContext;
  @NotNull private final Lazy<Optional<SAgentType>> myAgentType;

  @SuppressWarnings("ConstantConditions")
  public CloudImage() {
    myCloudImage = null;
    myFields = null;
    myBeanContext = null;
    myAgentType = null;
  }

  public CloudImage(@NotNull jetbrains.buildServer.clouds.CloudImage cloudImage, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myCloudImage = cloudImage;
    myFields = fields;
    myBeanContext = beanContext;
    myAgentType = new Lazy<Optional<SAgentType>>() {
      @NotNull
      @Override
      protected Optional<SAgentType> createValue() {
        jetbrains.buildServer.clouds.CloudProfile profile = myBeanContext.getSingletonService(CloudUtil.class).getProfile(myCloudImage);
        if(profile == null) {
          return Optional.empty();
        }
        return Optional.ofNullable(myBeanContext.getSingletonService(CloudManager.class).getDescriptionFor(profile, myCloudImage.getId()));
      }
    };
  }

  @XmlAttribute
  public String getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id", true, true),
                                          () -> myBeanContext.getSingletonService(CloudUtil.class).getId(myCloudImage));
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("name", true, true), myCloudImage::getName);
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href", true), () -> myBeanContext.getApiUrlBuilder().transformRelativePath(CloudRequest.getHref(myCloudImage, myBeanContext.getSingletonService(CloudUtil.class))));
  }

  @XmlElement(name = "profile")
  public CloudProfile getProfile() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("profile", false),
                                          () -> Util.resolveNull(myBeanContext.getSingletonService(CloudUtil.class).getProfile(myCloudImage), p -> new CloudProfile(p, myFields.getNestedField("profile"), myBeanContext)));
  }

  @XmlElement(name = "instances")
  public CloudInstances getInstances() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("instances", false, true),
      () -> new CloudInstances(
        CachingValue.simple(() -> myCloudImage.getInstances().stream()
                                              .map(i -> new CloudInstanceData(i, myCloudImage.getProfileId(), myBeanContext.getServiceLocator()))
                                              .collect(Collectors.toList())),
        new PagerDataImpl(CloudRequest.getInstancesHref(myCloudImage, myBeanContext.getSingletonService(CloudUtil.class))),
        myFields.getNestedField("instances", Fields.NONE, Fields.LONG), myBeanContext)
    );
  }

  @Nullable
  @XmlElement(name = "agentTypeId")
  public Integer getAgentTypeId() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("agentTypeId", false, false),
      () -> myAgentType.get().map(SAgentType::getAgentTypeId).orElse(null)
    );
  }

  @Nullable
  @ModelExperimental
  @XmlElement(name = "agentType")
  public AgentType getAgentType() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("agentType", false, false),
      () -> myAgentType
        .get()
        .map(agentType -> new AgentType(agentType, myFields.getNestedField("agentType"), myBeanContext.getServiceLocator(), myBeanContext.getApiUrlBuilder()))
        .orElse(null)
    );
  }

  @Nullable
  @XmlElement(name = "agentPoolId")
  public Integer getAgentPoolId() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("agentPoolId", false, false),
      () -> myAgentType.get().map(SAgentType::getAgentPoolId).orElse(null)
    );
  }

  @Nullable
  @XmlElement(name = "operatingSystemName")
  public String getOperatingSystemName() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("operatingSystemName", false, false),
      () -> myAgentType.get().map(SAgentType::getOperatingSystemName).orElse(null)
    );
  }

  @XmlElement(name = "errorMessage")
  public String getErrorMessage() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("errorMessage", true), () -> Util.resolveNull(myCloudImage.getErrorInfo(), e-> e.getMessage()));
  }

  private String submittedId;
  private String submittedLocator;

  public void setId(final String id) {
    submittedId = id;
  }

  /**
   * This is used only when posting a link to the image
   */
  @XmlAttribute
  public String getLocator() {
    return null;
  }

  public void setLocator(final String locator) {
    submittedLocator = locator;
  }

  @NotNull
  public jetbrains.buildServer.clouds.CloudImage getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (submittedLocator != null && submittedId != null) {
      throw new BadRequestException("Invalid submitted image: both 'id' and 'locator' are specified.");
    }
    CloudImageFinder finder = serviceLocator.getSingletonService(CloudImageFinder.class);
    if (submittedLocator != null) {
      return finder.getItem(submittedLocator);
    }
    if (submittedId != null) {
      return finder.getItem(CloudImageFinder.getLocatorById(submittedId));
    }
    throw new BadRequestException("Invalid submitted image: neither 'id' nor 'locator' are specified.");
  }
}
