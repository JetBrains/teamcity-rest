/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsManager;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "change")
public class Change extends ChangeRef {

  @Autowired VcsManager myVcsManager;
  @Autowired BeanFactory myFactory;

  public Change() {
  }

  public Change(SVcsModification modification, final ApiUrlBuilder apiUrlBuilder, final BeanFactory myFactory) {
    super(modification, apiUrlBuilder, myFactory);
  }

  @XmlAttribute
  public String getUsername() {
    return myModification.getUserName();
  }

  @XmlAttribute
  public String getDate() {
    final Date vcsDate = myModification.getVcsDate();
    if (vcsDate != null) {
      return Util.formatTime(vcsDate);
    }
    return null;
  }

  @XmlElement
  public String getComment() {
    return myModification.getDescription();
  }

  @XmlElement
  public UserRef getUser() {
    final Collection<SUser> users = myVcsManager.getModificationUsers(myModification);
    if (users.size() != 1){
      return null;
    }
    return new UserRef(users.iterator().next(), myApiUrlBuilder);
  }

  @XmlElement(name = "files")
  public FileChanges getFileChanges() {
    return new FileChanges(myModification.getChanges());
  }
}