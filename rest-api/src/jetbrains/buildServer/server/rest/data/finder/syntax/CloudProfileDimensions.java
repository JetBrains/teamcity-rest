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

import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.PlainValue;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.CLOUD_PROFILE,
  baseEntity = "CloudProfile",
  examples = {
    "`name:MyCloudProfile` - find cloud profile with `MyCloudProfile` name.",
    "`project:<projectLocator>` - find all cloud profiles under project found by `projectLocator`."
  }
)
public class CloudProfileDimensions implements FinderLocatorDefinition {
  public static final Dimension ID = Dimension.ofName("id").description("Profile id.")
                                              .syntax(PlainValue.string()).build();
  public static final Dimension NAME = Dimension.ofName("name").description("Profile name.")
                                                .dimensions(ValueCondition.class).build();
  public static final Dimension CLOUD_PROVIDER_ID = Dimension.ofName("cloudProviderId").description("Profile cloud provider id.")
                                                             .syntax(PlainValue.string()).build();
  public static final Dimension INSTANCE = Dimension.ofName("instance").description("Cloud instance which belongs to a profile.")
                                                    .dimensions(CloudInstanceDimensions.class).build();
  public static final Dimension IMAGE = Dimension.ofName("image").description("Cloud image which belongs to a profile.")
                                                 .dimensions(CloudImageDimensions.class).build();
  public static final Dimension PROJECT = Dimension.ofName("project").description("Projects defining the cloud profiles.")
                                                   .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject").description("Projects where the cloud profiles are accessible")
                                                            .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();

  public static final Dimension PROPERTY = CommonLocatorDimensions.PROPERTY;
}
