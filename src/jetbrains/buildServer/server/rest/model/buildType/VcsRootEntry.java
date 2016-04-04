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
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.InvalidVcsRootScopeException;
import jetbrains.buildServer.vcs.CheckoutRules;
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

  //see also PropEntityEdit
  @NotNull
  public SVcsRoot addTo(@NotNull final BuildTypeSettings buildType, @NotNull final VcsRootFinder vcsRootFinder) {
    VcsRootEntries.Storage original = new VcsRootEntries.Storage(buildType);
    try {
      return addToInternal(buildType, vcsRootFinder);    } catch (Exception e) {
      //restore original settings
      original.apply(buildType);
      throw new BadRequestException("Error replacing items", e);
    }
  }

  @NotNull
  public SVcsRoot addToInternal(@NotNull final BuildTypeSettings buildType, @NotNull final VcsRootFinder vcsRootFinder) {
    if (vcsRoot == null){
      throw new BadRequestException("Element vcs-root should be specified.");
    }
    final SVcsRoot result = vcsRoot.getVcsRoot(vcsRootFinder);

    try {
      buildType.addVcsRoot(result);
    } catch (InvalidVcsRootScopeException e) {
      throw new BadRequestException("Could not attach VCS root with id '" + result.getExternalId() + "' because of scope issues. Error: " + e.getMessage());
    }
    buildType.setCheckoutRules(result, new CheckoutRules(checkoutRules != null ? checkoutRules : ""));
    return result;
  }

  @NotNull
  public SVcsRoot replaceIn(@NotNull final BuildTypeSettings buildType, @NotNull final SVcsRoot entityToReplace, @NotNull final VcsRootFinder vcsRootFinder){
    if (!buildType.containsVcsRoot(entityToReplace.getId())) {
      throw new NotFoundException("VCS root with id '" + entityToReplace.getExternalId() + "' is not attached to the build type.");
    }
    if (vcsRoot == null){
      throw new BadRequestException("No VCS root is specified in the entry description.");
    }
    buildType.removeVcsRoot(entityToReplace);
    return addToInternal(buildType, vcsRootFinder);
  }
}

