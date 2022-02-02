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

package jetbrains.buildServer.server.rest.data;

import com.google.common.collect.ComparisonChain;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.util.AggregatingItemHolder;
import jetbrains.buildServer.server.rest.data.util.ComparatorDuplicateChecker;
import jetbrains.buildServer.server.rest.data.util.DuplicateChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 22/01/2016
 */
@LocatorResource(value = LocatorName.BRANCH,
    extraDimensions = AbstractFinder.DIMENSION_ITEM,
    baseEntity = "Branch",
    examples = {
        "`build:<buildLocator>` — find branch with which the build found by `buildLocator` was started.",
        "`buildType:<buildTypeLocator>` — find branches of a build configuration found by `buildTypeLocator`."
    }
)
public class BranchFinder extends AbstractFinder<BranchData> {
  @LocatorDimension("name") protected static final String NAME = "name";
  @LocatorDimension(value = "default", format = LocatorDimensionDataType.BOOLEAN, notes = "Is default branch.")
  protected static final String DEFAULT = "default";
  protected static final String UNSPECIFIED = "unspecified";
  @LocatorDimension(value = "branched", format = LocatorDimensionDataType.BOOLEAN, notes = "Is feature branch.")
  protected static final String BRANCHED = "branched"; //rather use "branched" dimension in build locator
  @LocatorDimension(value = "build", format = LocatorName.BUILD, notes = "Build locator.")
  protected static final String BUILD = "build";
  @LocatorDimension(value = "buildType", format = LocatorName.BUILD_TYPE, notes = "Build type locator.")
  protected static final String BUILD_TYPE = "buildType";

  protected static final String BRANCH_GROUP = "group";
  protected static final String GROUP_INCLUDE = "includeGroups"; //this activates a temporary/experemental hack to include branch groups as fake branches in the result

  @LocatorDimension(value = "policy", allowableValues = "VCS_BRANCHES,ACTIVE_VCS_BRANCHES,HISTORY_BRANCHES,ACTIVE_HISTORY_BRANCHES,ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES,ALL_BRANCHES")
  protected static final String POLICY = "policy";
  protected static final String CHANGES_FROM_DEPENDENCIES = "changesFromDependencies";   //todo: revise naming

  private static final String ANY = "<any>";
  protected static final String COMPUTE_TIMESTAMPS = "computeLastActivity"; //experimental

  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ServiceLocator myServiceLocator;

  public BranchFinder(@NotNull final BuildTypeFinder buildTypeFinder, @NotNull final ServiceLocator serviceLocator) {
    super(NAME, DEFAULT, UNSPECIFIED, BUILD_TYPE, BUILD, POLICY, CHANGES_FROM_DEPENDENCIES, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME); //see also getBranchFilterDetails
    setHiddenDimensions(BRANCHED, COMPUTE_TIMESTAMPS, BRANCH_GROUP, GROUP_INCLUDE);
    myBuildTypeFinder = buildTypeFinder;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public static String getLocator(@NotNull final Branch branch) {
    if (branch.isDefaultBranch()) return getDefaultBranchLocator();
    return Locator.getStringLocator(NAME, ParameterCondition.getLocatorExactValueMatch(branch.getName())); //see also getBranchFilterDetails
  }

  public static String getDefaultBranchLocator() {
    return Locator.getStringLocator(DEFAULT, "true");
  }


  @Nullable
  @Contract("_, !null -> !null; !null,_ -> !null")
  public static String patchLocatorWithBuildType(final @Nullable String branchLocator, final @Nullable String buildTypeLocator) {
    return Locator.setDimensionIfNotPresent(branchLocator, BUILD_TYPE, buildTypeLocator);
  }

  @NotNull
  @Override
  public ItemFilter<BranchData> getFilter(@NotNull final Locator locator) {
    return getBranchFilterDetails(locator).filter;
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  @NotNull
  public BranchFilterDetails getBranchFilterDetailsWithoutLocatorCheck(@NotNull final String branchLocator) {
    return getBranchFilterDetails(createLocator(branchLocator, null));
  }

  public boolean isAnyBranch(@Nullable final String branchLocator) {
    if (branchLocator == null) return true;
    return getBranchFilterDetailsWithoutLocatorCheck(branchLocator).isAnyBranch();
  }

  @NotNull
  public BranchFilterDetails getBranchFilterDetails(@NotNull final String branchLocator) {
    final Locator locator = createLocator(branchLocator, null);
    final BranchFilterDetails branchFilterDetails = getBranchFilterDetails(locator);
    locator.checkLocatorFullyProcessed();
    return branchFilterDetails;
  }

  @NotNull
  private BranchFilterDetails getBranchFilterDetails(@NotNull final Locator locator) {
    final MultiCheckerFilter<BranchData> filter = new MultiCheckerFilter<BranchData>();
    final BranchFilterDetails result = new BranchFilterDetails();
    result.filter = filter;

    final String singleValue = locator.getSingleValue();
    if (singleValue != null) {
      if (!ANY.equals(singleValue)) {
//        result.branchName = singleValue;  do not set as it is ignore case and can match display/vcs branch
        filter.add(new FilterConditionChecker<BranchData>() {
          @Override
          public boolean isIncluded(@NotNull final BranchData item) {
            return singleValue.equalsIgnoreCase(item.getDisplayName()) || singleValue.equalsIgnoreCase(item.getName());
          }
        });
        return result;
      } else {
        result.matchesAllBranches = true;
        return result;
      }
    }

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null && !ANY.equals(nameDimension)) {
      final ValueCondition parameterCondition = ParameterCondition.createValueCondition(nameDimension);
      boolean compatibilityMode;
      if (nameDimension.equals(parameterCondition.getValue())){
        //single value
        compatibilityMode = true;
        if (parameterCondition.getIgnoreCase() == null) parameterCondition.setIgnoreCase(true); //pre-TeamCity-10 behavior
      } else{
        compatibilityMode = false;
      }
      String exactValue = parameterCondition.getConstantValueIfSimpleEqualsCondition();
      if (exactValue != null) result.branchName = exactValue;
      filter.add(new FilterConditionChecker<BranchData>() {
        @Override
        public boolean isIncluded(@NotNull final BranchData item) {
          if (compatibilityMode){
            return parameterCondition.matches(item.getDisplayName()) || parameterCondition.matches(item.getName()); //this basically matched both actual name and "<default>" for default branch
          }
          return parameterCondition.matches(item.getDisplayName());
        }
      });
    }

    final Boolean defaultDimension = locator.getSingleDimensionValueAsBoolean(DEFAULT);
    if (defaultDimension != null) {
      if (defaultDimension) {
        result.matchesDefaultBranchOrNotBranched = true;
      }
      filter.add(new FilterConditionChecker<BranchData>() {
        @Override
        public boolean isIncluded(@NotNull final BranchData item) {
          return FilterUtil.isIncludedByBooleanFilter(defaultDimension, item.isDefaultBranch());
        }
      });
    }

    final Boolean unspecifiedDimension = locator.getSingleDimensionValueAsBoolean(UNSPECIFIED);
    if (unspecifiedDimension != null) {
      result.unspecified = true;
      filter.add(new FilterConditionChecker<BranchData>() {
        @Override
        public boolean isIncluded(@NotNull final BranchData item) {
          return FilterUtil.isIncludedByBooleanFilter(unspecifiedDimension, Branch.UNSPECIFIED_BRANCH_NAME.equals(item.getName()));
        }
      });
    }

    final Boolean branchedDimension = locator.getSingleDimensionValueAsBoolean(BRANCHED);
    if (branchedDimension != null) {
      filter.add(new FilterConditionChecker<BranchData>() {
        @Override
        public boolean isIncluded(@NotNull final BranchData item) {
          return FilterUtil.isIncludedByBooleanFilter(branchedDimension, BranchData.isBranched(item));
        }
      });
    }

    result.matchesAllBranches = filter.getSubFiltersCount() == 0 &&
                                locator.getUnusedDimensions().isEmpty(); //e.g. "count" or "item" dimension is present
    return result;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final BranchData branch) {
    return getLocator(branch);
  }

  @NotNull
  @Override
  public ItemHolder<BranchData> getPrefilteredItems(@NotNull final Locator locator) {
    String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (!StringUtil.isEmpty(buildLocator)) {
      BuildPromotion build = myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItem(buildLocator);
      return getItemHolder(Collections.singleton(BranchData.fromBuild(build)));
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator == null) {
      throw new BadRequestException("No '" + BUILD_TYPE + "' dimension is present but it is required for searching branches. Locator: '" + locator.getStringRepresentation() + "'");
    }
    final List<SBuildType> buildTypes = myBuildTypeFinder.getBuildTypes(null, buildTypeLocator);

    AggregatingItemHolder<BranchData> result = new AggregatingItemHolder<>();

    final String groupsInclude = locator.getSingleDimensionValue(GROUP_INCLUDE);
    if (groupsInclude != null) {
      SUser user = validateAndgetGroupIncludeUser(groupsInclude);
      BranchGroupsService branchGroupsService = myServiceLocator.getSingletonService(BranchGroupsService.class);
      result.add(FinderDataBinding.getItemHolder(buildTypes.stream().
        flatMap(buildType -> branchGroupsService.getAvailableBranchGroups(new BranchGroupsProvider.Context((BuildTypeEx)buildType, user)).stream()).distinct().
                                                             map(branchGroup -> BranchData.fromBranchGroup(branchGroup))));
    }

    final String groupDimension = locator.getSingleDimensionValue(BRANCH_GROUP);
    if (groupDimension != null) {
      UserFinder userFinder = myServiceLocator.getSingletonService(UserFinder.class);
      BranchGroupsService branchGroupsService = myServiceLocator.getSingletonService(BranchGroupsService.class);
      Locator branchGroupLocator = new Locator(groupDimension, "id", "user", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
      String userLocator = branchGroupLocator.getSingleDimensionValue("user");
      SUser user = userLocator == null ? userFinder.getCurrentUser() : userFinder.getItem(userLocator);
      if (user == null) {
        throw new BadRequestException("Can only filter by branch group when the user is present");
      } else {
        userFinder.checkViewUserPermission(user);
      }

      String branchGroupId = branchGroupLocator.isSingleValue() ? branchGroupLocator.getSingleValue() : branchGroupLocator.getSingleDimensionValue("id");
      if (branchGroupId == null) {
        throw new BadRequestException(
          "Dimension '" + BRANCH_GROUP + "' does not specify 'id' subdimension. Example: " + branchGroupsService.getAvailableBranchGroups(new BranchGroupsProvider.Context(
            (BuildTypeEx)buildTypes.get(0), user)).stream().map(branchGroup -> branchGroup.getId()).collect(Collectors.joining(", ")));
      }
      branchGroupLocator.checkLocatorFullyProcessed();

      result.add(processor -> {
        try {
          buildTypes.forEach(buildType -> branchGroupsService.collectBranches(branchGroupId, new BranchGroupsProvider.Context((BuildTypeEx)buildType, user),
                                                                              item -> processor.processItem(BranchData.fromBranchEx(item, myServiceLocator, null, true))));
        } catch (IllegalStateException e) {
          throw new BadRequestException("Error retrieving branch groups: " + e.getMessage());
        }
      });
      return result;
    }

    BranchSearchOptions searchOptions = getBranchSearchOptions(locator);

    Accumulator resultAccumulator = new Accumulator();
    for (SBuildType buildType : buildTypes) {
      Boolean locatorComputeTimestamps = locator.getSingleDimensionValueAsBoolean(COMPUTE_TIMESTAMPS);
      resultAccumulator.addAll(getBranches(buildType, searchOptions, locatorComputeTimestamps != null ? locatorComputeTimestamps : TeamCityProperties.getBoolean("rest.beans.branch.defaultComputeTimestamp")));
    }

    result.add(getItemHolder(resultAccumulator.get()));
    return result;
  }

  @NotNull
  private SUser validateAndgetGroupIncludeUser(@NotNull final String groupsInclude) {
    Locator groupsIncludeLocator = new Locator(groupsInclude, "user", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    String groupsIncludeUserLocator = groupsIncludeLocator.getSingleDimensionValue("user");
    UserFinder userFinder = myServiceLocator.getSingletonService(UserFinder.class);
    final SUser user = groupsIncludeUserLocator == null ? userFinder.getCurrentUser() : userFinder.getItem(groupsIncludeUserLocator);
    if (user == null) throw new BadRequestException("Can only include branch groups when the user is present");
    if (groupsIncludeLocator.getSingleValue() != null) {
      if (!"true".equals(groupsIncludeLocator.getSingleValue())) {
        throw new BadRequestException("Only \"true\" locator is supported for \"" + GROUP_INCLUDE + "\" dimension");
      }
    }
    groupsIncludeLocator.checkLocatorFullyProcessed();
    return user;
  }

  private class BranchSearchOptions {
    @NotNull private final BranchesPolicy branchesPolicy;
    @Nullable private final Boolean includeBranchesFromDependencies;

    public BranchSearchOptions(@NotNull final BranchesPolicy branchesPolicy, @Nullable final Boolean includeBranchesFromDependencies) {
      this.branchesPolicy = branchesPolicy;
      this.includeBranchesFromDependencies = includeBranchesFromDependencies;
    }

    @NotNull
    public BranchesPolicy getBranchesPolicy() {
      return branchesPolicy;
    }

    public Boolean isIncludeBranchesFromDependencies() {
      return includeBranchesFromDependencies;
    }
  }

  @NotNull
  private BranchSearchOptions getBranchSearchOptions(final @NotNull Locator locator) {
    BranchesPolicy branchesPolicy = BranchesPolicy.ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES;
    final String policyDimension = locator.getSingleDimensionValue(POLICY);
    if (policyDimension != null) {
      try {
        branchesPolicy = BranchesPolicy.valueOf(policyDimension.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Invalid value '" + policyDimension + "' for '" + POLICY + "' dimension. Supported values are: " + Arrays.toString(BranchesPolicy.values()));
      }
    }

    Boolean changesFromDependencies;
    try {
      changesFromDependencies = locator.getSingleDimensionValueAsStrictBoolean(CHANGES_FROM_DEPENDENCIES, null);
    } catch (LocatorProcessException e) {
      throw new LocatorProcessException("Invalid '" + CHANGES_FROM_DEPENDENCIES + "' dimension", e);
    }
    return new BranchSearchOptions(branchesPolicy, changesFromDependencies);
  }

  private List<BranchData> getBranches(final @NotNull SBuildType buildType, @NotNull final BranchSearchOptions branchSearchOptions, final boolean computeTimestamps) {
    final BuildTypeEx buildTypeImpl = (BuildTypeEx)buildType; //TeamCity openAPI issue: cast
    BranchesPolicy mainPolicy = branchSearchOptions.getBranchesPolicy();
    List<BranchEx> branches = buildTypeImpl.getBranches(mainPolicy, branchSearchOptions.isIncludeBranchesFromDependencies(), computeTimestamps);
    // return branches.stream().map(b -> BranchData.fromBranchEx(b, myServiceLocator)).collect(Collectors.toList());
    // workaround for the TeamCity core performance issue of getting activity status per branch: it's ineffective, see implementation of BuildTypeBranchImpl.isActive()
    boolean disableActive = TeamCityProperties.getBoolean("rest.beans.branch.disableActive");
    boolean computeActive = TeamCityProperties.getBooleanOrTrue("rest.beans.branch.computeActive");
    BranchesPolicy activeBranchesPolicy;
    switch (mainPolicy) {
      case ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES:
      case ACTIVE_VCS_BRANCHES:
      case ACTIVE_HISTORY_BRANCHES:
        //al branches are active
        return branches.stream().map(b -> BranchData.fromBranchEx(b, myServiceLocator, computeActive ? true : null, disableActive)).collect(Collectors.toList());
      case HISTORY_BRANCHES:
        activeBranchesPolicy = BranchesPolicy.ACTIVE_HISTORY_BRANCHES;
        break;
      case VCS_BRANCHES:
        activeBranchesPolicy = BranchesPolicy.ACTIVE_VCS_BRANCHES;
        break;
      case ALL_BRANCHES:
      default:
        activeBranchesPolicy = BranchesPolicy.ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES;
    }
    Set<String> activeBranches = computeActive ? buildTypeImpl.getBranches(activeBranchesPolicy, branchSearchOptions.isIncludeBranchesFromDependencies(), false)
                                                              .stream().map(b -> b.getName()).collect(Collectors.toSet())
                                               : null;
    return branches.stream().map(b -> BranchData.fromBranchEx(b, myServiceLocator, computeActive ? activeBranches.contains(b.getName()) : null, disableActive))
                   .collect(Collectors.toList());
  }

  @NotNull
  public PagedSearchResult<BranchData> getItems(@NotNull SBuildType buildType, @Nullable final String locatorText) {
    String baseLocator = locatorText;
    if (locatorText != null) {
      Locator locator = new Locator(locatorText);
      if (locator.isSingleValue()) {
        if (!locator.isHelpRequested()) {
          baseLocator = Locator.getStringLocator(NAME, locatorText);
        } else {
          baseLocator = Locator.getStringLocator(Locator.HELP_DIMENSION, "");
        }
      }
    }
    return getItems(Locator.setDimensionIfNotPresent(baseLocator, BUILD_TYPE, myBuildTypeFinder.getCanonicalLocator(new BuildTypeOrTemplate(buildType))));
  }

  @NotNull
  public PagedSearchResult<BranchData> getItemsIfValidBranchListLocator(@Nullable String buildTypesLocator, @Nullable final String locatorText) {
    final Locator locator = createLocator(locatorText, null); //using createLocator here to make sure due error on wrong locator will be generated
    if (buildTypesLocator != null &&
        !locator.isSingleValue() &&
        (locator.getSingleDimensionValue(POLICY) != null
         || locator.getSingleDimensionValue(CHANGES_FROM_DEPENDENCIES) != null
         || locator.getSingleDimensionValue(BRANCH_GROUP) != null
         || locator.getSingleDimensionValue(GROUP_INCLUDE) != null
        )
    ) {
      locator.setDimensionIfNotPresent(BUILD_TYPE, buildTypesLocator);
    }
    return getItems(locator.getStringRepresentation());
  }

  @Override
  @NotNull
  public DuplicateChecker<BranchData> createDuplicateChecker() {
    return new ComparatorDuplicateChecker<>((branchData1, branchData2) -> {
      return ComparisonChain.start()
                            .compareTrueFirst(branchData1.isDefaultBranch(), branchData2.isDefaultBranch())
                            .compare(branchData1.getName(), branchData2.getName())
                            .result();
    });
  }

  static private class Accumulator {
    //de-duplicate by name, ordering is not important here
    private final Map<String, BranchData> myMap = new HashMap<String, BranchData>();

    void addAll(@NotNull final List<BranchData> buildTypeBranches) {
      for (BranchData branch : buildTypeBranches) {
        //assuming that branch.isDefaultBranch() means Branch.DEFAULT_BRANCH_NAME.equals(name)

        BranchData previousData = myMap.get(branch.getName());
        if (previousData == null) {
          myMap.put(branch.getName(), branch);
        } else {
          myMap.put(branch.getName(), BranchData.mergeSameNamed(branch, previousData));
        }
      }
    }

    @NotNull
    Iterable<BranchData> get() {
      ArrayList<BranchData> result = new ArrayList<>(myMap.values());
      result.sort((o1, o2) -> {
            return ComparisonChain.start()
                                  .compareTrueFirst(Branch.DEFAULT_BRANCH_NAME.equals(o1.getName()), Branch.DEFAULT_BRANCH_NAME.equals(o2.getName()))
                                  .compareFalseFirst(Branch.UNSPECIFIED_BRANCH_NAME.equals(o1.getName()), Branch.UNSPECIFIED_BRANCH_NAME.equals(o2.getName()))
                                  .compare(o1.getName(), o2.getName())
                                  .result();
          });
      return result;
    }
  }

  public static class BranchFilterDetails {
    private ItemFilter<BranchData> filter;
    private String branchName; // name or display name of the branch, if set. Even if set, there might be other conditions set
    private boolean matchesAllBranches = false;
    private boolean matchesDefaultBranchOrNotBranched = false;
    private boolean unspecified = false;

    public boolean isIncluded(@NotNull final BuildPromotion promotion) {
      if (matchesAllBranches) {
        return true;
      }
      return filter.isIncluded(BranchData.fromBuild(promotion));
    }

    public boolean isAnyBranch() {
      return matchesAllBranches;
    }

    @Nullable
    public String getBranchName() {
      return branchName;
    }

    public boolean isDefaultBranchOrNotBranched() {
      return matchesDefaultBranchOrNotBranched;
    }

    public boolean isUnspecified() {
      return unspecified;
    }
  }
}