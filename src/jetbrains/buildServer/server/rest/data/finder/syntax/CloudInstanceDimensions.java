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

package jetbrains.buildServer.server.rest.data.finder.syntax;

import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.server.rest.data.finder.impl.CloudProfileFinder;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.EnumValue;
import jetbrains.buildServer.server.rest.data.locator.PlainValue;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.CLOUD_INSTANCE,
  baseEntity = "CloudInstance",
  examples = {
    "`agent:<agentLocator>` - find cloud instance which hosts agent found by `agentLocator`.",
    "`profile:<profileLocator>` - find all cloud instances in cloud profile found by `profileLocator`."
  }
)
public class CloudInstanceDimensions implements FinderLocatorDefinition {
  public static final Dimension ID = Dimension.ofName("id").description("Instance id as provided by list instances call.")
                                              .syntax(PlainValue.string("Should be in the form \"profileId:<profileId>,imageId:<imageId>,id:<instanceId>\".")).build();
  public static final Dimension ERROR = Dimension.ofName("errorMessage").description("Instance error message.")
                                                 .syntax(PlainValue.string()).hidden().build();
  public static final Dimension STATE = Dimension.ofName("state").description("Instance state.")
                                                 .syntax(EnumValue.of(InstanceStatus.class)).hidden().build();
  public static final Dimension NETWORK_ADDRESS = Dimension.ofName("networkAddress").description("Instance network address.")
                                                           .syntax(PlainValue.string("DNS name or IP address")).build();
  public static final Dimension START_DATE = Dimension.ofName("startDate").description("Instance start time.")
                                                      .syntax(Syntax.TODO("Time condition")).hidden().build();
  public static final Dimension AGENT = Dimension.ofName("agent").description("Agent running on an instance.")
                                                 .syntax(Syntax.forLocator(LocatorName.AGENT)).build();
  public static final Dimension IMAGE = Dimension.ofName("image").description("Cloud image corresponding to an instance.")
                                                 .dimensions(CloudImageDimensions.class).build();
  public static final Dimension PROFILE = Dimension.ofName("profile").description("Cloud profile of an instance.")
                                                   .dimensions(CloudProfileFinder.class).build();
  public static final Dimension PROJECT = Dimension.ofName("project").description("Project defining the cloud profiles/images.")
                                                   .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject").description("Projects where the cloud profiles/images are accessible.")
                                                            .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension PROPERTY = CommonLocatorDimensions.PROPERTY;
}
