/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "vcs-root-entry")
@XmlType(name = "vcs-root-entry", propOrder = {"id",
  "vcsRoot", "checkoutRules"})
public class VcsRootEntry {
  public static final String CHECKOUT_RULES = "checkout-rules";
  @XmlAttribute(name = "id")
  public String id;

  @XmlElement(name = "vcs-root")
  public VcsRoot vcsRoot;
  @XmlElement(name = CHECKOUT_RULES)
  public String checkoutRules;

  public VcsRootEntry() {
  }

  public VcsRootEntry(final @NotNull SVcsRoot vcsRootParam, @NotNull final BuildTypeOrTemplate buildType, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id", true, true), vcsRootParam.getExternalId());
    vcsRoot = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("vcs-root", true, true), new VcsRoot(vcsRootParam, fields.getNestedField("vcs-root"), beanContext));
    checkoutRules =  ValueWithDefault.decideIncludeByDefault(fields.isIncluded(CHECKOUT_RULES, true, true), buildType.get().getCheckoutRules(vcsRootParam).getAsString());
  }
}

