/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.ChangeFinder;
import jetbrains.buildServer.server.rest.request.ChangeRequest;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@SuppressWarnings("NullableProblems")
@XmlRootElement(name = "changes-ref")
@XmlType(name = "changes-ref")
public class ChangesRef {
  @Nullable private BuildPromotion myBuildPromotion;
  @Nullable private SBuild myBuild;
  private ApiUrlBuilder myApiUrlBuilder;
  private Integer myCachedCount = null;

  public ChangesRef() {
  }

  public ChangesRef(@NotNull final SBuild build, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myBuild = build;
    myBuildPromotion = null;
    myApiUrlBuilder = apiUrlBuilder;
  }

  public ChangesRef(@NotNull final BuildPromotion buildPromotion, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myBuildPromotion = buildPromotion;
    myBuild = null;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlAttribute
  public String getHref() {
    //noinspection ConstantConditions
    return getCount() > 0
           ? myBuild != null ? myApiUrlBuilder.getBuildChangesHref(myBuild) : myApiUrlBuilder.transformRelativePath(ChangeRequest.getChangesHref(myBuildPromotion))
           : null;
  }

  @XmlAttribute
  public int getCount() {
    if (myCachedCount == null) {
      //todo: not efficient
      //noinspection ConstantConditions
      myCachedCount = myBuild != null ? ChangeFinder.getBuildChanges(myBuild.getBuildPromotion()).size() : ChangeFinder.getBuildChanges(myBuildPromotion).size();
    }
    return myCachedCount;
  }
}