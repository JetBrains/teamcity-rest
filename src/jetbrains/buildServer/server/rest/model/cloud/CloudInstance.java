/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.clouds.server.StartInstanceReason;
import jetbrains.buildServer.server.rest.data.CloudInstanceData;
import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.request.CloudRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 21.08.2019
 */
@XmlRootElement(name = "cloudInstance")
@XmlType(name = "cloudInstance", propOrder = {"id", "name", "state", "startDate", "networkAddress", "href",
  "image", "agent", "errorMessage"})
@SuppressWarnings("PublicField")
public class CloudInstance {

  @NotNull private final CloudInstanceData myCloudInstance;
  @NotNull private final Fields myFields;
  @NotNull private final BeanContext myBeanContext;

  @SuppressWarnings("ConstantConditions")
  public CloudInstance() {
    myCloudInstance = null;
    myFields = null;
    myBeanContext = null;
  }

  public CloudInstance(@NotNull final CloudInstanceData cloudInstance, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myCloudInstance = cloudInstance;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlAttribute
  public String getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id", true, true), () -> myCloudInstance.getId());
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("name", true, true), () -> myCloudInstance.getInstance().getName());
  }

  @XmlAttribute
  public String getState() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("state", true, true), () -> myCloudInstance.getState());
  }

  @XmlAttribute
  public String getStartDate() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("startDate", true, true), () -> Util.formatTime(myCloudInstance.getStartDate()));
  }

  @XmlAttribute
  public String getNetworkAddress() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("networkAddress", true, true), () -> myCloudInstance.getInstance().getNetworkIdentity());
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href", true), () -> myBeanContext.getApiUrlBuilder().transformRelativePath(CloudRequest.getHref(myCloudInstance)));
  }

  @XmlElement(name = "image")
  public CloudImage getImage() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("image", true, true),
                                          () -> new CloudImage(myCloudInstance.getInstance().getImage(), myFields.getNestedField("image", Fields.SHORT, Fields.SHORT), myBeanContext));
  }

  @XmlElement(name = "agent")
  public Agent getAgent() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("agent", false),
                                          () -> Util.resolveNull(myCloudInstance.getAgent(), a -> new Agent(a, myFields.getNestedField("agent"), myBeanContext)));
  }

  @XmlElement(name = "errorMessage")
  public String getErrorMessage() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("errorMessage", true), () -> myCloudInstance.getError());
  }


  private CloudImage submittedImage;

  public void setImage(CloudImage item) {
    submittedImage = item;
  }


  @Nullable
  public jetbrains.buildServer.clouds.CloudInstance startInstance(@NotNull final SUser user, @NotNull final ServiceLocator serviceLocator) {
    if (submittedImage == null) {

    }
    CloudUtil util = serviceLocator.getSingletonService(CloudUtil.class);
    jetbrains.buildServer.clouds.CloudImage image = submittedImage.getFromPosted(serviceLocator);
    CloudProfile profile = util.getProfile(image);
    if (profile == null) {
      throw new InvalidStateException("Cannot find profile for the cloud image");
    }
    CloudManager cloudManager = serviceLocator.getSingletonService(CloudManager.class);
    cloudManager.startInstance(profile.getProfileId(), image.getId(), StartInstanceReason.userAction(user));
    return null;
  }
}
