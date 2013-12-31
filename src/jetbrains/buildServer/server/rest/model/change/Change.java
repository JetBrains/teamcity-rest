/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsModification;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "change")
@XmlType(name= "change", propOrder = {"username", "date",
  "comment", "user", "fileChanges", "vcsRootInstance"})
public class Change extends ChangeRef {

//  @Autowired VcsManager myVcsManager;
//  @Autowired BeanFactory myFactory;

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
    return Util.formatTime(vcsDate);
  }

  @XmlElement
  public String getComment() {
    return myModification.getDescription();
  }

  @XmlElement(name = "user")
  public UserRef getUser() {
    final Collection<SUser> users = myModification.getCommitters();
    if (users.size() != 1){
      return null;
    }
    return new UserRef(users.iterator().next(), myApiUrlBuilder);
  }

  @XmlElement(name = "files")
  public FileChanges getFileChanges() {
    return new FileChanges(myModification.getChanges());
  }

  @XmlElement(name = "vcsRootInstance")
  public VcsRootInstanceRef getVcsRootInstance() {
    return myModification.isPersonal() ? null : new VcsRootInstanceRef(myModification.getVcsRoot(), myApiUrlBuilder);
  }

  public static String getFieldValue(final SVcsModification vcsModification, final String field) {
    if ("id".equals(field)) {
      return String.valueOf(vcsModification.getId());
    } else if ("version".equals(field)) {
      return vcsModification.getDisplayVersion();
    } else if ("username".equals(field)) {
      return vcsModification.getUserName();
    } else if ("date".equals(field)) {
      return Util.formatTime(vcsModification.getVcsDate());
    } else if ("personal".equals(field)) {
      return String.valueOf(vcsModification.isPersonal());
    } else if ("comment".equals(field)) {
      return vcsModification.getDescription();
    } else if ("registrationDate".equals(field)) { //not documented
      return Util.formatTime(vcsModification.getRegistrationDate());
    } else if ("versionControlName".equals(field)) { //not documented
      return vcsModification.getVersionControlName();
    } else if ("internalVersion".equals(field)) { //not documented
      return vcsModification.getVersion();
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported fields are: 'id', 'version', 'username', 'date', 'personal', 'comment'.");
  }
}