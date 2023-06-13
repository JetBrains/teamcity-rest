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

import jetbrains.buildServer.server.rest.data.finder.impl.CloudProfileFinder;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.CLOUD_IMAGE,
  baseEntity = "CloudImage",
  examples = {
    "`name:MyImage` - find image with name `MyImage`.",
    "`profile:<profileLocator>` - find all images in cloud profile found by `profileLocator`."
  }
)
public class CloudImageDimensions implements FinderLocatorDefinition {
  public static final Dimension ID = Dimension.ofName("id").description("Image id as provided by list images call.")
                                              .syntax(Syntax.TODO("Specially formatted text")).build();
  public static final Dimension NAME = Dimension.ofName("name").description("Image name.")
                                                .syntax(Syntax.TODO("Value condition")).build();
  public static final Dimension ERROR = Dimension.ofName("errorMessage").description("Image error message.")
                                                 .hidden().build();

  public static final Dimension AGENT = Dimension.ofName("agent").description("Agent locator.")
                                                 .syntax(Syntax.forLocator(LocatorName.AGENT)).build();
  public static final Dimension AGENT_POOL = Dimension.ofName("agentPool").description("Agent pool locator.")
                                                      .syntax(Syntax.forLocator(LocatorName.AGENT_POOL)).build();
  public static final Dimension INSTANCE = Dimension.ofName("instance").description("Cloud instance locator.")
                                                    .dimensions(CloudInstanceDimensions.class).build();
  public static final Dimension PROFILE = Dimension.ofName("profile").description("Cloud profile locator.")
                                                   .dimensions(CloudProfileFinder.class).build();
  public static final Dimension PROJECT = Dimension.ofName("project").description("Projects defining the cloud profiles/images.")
                                                   .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject").description("Projects where the cloud profiles/images are accessible.")
                                                            .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension COMPATIBLE_BUILD_TYPE = Dimension.ofName("compatibleBuildType").description("Build type locator.")
                                                                 .syntax(Syntax.forLocator(LocatorName.BUILD_TYPE)).build();
  public static final Dimension COMPATIBLE_BUILD_PROMOTION = Dimension.ofName("compatibleBuildPromotion").description("Build promotion locator.")
                                                                      .syntax(Syntax.forLocator(LocatorName.BUILD)).build();

  public static final Dimension PROPERTY = CommonLocatorDimensions.PROPERTY;
}
