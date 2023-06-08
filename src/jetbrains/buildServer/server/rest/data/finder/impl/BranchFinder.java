/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import com.google.common.collect.ComparisonChain;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.ExistenceAwareFinder;
import jetbrains.buildServer.server.rest.data.util.*;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * @author Yegor.Yarko
 *         Date: 22/01/2016
 */
@LocatorResource(value = LocatorName.BRANCH,
    extraDimensions = AbstractFinder.DIMENSION_ITEM,
    baseEntity = "Branch",
    examples = {
        "`build:<buildLocator>` - find branch with which the build found by `buildLocator` was started.",
        "`buildType:<buildTypeLocator>` - find branches of a build configuration found by `buildTypeLocator`."
    }
)
@JerseyContextSingleton
@Component("restBranchFinder") // Name copied from context xml file.
public class BranchFinder extends AbstractFinder<BranchData> implements ExistenceAwareFinder {
  @LocatorDimension("name")
  public static final String NAME = "name";
  @LocatorDimension(value = "default", format = LocatorDimensionDataType.BOOLEAN, notes = "Is default branch.")
  public static final String DEFAULT = "default";
  protected static final String UNSPECIFIED = "unspecified";
  @LocatorDimension(value = "branched", format = LocatorDimensionDataType.BOOLEAN, notes = "Is feature branch.")
  protected static final String BRANCHED = "branched"; //rather use "branched" dimension in build locator
  @LocatorDimension(value = "build", format = LocatorName.BUILD, notes = "Build locator.")
  protected static final String BUILD = "build";
  @LocatorDimension(value = "buildType", format = LocatorName.BUILD_TYPE, notes = "Build type locator.")
  protected static final String BUILD_TYPE = "buildType";

  public static final String BRANCH_GROUP = "group";
  protected static final String GROUP_INCLUDE = "includeGroups"; //this activates a temporary/experemental hack to include branch groups as fake branches in the result

  @LocatorDimension(value = "policy", allowableValues = "VCS_BRANCHES,ACTIVE_VCS_BRANCHES,HISTORY_BRANCHES,ACTIVE_HISTORY_BRANCHES,ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES,ALL_BRANCHES")
  public static final String POLICY = "policy";
  protected static final String CHANGES_FROM_DEPENDENCIES = "changesFromDependencies";   //todo: revise naming

  private static final String ANY = "<any>";
  protected static final String COMPUTE_TIMESTAMPS = "computeLastActivity"; //experimental

  @NotNull
  private final BuildTypeFinder myBuildTypeFinder;
  @NotNull
  private final ServiceLocator myServiceLocator;
  @NotNull
  private final BranchGroupsService myBranchGroupsService;

  public BranchFinder(@NotNull final BuildTypeFinder buildTypeFinder, @NotNull final ServiceLocator serviceLocator) {
    super(NAME, DEFAULT, UNSPECIFIED, BUILD_TYPE, BUILD, POLICY, CHANGES_FROM_DEPENDENCIES, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME); //see also getBranchFilterDetails
    setHiddenDimensions(BRANCHED, COMPUTE_TIMESTAMPS, BRANCH_GROUP, GROUP_INCLUDE);
    myBuildTypeFinder = buildTypeFinder;
    myServiceLocator = serviceLocator;
    myBranchGroupsService = myServiceLocator.getSingletonService(BranchGroupsService.class);
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
  public static String patchLocatorWithBuildType(@Nullable final String branchLocator, @Nullable final String buildTypeLocator) {
    return Locator.setDimensionIfNotPresent(branchLocator, BUILD_TYPE, buildTypeLocator);
  }

  @NotNull
  @Override
  public ItemFilter<BranchData> getFilter(@NotNull final Locator locator) {
    return getBranchFilterDetails(locator).filter;
  }

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
    final List<FilterConditionChecker<BranchData>> filter = new ArrayList<>();
    final BranchFilterDetails result = new BranchFilterDetails();

    final String singleValue = locator.getSingleValue();
    if (singleValue != null) {
      if (!ANY.equals(singleValue)) {
//        result.branchName = singleValue;  do not set as it is ignore case and can match display/vcs branch
        filter.add(item -> singleValue.equalsIgnoreCase(item.getDisplayName()) || singleValue.equalsIgnoreCase(item.getName()));
        result.filter = MultiCheckerFilter.of(filter).toItemFilter();
        return result;
      } else {
        result.matchesAllBranches = true;
        result.filter = MultiCheckerFilter.of(filter).toItemFilter();
        return result;
      }
    }

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null && !ANY.equals(nameDimension)) {
      final ValueCondition parameterCondition = ParameterCondition.createValueCondition(nameDimension);
      boolean compatibilityMode;
      if (nameDimension.equals(parameterCondition.getValue())) {
        //single value
        compatibilityMode = true;
        if (parameterCondition.getIgnoreCase() == null) parameterCondition.setIgnoreCase(true); //pre-TeamCity-10 behavior
      } else {
        compatibilityMode = false;
      }
      String exactValue = parameterCondition.getConstantValueIfSimpleEqualsCondition();
      if (exactValue != null) result.branchName = exactValue;
      filter.add(item -> {
          if (compatibilityMode) {
            return parameterCondition.matches(item.getDisplayName()) || parameterCondition.matches(item.getName()); //this basically matched both actual name and "<default>" for default branch
          }
          return parameterCondition.matches(item.getDisplayName());
      });
    }

    final Boolean defaultDimension = locator.getSingleDimensionValueAsBoolean(DEFAULT);
    if (defaultDimension != null) {
      if (defaultDimension) {
        result.matchesDefaultBranchOrNotBranched = true;
      }
      filter.add(item -> FilterUtil.isIncludedByBooleanFilter(defaultDimension, item.isDefaultBranch()));
    }

    final Boolean unspecifiedDimension = locator.getSingleDimensionValueAsBoolean(UNSPECIFIED);
    if (unspecifiedDimension != null) {
      result.unspecified = true;
      filter.add(item -> FilterUtil.isIncludedByBooleanFilter(unspecifiedDimension, Branch.UNSPECIFIED_BRANCH_NAME.equals(item.getName())));
    }

    final Boolean branchedDimension = locator.getSingleDimensionValueAsBoolean(BRANCHED);
    if (branchedDimension != null) {
      filter.add(item -> FilterUtil.isIncludedByBooleanFilter(branchedDimension, BranchData.isBranched(item)));
    }

    result.matchesAllBranches = filter.size() == 0 &&
                                locator.getUnusedDimensions().isEmpty(); //e.g. "count" or "item" dimension is present

    result.filter = MultiCheckerFilter.of(filter).toItemFilter();
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
    return getPrefilteredItemsInternal(locator, false);
  }

  @NotNull
  private ItemHolder<BranchData> getPrefilteredItemsInternal(@NotNull final Locator locator, boolean existenseCheck) {
    String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (!StringUtil.isEmpty(buildLocator)) {
      BuildPromotion build = myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItem(buildLocator);
      return ItemHolder.of(Collections.singleton(BranchData.fromBuild(build)));
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator == null) {
      throw new BadRequestException("No '" + BUILD_TYPE + "' dimension is present but it is required for searching branches. Locator: '" + locator.getStringRepresentation() + "'");
    }
    final List<SBuildType> buildTypes = myBuildTypeFinder.getBuildTypes(null, buildTypeLocator);

    List<ItemHolder<BranchData>> result = new ArrayList<>();

    final String groupsInclude = locator.getSingleDimensionValue(GROUP_INCLUDE);
    if (groupsInclude != null) {
      SUser user = validateAndgetGroupIncludeUser(groupsInclude);
      result.add(ItemHolder.of(
        buildTypes
          .stream()
          .flatMap(buildType -> myBranchGroupsService.getAvailableBranchGroups(
            new BranchGroupsProvider.Context((BuildTypeEx)buildType, user)
          ).stream())
          .distinct()
          .map(branchGroup -> BranchData.fromBranchGroup(branchGroup))
      ));
    }

    final String groupDimension = locator.getSingleDimensionValue(BRANCH_GROUP);
    if (groupDimension != null) {
      BranchGroupFilterDetails groupDetails = getBranchGroupFilterDetails(groupDimension, buildTypes.get(0));

      result.add(processor -> {
        try {
          buildTypes.forEach(buildType -> {
            BranchGroupsProvider.Context ctx = new BranchGroupsProvider.Context((BuildTypeEx)buildType, groupDetails.getUser());
            myBranchGroupsService.collectBranches(
              groupDetails.getBranchGroupId(),
              ctx,
              branchEx -> processor.processItem(BranchData.fromBranchEx(branchEx, myServiceLocator, null, true))
            );
          });
        } catch (IllegalStateException e) {
          throw new BadRequestException("Error retrieving branch groups: " + e.getMessage());
        }
      });
      return ItemHolder.concat(result);
    }

    BranchSearchOptions searchOptions = getBranchSearchOptions(locator);
    boolean lookingForDefaultBranch = BooleanUtils.isTrue(locator.getSingleDimensionValueAsBoolean(DEFAULT));
    // We must make sure that no unused dimensions are present as otherwise we may stop too early and then get filtered,
    // i.e. loose relevant item.
    if(lookingForDefaultBranch && existenseCheck && locator.getUnusedDimensions().isEmpty()) {
      for (SBuildType buildType : buildTypes) {
        final BranchData branch = detectDefaultBranch(buildType, searchOptions);

        // As soon as default branch is found, create a simple processor and don't look into other buildTypes.
        if(branch != null) {
          result.add(processor -> processor.processItem(branch));
          break;
        }
      }
    } else {
      locator.markUnused(DEFAULT);

      Filter<SBuildType> dependenciesFilter = getBranchDependenciesFilter(buildTypes);

      if(existenseCheck) {
        // We don't use deduplication here as in this case we have a chance to stop earlier
        // using lazy stream evaluation and avoid getting branches from all build types.
        // We also skip computing timestamps as that is unnecessary for an existence check.
        locator.markUsed(COMPUTE_TIMESTAMPS);

        Stream<BranchData> branchStream = buildTypes.stream()
                                                    .flatMap(buildType -> getBranches(buildType, searchOptions, false, dependenciesFilter));

        result.add(ItemHolder.of(branchStream));
      } else {
        DeduplicatingAccumulator resultAccumulator = new DeduplicatingAccumulator();
        for (SBuildType buildType : buildTypes) {
          Boolean locatorComputeTimestamps = locator.getSingleDimensionValueAsBoolean(COMPUTE_TIMESTAMPS);
          boolean finalComputeTimestamps = locatorComputeTimestamps != null ? locatorComputeTimestamps : TeamCityProperties.getBoolean("rest.beans.branch.defaultComputeTimestamp");
          Stream<BranchData> branchStream = getBranches(buildType, searchOptions, finalComputeTimestamps, dependenciesFilter);
          resultAccumulator.addAll(branchStream);
        }

        result.add(ItemHolder.of(resultAccumulator.get()));
      }
    }

    return ItemHolder.concat(result);
  }

  @NotNull
  private Filter<SBuildType> getBranchDependenciesFilter(@NotNull final List<SBuildType> buildTypes) {
    // this filter disables fetching of branches from dependencies if they present in the buildTypes list
    // since we're going to traverse all build types form the buildTypes it makes sense to fetch branches from them once,
    // without this filter we'd traverse branches of a single build type 1 + as many times as it is accessible via snapshot dependencies
    final Set<SBuildType> myFilteredBuildTypes = new HashSet<>(buildTypes);
    return data -> !myFilteredBuildTypes.contains(data);
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

  @NotNull
  private BranchSearchOptions getBranchSearchOptions(@NotNull final Locator locator) {
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

  @Nullable
  private BranchData detectDefaultBranch(@NotNull final SBuildType buildType, @NotNull final BranchSearchOptions branchSearchOptions) {
    final BuildTypeEx buildTypeImpl = (BuildTypeEx)buildType; //TeamCity openAPI issue: cast
    BranchCalculationOptions calculationOptions = new BranchCalculationOptions()
      .setBranchesPolicy(branchSearchOptions.getBranchesPolicy())
      .setComputeTimestamps(false)
      .setSortBranches(false)
      .setIncludeBranchesFromDependencies(branchSearchOptions.isIncludeBranchesFromDependencies());

    for(BranchEx branch : buildTypeImpl.getBranches(calculationOptions)) {
      if(!branch.isDefaultBranch()) continue;

      return BranchData.fromBranchEx(branch, myServiceLocator, true, false);
    }

    return null;
  }

  @NotNull
  public BranchGroupFilterDetails getBranchGroupFilterDetails(@NotNull String groupDimension, @Nullable SBuildType buildTypeForErrorMessage) {
    Locator branchGroupLocator = new Locator(groupDimension, "id", "user", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    String userLocator = branchGroupLocator.getSingleDimensionValue("user");
    UserFinder userFinder = myServiceLocator.getSingletonService(UserFinder.class);

    SUser user = userLocator == null ? userFinder.getCurrentUser() : userFinder.getItem(userLocator);
    if (user == null) {
      throw new BadRequestException("Can only filter by branch group when the user is present");
    } else {
      userFinder.checkViewUserPermission(user);
    }

    String branchGroupId = branchGroupLocator.isSingleValue() ? branchGroupLocator.getSingleValue() : branchGroupLocator.getSingleDimensionValue("id");
    if (branchGroupId == null) {
      String errorMessage = "Dimension '" + BRANCH_GROUP + "' does not specify 'id' subdimension.";
      if(buildTypeForErrorMessage != null) {
        BranchGroupsProvider.Context ctx = new BranchGroupsProvider.Context((BuildTypeEx)buildTypeForErrorMessage, user);
        errorMessage += "Example: " + myBranchGroupsService.getAvailableBranchGroups(ctx).stream()
                                                         .map(branchGroup -> branchGroup.getId())
                                                         .collect(Collectors.joining(", "));
      }

      throw new BadRequestException(errorMessage);
    }

    branchGroupLocator.checkLocatorFullyProcessed();
    return new BranchGroupFilterDetails(branchGroupId, user);
  }

  @NotNull
  protected Stream<BranchData> getBranches(@NotNull final SBuildType buildType,
                                           @NotNull final BranchSearchOptions branchSearchOptions,
                                           final boolean computeTimestamps,
                                           @NotNull final Filter<SBuildType> dependenciesFilter) {
    final BuildTypeEx buildTypeImpl = (BuildTypeEx)buildType; //TeamCity openAPI issue: cast
    BranchesPolicy mainPolicy = branchSearchOptions.getBranchesPolicy();
    BranchCalculationOptions branchCalculationOptions = new BranchCalculationOptions()
      .setBranchesPolicy(mainPolicy)
      .setComputeTimestamps(computeTimestamps)
      .setIncludeBranchesFromDependencies(branchSearchOptions.includeBranchesFromDependencies)
      .setDependenciesFilter(dependenciesFilter)
      .setSortBranches(false);
    List<BranchEx> branches = buildTypeImpl.getBranches(branchCalculationOptions);
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
        return branches.stream().map(b -> BranchData.fromBranchEx(b, myServiceLocator, computeActive ? true : null, disableActive));
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
    return branches.stream().map(b -> BranchData.fromBranchEx(b, myServiceLocator, computeActive ? activeBranches.contains(b.getName()) : null, disableActive));
  }

  @NotNull
  public PagedSearchResult<BranchData> getItems(@NotNull final SBuildType buildType, @Nullable final String locatorText) {
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
  public PagedSearchResult<BranchData> getItemsIfValidBranchListLocator(@Nullable final String buildTypesLocator, @Nullable final String locatorText) {
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

  @Override
  public boolean itemsExist(@NotNull Locator locator) {
    ItemHolder<BranchData> prefilteredItems = getPrefilteredItemsInternal(locator, true);
    ItemFilter<BranchData> filter = getFilter(locator);

    AtomicBoolean result = new AtomicBoolean(false);
    ItemProcessor<BranchData> existenceChecker = branchData -> {
      if (!filter.isIncluded(branchData)) {
        return true;
      }

      result.compareAndSet(false, true);
      return false;
    };

    prefilteredItems.process(existenceChecker);

    return result.get();
  }

  protected class BranchSearchOptions {
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

  /** Deduplicates branches by name */
  private static class DeduplicatingAccumulator {
    //de-duplicate by name, ordering is not important here
    private final Map<String, BranchData> myMap = new HashMap<>();

    void addAll(@NotNull final Stream<BranchData> buildTypeBranches) {
      buildTypeBranches.forEach(branch -> {
        //assuming that branch.isDefaultBranch() means Branch.DEFAULT_BRANCH_NAME.equals(name)

        BranchData previousData = myMap.get(branch.getName());
        if (previousData == null) {
          myMap.put(branch.getName(), branch);
        } else {
          myMap.put(branch.getName(), BranchData.mergeSameNamed(branch, previousData));
        }
      });
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

  public static class BranchGroupFilterDetails {
    private final String myBranchGroupId;
    private final SUser myUser;

    public BranchGroupFilterDetails(@NotNull String branchGroupId, @NotNull SUser user) {
      myBranchGroupId = branchGroupId;
      myUser = user;
    }

    @NotNull
    public String getBranchGroupId() {
      return myBranchGroupId;
    }

    @NotNull
    public SUser getUser() {
      return myUser;
    }
  }
}