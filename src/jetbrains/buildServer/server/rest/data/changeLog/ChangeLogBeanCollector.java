/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.changeLog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.controllers.BranchBean;
import jetbrains.buildServer.controllers.BranchBeanFactory;
import jetbrains.buildServer.controllers.BuildTypeBranchBean;
import jetbrains.buildServer.controllers.buildType.BuildTypeChangeLogBeanProvider;
import jetbrains.buildServer.controllers.buildType.BuildTypePendingChangeLogBeanProvider;
import jetbrains.buildServer.controllers.buildType.tabs.ChangeLogBean;
import jetbrains.buildServer.controllers.buildType.tabs.ChangeLogFilter;
import jetbrains.buildServer.controllers.buildType.tabs.ChangesListFilter;
import jetbrains.buildServer.controllers.project.ProjectChangeLogBeanProvider;
import jetbrains.buildServer.controllers.viewLog.BuildChangeLogBeanProvider;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.data.ValueCondition;
import jetbrains.buildServer.server.rest.data.finder.impl.BranchFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.data.util.LocatorUtil;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;


/**
 * This is the facade which maps locator to internal API realated to ChangeLogBean.
 * Some dimensions are partially supported and BadRequestException will be thrown in case of unsupported dimension values.
 * <p>
 * This an attempt to reuse already existing graph and change log. It is important to reuse those together due to interconnected pagination
 * and is undesirable to make separate requests for a change log and a graph. Ideally, graph information should be included into
 * change log rows and this class may then implement Finder<ChangeLogRow>, but as of now the limitation is graph rendering library used on frontend.
 */
@JerseyContextSingleton
@Component
public class ChangeLogBeanCollector {
  @LocatorDimension(value = "build", hidden = true)
  public static final String BUILD = "build";

  @LocatorDimension(value = "buildType", hidden = true)
  public static final String BUILD_TYPE = "buildType";

  @LocatorDimension(value = "project", hidden = true)
  public static final String PROJECT = "project";

  @LocatorDimension(value = "pending", hidden = true)
  public static final String PENDING = "pending";

  @LocatorDimension(value = "branch", hidden = true)
  public static final String BRANCH = "branch";

  @LocatorDimension(value = "changesFromDependencies", hidden = true)
  public static final String CHANGES_FROM_DEPS = "changesFromDependencies";

  @LocatorDimension(value = "path", hidden = true)
  public static final String PATH = "path";

  @LocatorDimension(value = "includeBuilds", hidden = true)
  public static final String INCLUDE_BUILDS = "includeBuilds";

  @LocatorDimension(value = "comment", hidden = true)
  public static final String COMMENT = "comment";

  @LocatorDimension(value = "revision", hidden = true)
  public static final String REVISION = "revision";

  @LocatorDimension(value = "page", hidden = true)
  public static final String PAGE = "page";

  @LocatorDimension(value = "pageSize", hidden = true)
  public static final String PAGE_SIZE = "pageSize";

  @LocatorDimension(value = "user", hidden = true)
  public static final String USER = "user";

  @LocatorDimension(value = "vcsUsername", hidden = true)
  public static final String VCS_USERNAME = "vcsUsername";

  @LocatorDimension(value = "fromBuildNumber", hidden = true)
  public static final String FROM_BUILD_NUMBER = "fromBuildNumber";

  @LocatorDimension(value = "toBuildNumber", hidden = true)
  public static final String TO_BUILD_NUMBER = "toBuildNumber";

  private final BuildPromotionFinder myBuildPromotionFinder;
  private final BuildChangeLogBeanProvider myBuildChangeLogBeanProvider;
  private final BuildTypeChangeLogBeanProvider myBuildTypeChangeLogBeanProvider;
  private final BuildTypePendingChangeLogBeanProvider myPendingChangeLogBeanProvider;
  private final ProjectChangeLogBeanProvider myProjectChangeLogBeanProvider;
  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final BranchBeanFactory myBranchBeanFactory;
  private final BranchFinder myBranchFinder;
  private final SecurityContext mySecurityContext;

  public ChangeLogBeanCollector(@NotNull BuildPromotionFinder buildPromotionFinder,
                                @NotNull ProjectFinder projectFinder,
                                @NotNull BuildTypeFinder buildTypeFinder,
                                @NotNull BranchFinder branchFinder,
                                @NotNull BuildChangeLogBeanProvider buildChangeLogBeanProvider,
                                @NotNull BuildTypeChangeLogBeanProvider buildTypeChangeLogBeanProvider,
                                @NotNull BuildTypePendingChangeLogBeanProvider pendingChangeLogBeanProvider,
                                @NotNull ProjectChangeLogBeanProvider projectChangeLogBeanProvider,
                                @NotNull BranchBeanFactory branchBeanFactory,
                                @NotNull SecurityContext securityContext) {
    myBranchBeanFactory = branchBeanFactory;
    myBuildPromotionFinder = buildPromotionFinder;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myBranchFinder = branchFinder;
    myBuildChangeLogBeanProvider = buildChangeLogBeanProvider;
    myBuildTypeChangeLogBeanProvider = buildTypeChangeLogBeanProvider;
    myPendingChangeLogBeanProvider = pendingChangeLogBeanProvider;
    myProjectChangeLogBeanProvider = projectChangeLogBeanProvider;
    mySecurityContext = securityContext;
  }

  @Nullable
  public ChangeLogBean getItem(@NotNull Locator locator) {
    locator.addHiddenDimensions(BUILD, BUILD_TYPE, PROJECT, PENDING, BRANCH, CHANGES_FROM_DEPS, PATH, INCLUDE_BUILDS, COMMENT, PAGE, PAGE_SIZE, USER, VCS_USERNAME, REVISION);
    locator.processHelpRequest();

    if (!LocatorUtil.exactlyOneIsPresentAndUnused(locator, BUILD, BUILD_TYPE, PROJECT)) {
      throw new BadRequestException("Exactly one of dimensions [" + String.join(",", new String[]{BUILD, BUILD_TYPE, PROJECT}) + "] must be set.");
    }

    if (locator.isAnyPresent(USER) && locator.isAnyPresent(VCS_USERNAME)) {
      throw new BadRequestException("Only one of dimensions [" + String.join(",", new String[]{USER, VCS_USERNAME}) + "] may be set.");
    }

    SUser user = (SUser)mySecurityContext.getAuthorityHolder().getAssociatedUser();
    if (user == null) {
      throw new BadRequestException("Can't calculate change log for a non-user authority.");
    }

    if (locator.isUnused(BUILD)) {
      return getItemForBuild(locator, user);
    }

    if (locator.isUnused(BUILD_TYPE)) {
      return getItemForBuildType(locator, user);
    }

    if (locator.isUnused(PROJECT)) {
      return getItemForProject(locator, user);
    }

    throw new OperationException("Internal error, can't get change log by given locator. Please contact TeamCity developers.");
  }

  @NotNull
  private ChangeLogBean getItemForProject(@NotNull Locator locator, @NotNull SUser user) {
    SProject project = myProjectFinder.getItem(locator.getSingleDimensionValue(PROJECT));

    BranchBean branchBean = getProjectBranchBean(locator.getSingleDimensionValue(BRANCH));
    ChangeLogFilter filter = new ChangeLogFilter(user);
    filter.setShowGraph(true);
    fillFilterFromLocator(filter, locator);
    locator.checkLocatorFullyProcessed();

    return myProjectChangeLogBeanProvider.createBean(project, branchBean, filter, false);
  }

  @Nullable
  private ChangeLogBean getItemForBuildType(@NotNull Locator locator, @NotNull SUser user) {
    SBuildType buildType = myBuildTypeFinder.getBuildTypeIfNotNull(locator.getSingleDimensionValue(BUILD_TYPE));
    if (buildType == null) {
      return null;
    }
    BuildTypeBranchBean branchBean = getBuildTypeBranchBean(locator.getSingleDimensionValue(BRANCH), buildType);

    boolean pending = locator.getSingleDimensionValueAsStrictBoolean(PENDING, false);
    if (pending) {
      ChangesListFilter filter = new ChangesListFilter(user);
      filter.setShowChangesFromDependencies(locator.getSingleDimensionValueAsStrictBoolean(CHANGES_FROM_DEPS, buildType.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES)));
      fillFilterFromLocator(filter, locator);
      locator.checkLocatorFullyProcessed();

      return myPendingChangeLogBeanProvider.createPendingChangesBean(buildType, branchBean, filter, false);
    }

    ChangeLogFilter filter = new ChangeLogFilter(user);
    filter.setShowChangesFromDependencies(locator.getSingleDimensionValueAsStrictBoolean(CHANGES_FROM_DEPS, buildType.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES)));
    fillFilterFromLocator(filter, locator);
    return myBuildTypeChangeLogBeanProvider.createChangeLogBean(buildType, branchBean, filter, false);
  }

  private static void fillFilterFromLocator(@NotNull ChangeLogFilter filter, @NotNull Locator locator) {
    filter.setShowGraph(true);
    filter.setBuildsActiveDays(-1);
    if (locator.isAnyPresent(INCLUDE_BUILDS)) {
      filter.setShowBuilds(locator.getSingleDimensionValueAsStrictBoolean(INCLUDE_BUILDS, false));
    }
    if (locator.isAnyPresent(COMMENT)) {
      ValueCondition condition = ParameterCondition.createValueCondition(locator.getSingleDimensionValue(COMMENT));
      if (condition != null) {
        filter.setCommentRequirement(convertConditionToRequirement(condition));
      }
    }
    if (locator.isAnyPresent(PATH)) {
      ValueCondition condition = ParameterCondition.createValueCondition(locator.getSingleDimensionValue(PATH));
      if (condition != null) {
        filter.setPathRequirement(convertConditionToRequirement(condition));
      }
    }
    if (locator.isAnyPresent(REVISION)) {
      ValueCondition condition = ParameterCondition.createValueCondition(locator.getSingleDimensionValue(REVISION));
      if (condition != null) {
        filter.setRevisionRequirement(convertConditionToRequirement(condition));
      }
    }
    // Set from and to before setting page as otherwise page will be overwritten
    if (locator.isAnyPresent(FROM_BUILD_NUMBER)) {
      filter.setFrom(locator.getSingleDimensionValue(FROM_BUILD_NUMBER));
    }
    if (locator.isAnyPresent(TO_BUILD_NUMBER)) {
      filter.setTo(locator.getSingleDimensionValue(TO_BUILD_NUMBER));
    }
    if (locator.isAnyPresent(PAGE)) {
      Long page = locator.getSingleDimensionValueAsLong(PAGE);
      filter.setPage(page == null ? 0 : page.intValue());
    }
    if (locator.isAnyPresent(PAGE_SIZE)) {
      Long pageSize = locator.getSingleDimensionValueAsLong(PAGE_SIZE);
      filter.setRecordsPerPage(pageSize == null ? 0 : pageSize.intValue());
    }
    if (locator.isAnyPresent(VCS_USERNAME)) {
      filter.setUserId("vcs:" + locator.getSingleDimensionValue(VCS_USERNAME));
    }
    if (locator.isAnyPresent(USER)) {
      Locator userLocator = Locator.locator(locator.lookupSingleDimensionValue(USER));
      assert userLocator != null; // make IDEA happy
      if (!userLocator.isAnyPresent("id") || userLocator.getUnusedDimensions().size() > 1) {
        throw new BadRequestException("User lookup is supported by id only.");
      }
      // at this point user dimension looks like "id:XXX" and that is exactly how filter wants it, so just pass it directly
      filter.setUserId(locator.getSingleDimensionValue(USER));
    }
  }

  @NotNull
  private static ChangeLogFilter.Requirement convertConditionToRequirement(@NotNull ValueCondition condition) {
    if (StringUtil.isEmpty(condition.getValue())) {
      return ChangeLogFilter.Requirement.ANY;
    }
    return new ChangeLogFilter.Requirement(condition.getValue(), condition.getRequirementType(), condition.getActualIgnoreCase());
  }

  @NotNull
  private BuildTypeBranchBean getBuildTypeBranchBean(@Nullable final String branchLocatorDef, @NotNull final SBuildType buildType) {
    Locator branchLocator = Locator.createPotentiallyEmptyLocator(branchLocatorDef);

    Set<String> supportedBranchDimensions = new HashSet<>(Arrays.asList(
      BranchFinder.NAME,
      BranchFinder.DEFAULT,
      BranchFinder.POLICY,
      BranchFinder.BRANCH_GROUP,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME
    ));

    if (!supportedBranchDimensions.containsAll(branchLocator.getUnusedDimensions())) {
      throw new BadRequestException(String.format("Supported sub-dimensions for dimension '%s' are: [%s]", BRANCH, String.join(",", supportedBranchDimensions)));
    }

    if (branchLocatorDef == null) {
      return BuildTypeBranchBean.allBranches(buildType);
    }

    // check unsupported cases
    {
      Locator copy = new Locator(branchLocator);
      if(copy.isSingleValue()) {
        throw new BadRequestException(String.format("Single value branch locator is unsupported. There should be a name subdimension in a branch locator, which must be in a following format: branch:(name:(matchType:<MATCH_TYPE>,value:<VALUE>)) ."));
      }
      if (copy.isAnyPresent(BranchFinder.NAME)) {
        Locator name = Locator.createPotentiallyEmptyLocator(copy.getSingleDimensionValue(BranchFinder.NAME));
        if (!name.isAnyPresent("matchType")) {
          throw new BadRequestException(String.format("Name subdimension in a branch locator must be in a following format: name:(matchType:<MATCH_TYPE>,value:<VALUE>)."));
        }
      }
    }

    BranchFinder.BranchFilterDetails branchFilterDetails = myBranchFinder.getBranchFilterDetailsWithoutLocatorCheck(branchLocatorDef);
    if (branchFilterDetails.isAnyBranch()) {
      return BuildTypeBranchBean.allBranches(buildType);
    }
    if (branchFilterDetails.isDefaultBranchOrNotBranched()) {
      return myBranchBeanFactory.createBranchBean(Branch.DEFAULT_BRANCH_NAME, buildType);
    }

    String groupDimension = branchLocator.getSingleDimensionValue(BranchFinder.BRANCH_GROUP);
    if (groupDimension != null) {
      BranchFinder.BranchGroupFilterDetails groupFilterDetails = myBranchFinder.getBranchGroupFilterDetails(groupDimension, buildType);

      return myBranchBeanFactory.createBranchBean(String.format("__%s__", groupFilterDetails.getBranchGroupId()), buildType);
    }

    return myBranchBeanFactory.createBranchBean(branchFilterDetails.getBranchName(), buildType);
  }

  @NotNull
  private BranchBean getProjectBranchBean(@Nullable final String branchLocatorDef) {
    Locator branchLocator = Locator.createPotentiallyEmptyLocator(branchLocatorDef);

    Set<String> supportedBranchDimensions = new HashSet<>(Arrays.asList(
      BranchFinder.NAME,
      BranchFinder.DEFAULT,
      BranchFinder.POLICY,
      BranchFinder.BRANCH_GROUP,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME
    ));

    if (!supportedBranchDimensions.containsAll(branchLocator.getUnusedDimensions())) {
      throw new BadRequestException(String.format("Supported sub-dimensions for dimension '%s' are: [%s]", BRANCH, String.join(",", supportedBranchDimensions)));
    }

    if (branchLocatorDef == null) {
      // return default branch bean if no branch is given
      return BuildTypeBranchBean.allBranches();
    }

    BranchFinder.BranchFilterDetails branchFilterDetails = myBranchFinder.getBranchFilterDetailsWithoutLocatorCheck(branchLocatorDef);
    if (branchFilterDetails.isAnyBranch()) {
      return BuildTypeBranchBean.allBranches();
    }
    if (branchFilterDetails.isDefaultBranchOrNotBranched()) {
      return myBranchBeanFactory.createBranchBean(Branch.DEFAULT_BRANCH_NAME);
    }

    String groupDimension = branchLocator.getSingleDimensionValue(BranchFinder.BRANCH_GROUP);
    if (groupDimension != null) {
      BranchFinder.BranchGroupFilterDetails groupFilterDetails = myBranchFinder.getBranchGroupFilterDetails(groupDimension, null);

      return myBranchBeanFactory.createBranchBean(String.format("__%s__", groupFilterDetails.getBranchGroupId()));
    }

    return myBranchBeanFactory.createBranchBean(branchFilterDetails.getBranchName());
  }

  @NotNull
  private ChangeLogBean getItemForBuild(@NotNull Locator locator, @NotNull SUser user) {
    BuildPromotion promotion = myBuildPromotionFinder.getItem(locator.getSingleDimensionValue(BUILD));
    SBuildType bt = promotion.getBuildType();

    ChangesListFilter filter = new ChangesListFilter(user);
    filter.setShowChangesFromDependencies(locator.getSingleDimensionValueAsStrictBoolean(CHANGES_FROM_DEPS, bt != null && bt.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES)));
    filter.setShowGraph(true); // it is important to force graph
    fillFilterFromLocator(filter, locator);
    locator.checkLocatorFullyProcessed();
    return myBuildChangeLogBeanProvider.createChangeLogBean(promotion, filter, false);
  }
}
