/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.model;

import jetbrains.buildServer.server.graphql.util.ObjectIdentificationNode;
import jetbrains.buildServer.clouds.CloudProfile;
import org.jetbrains.annotations.NotNull;

public class CloudImage implements ObjectIdentificationNode {
  private final jetbrains.buildServer.clouds.CloudImage myRealImage;
  private final CloudProfile myRealProfile;

  public CloudImage(@NotNull jetbrains.buildServer.clouds.CloudImage realImage, @NotNull CloudProfile profile) {
    myRealImage = realImage;
    myRealProfile = profile;
  }

  public String getRawId() {
    return myRealImage.getId();
  }

  public String getName() {
    return myRealImage.getName();
  }

  public String getProfileId() {
    return myRealProfile.getProfileId();
  }

  @NotNull
  public jetbrains.buildServer.clouds.CloudImage getRealImage() {
    return myRealImage;
  }

  @NotNull
  public CloudProfile getRealProfile() {
    return myRealProfile;
  }
}
