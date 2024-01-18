/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootEntry;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@SuppressWarnings("PublicField")
@XmlType(name = "revision")
@ModelDescription(
    value = "Represents a revision related to a VCS change.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/revision.html",
    externalArticleName = "Revision"
)
public class Revision {
  @XmlAttribute(name = "version")
  public String displayRevision;
  @XmlAttribute(name = "internalVersion")
  public String internalRevision;
  @XmlAttribute(name = "vcsBranchName")
  public String vcsBranchName;

  @XmlElement(name = "vcs-root-instance")
  public VcsRootInstance vcsRoot;

  /**
   * Experimental, https://youtrack.jetbrains.com/issue/TW-42653
   */
  @XmlElement(name = VcsRootEntry.CHECKOUT_RULES)
  public String checkoutRules;

  public Revision() {
  }

  public Revision(@NotNull final BuildRevision revision, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    displayRevision = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("version"), revision.getRevisionDisplayName());
    final boolean internalMode = TeamCityProperties.getBoolean("rest.internalMode");
    internalRevision = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("internalVersion", internalMode, internalMode), revision.getRevision());

    checkoutRules = ValueWithDefault.decideDefault(fields.isIncluded(VcsRootEntry.CHECKOUT_RULES, false, false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        return revision.getCheckoutRules().getAsString();
      }
    });

    vcsBranchName = ValueWithDefault.decideDefault(fields.isIncluded("vcsBranchName"), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        return revision.getRepositoryVersion().getVcsBranch();
      }
    });

    vcsRoot = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("vcs-root-instance"),
                                                      new VcsRootInstance(revision.getRoot(), fields.getNestedField("vcs-root-instance"), beanContext));
  }
}