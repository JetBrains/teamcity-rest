/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.vcs.SVcsModification;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
public class ChangeRef {
  protected SVcsModification myModification;
  protected ApiUrlBuilder myApiUrlBuilder;
  @Autowired WebLinks myWebLinks;

  public ChangeRef() {
  }

  public ChangeRef(SVcsModification modification, final ApiUrlBuilder apiUrlBuilder) {
    myModification = modification;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlAttribute
  public String getVersion() {
    return myModification.getDisplayVersion();
  }

  @XmlAttribute
  public long getId() {
    return myModification.getId();
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myModification);
  }

  @XmlAttribute
  public String getWebLink() {
    return myWebLinks.getChangeUrl(myModification.getId(), myModification.isPersonal());
  }

}
