/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 25/02/2016
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "link")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a list of URLs."))
public class Link {
  public static final String WEB_VIEW_TYPE = "webView";
  public static final String WEB_EDIT_TYPE = "webEdit";
  public static final String WEB_VIEW_SETTINGS_TYPE = "webViewSettings";
  @XmlAttribute
  public String type;

  @XmlAttribute
  public String url;

  @XmlAttribute
  public String relativeUrl;

  public Link() {
  }

  public Link(@NotNull final String type, @NotNull final String url, @Nullable final String relativeUrl, @NotNull final Fields fields) {
    this.type = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("type"), type);
    this.url = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("url"), url);
    this.relativeUrl = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("relativeUrl"), relativeUrl);
  }
}
