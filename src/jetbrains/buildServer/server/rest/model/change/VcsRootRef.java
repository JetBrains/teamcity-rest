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

import com.intellij.openapi.util.text.StringUtil;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "vcs-root-ref")
@XmlType(name = "vcs-root-ref", propOrder = {"id", "internalId", "name", "href"})
public class VcsRootRef {
  @XmlAttribute
  public String id;
  @XmlAttribute
  public Long internalId;
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String href;
  /**
   * This is used only when posting a link to a build type.
   */
  @XmlAttribute public String locator;

  public VcsRootRef() {
  }

  public VcsRootRef(jetbrains.buildServer.vcs.SVcsRoot root, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this.id = root.getExternalId();
    this.internalId =  TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? root.getId() : null;
    this.href = apiUrlBuilder.getHref(root);
    this.name = root.getName();
  }

  @NotNull
  public SVcsRoot getVcsRoot(@NotNull VcsRootFinder vcsRootFinder) {
    String locatorText = "";
//    if (internalId != null) locatorText = "internalId:" + internalId;
    if (id != null) locatorText += (!locatorText.isEmpty() ? "," : "") + "id:" + id;
    if (locatorText.isEmpty()) {
      locatorText = locator;
    } else {
      if (locator != null) {
        throw new BadRequestException("Both 'locator' and 'id' attributes are specified. Only one should be present.");
      }
    }
    if (StringUtil.isEmpty(locatorText)){
      throw new BadRequestException("No VCS root specified. Either 'id' or 'locator' attribute should be present.");
    }
    return vcsRootFinder.getVcsRoot(locatorText);
  }
}
