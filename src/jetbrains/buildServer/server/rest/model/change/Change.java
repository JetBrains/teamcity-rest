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

package jetbrains.buildServer.server.rest.model.change;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.ChangeFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Items;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.RelationType;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.impl.VcsModificationEx;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "change")
@XmlType(name = "change", propOrder = {
  "id",
  "version",
  "internalVersion",
  "username",
  "date",
  "registrationDate",
  "personal",
  "href",
  "webUrl",
  "comment",
  "user",
  "fileChanges",
  "vcsRootInstance",
  "parentChanges",
  "parentRevisions",
  "attributes",
  "storesProjectSettings"
})
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a VCS change (commit)." + 
"\n\nRelated Help article: [Change](https://www.jetbrains.com/help/teamcity/change.html)"))
public class Change {
  protected SVcsModification myModification;
  protected ApiUrlBuilder myApiUrlBuilder;
  protected WebLinks myWebLinks;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;
  /**
   * These are used only when posting a link to the change
   */
  private String submittedLocator;
  private Long submittedId;
  private Boolean submittedPersonal;

  @Used("javax.xml")
  public Change() {
  }

  public Change(SVcsModification modification, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    this.myModification = modification;
    myFields = fields;
    myBeanContext = beanContext;
    myApiUrlBuilder = myBeanContext.getApiUrlBuilder();
    myWebLinks = myBeanContext.getSingletonService(WebLinks.class);
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
      return escapeNonPrintedCharacters(vcsModification.getDescription());
    } else if ("registrationDate".equals(field)) { //not documented
      return Util.formatTime(vcsModification.getRegistrationDate());
    } else if ("versionControlName".equals(field)) { //not documented
      return vcsModification.getVersionControlName();
    } else if ("internalVersion".equals(field)) { //not documented
      return vcsModification.getVersion();
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported fields are: 'id', 'version', 'username', 'date', 'personal', 'comment'.");
  }

  private static boolean isVersionedSettings(@NotNull final SVcsModification change) {
    final boolean[] isVersionedSettingsChange = new boolean[]{false};
    ((VcsModificationEx)change).consumeRelations((s, relationType) -> {
      if (relationType == RelationType.SETTINGS_AFFECT_BUILDS) {
        isVersionedSettingsChange[0] = true;
      }
    });
    return isVersionedSettingsChange[0];
  }

  @XmlAttribute
  public Long getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id"), myModification.getId());
  }

  public void setId(Long id) {
    submittedId = id;
  }

  @XmlAttribute
  public String getVersion() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("version"), myModification.getDisplayVersion());
  }

  @Used
  @XmlAttribute
  public String getInternalVersion() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("internalVersion", false, false), () -> myModification.getVersion());
  }

  @XmlAttribute
  public Boolean getPersonal() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("personal"), myModification.isPersonal());
  }

  public void setPersonal(Boolean value) {
    submittedPersonal = value;
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href"), myApiUrlBuilder.getHref(myModification));
  }

  @XmlAttribute
  public String getWebUrl() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("webUrl"), myWebLinks.getChangeUrl(myModification.getId(), myModification.isPersonal()));
  }

  @XmlAttribute
  public String getUsername() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("username"), myModification.getUserName());
  }

  @XmlAttribute
  public String getDate() {
    final Date vcsDate = myModification.getVcsDate();
    return ValueWithDefault.decideDefault(myFields.isIncluded("date"), Util.formatTime(vcsDate));
  }

  @Used
  @XmlAttribute
  public String getRegistrationDate() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("registrationDate", false, false), () -> Util.formatTime(myModification.getRegistrationDate()));
  }

  @XmlElement
  public String getComment() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("comment", false), () -> escapeNonPrintedCharacters(myModification.getDescription()));
  }

  @NotNull
  private static String escapeNonPrintedCharacters(final String str) {
    // Super-quick temporary workaround for TW-65005
    return StringUtil.replaceInvalidXmlChars(str);
  }

  @XmlElement(name = "user")
  public User getUser() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("user", false), () -> {
      final Collection<SUser> users = myModification.getCommitters();
      if (users.size() != 1) {
        return null;
      }
      return new User(users.iterator().next(), myFields.getNestedField("user"), myBeanContext);
    });
  }

  @XmlElement(name = "files")
  public FileChanges getFileChanges() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("files", false), () -> new FileChanges(myModification.getChanges(), myFields.getNestedField("files")));
  }

  @XmlElement(name = "vcsRootInstance")
  public VcsRootInstance getVcsRootInstance() {
    return myModification.isPersonal()
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("vcsRootInstance", false),
                                            new VcsRootInstance(myModification.getVcsRoot(), myFields.getNestedField("vcsRootInstance"), myBeanContext));
  }

  @Used
  @XmlElement(name = "parentChanges")
  public Changes getParentChanges() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("parentChanges", false, false),
                                          () -> new Changes(new ArrayList<>(myModification.getParentModifications()), null, myFields.getNestedField("parentChanges"),
                                                            myBeanContext));
  }

  @Used
  @XmlElement(name = "parentRevisions")
  public Items getParentRevisions() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("parentRevisions", false, false), () -> new Items(myModification.getParentRevisions()));
  }

  /**
   * experimental use only
   */
  @XmlElement(name = "attributes")
  public Properties getAttributes() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("attributes", false, false),
                                          () -> new Properties(myModification.getAttributes(), null, myFields.getNestedField("attributes"), myBeanContext));
  }

  /**
   * experimental
   */
  @Used
  @XmlAttribute(name = "storesProjectSettings")
  public Boolean getStoresProjectSettings() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("storesProjectSettings", false, false),
                                          () -> isVersionedSettings(myModification));
  }

  @XmlAttribute
  public String getLocator() {
    return null;
  }

  public void setLocator(final String locator) {
    submittedLocator = locator;
  }

  @NotNull
  public SVcsModification getChangeFromPosted(final ChangeFinder changeFinder) {
    String locatorText;
    if (submittedId != null) {
      if (submittedLocator != null) {
        throw new BadRequestException("Both 'locator' and 'id' attributes are specified. Only one should be present.");
      }
      final Locator locator = Locator.createEmptyLocator().setDimension(ChangeFinder.DIMENSION_ID, String.valueOf(submittedId));
      if (submittedPersonal != null && submittedPersonal) {
        locator.setDimension(ChangeFinder.PERSONAL, "true");
      }
      locatorText = locator.getStringRepresentation();
    } else {
      if (submittedLocator == null) {
        throw new BadRequestException("No change specified. Either 'id' or 'locator' attribute should be present.");
      }
      locatorText = submittedLocator;
    }

    return changeFinder.getItem(locatorText);
  }

}