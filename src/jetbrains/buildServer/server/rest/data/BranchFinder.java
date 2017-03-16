/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 22/01/2016
 */
public class BranchFinder extends AbstractFinder<Branch> {
  protected static final String BUILD_TYPE = "buildType";

  protected static final String NAME = "name";
  protected static final String DEFAULT = "default";
  protected static final String UNSPECIFIED = "unspecified";
  protected static final String BRANCHED = "branched";
  public static final String BUILD = "build";

  protected static final String POLICY = "policy";
  protected static final String CHANGES_FROM_DEPENDENCIES = "changesFromDependencies";   //todo: revise naming

  private static final String ANY = "<any>";

  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ServiceLocator myServiceLocator;

  public BranchFinder(@NotNull final BuildTypeFinder buildTypeFinder, @NotNull final ServiceLocator serviceLocator) {
    super(NAME, DEFAULT, UNSPECIFIED, BUILD_TYPE, BUILD, POLICY, CHANGES_FROM_DEPENDENCIES, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME); //see also getBranchFilterDetails
    setHiddenDimensions(BRANCHED);
    myBuildTypeFinder = buildTypeFinder;
    myServiceLocator = serviceLocator;
  }

  public String getDefaultBranchLocator() {
    return Locator.getStringLocator(DEFAULT, "true");
  }


  @Nullable
  @Contract("_, !null -> !null; !null,_ -> !null")
  public static String patchLocatorWithBuildType(final @Nullable String branchLocator, final @Nullable String buildTypeLocator) {
    return Locator.setDimensionIfNotPresent(branchLocator, BUILD_TYPE, buildTypeLocator);
  }

  @NotNull
  @Override
  public ItemFilter<Branch> getFilter(@NotNull final Locator locator) {
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
    final MultiCheckerFilter<Branch> filter = new MultiCheckerFilter<Branch>();
    final BranchFilterDetails result = new BranchFilterDetails();
    result.filter = filter;

    final String singleValue = locator.getSingleValue();
    if (singleValue != null) {
      if (!ANY.equals(singleValue)) {
//        result.branchName = singleValue;  do not set as it is ignore case and can match display/vcs branch
        filter.add(new FilterConditionChecker<Branch>() {
          @Override
          public boolean isIncluded(@NotNull final Branch item) {
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
      filter.add(new FilterConditionChecker<Branch>() {
        @Override
        public boolean isIncluded(@NotNull final Branch item) {
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
      filter.add(new FilterConditionChecker<Branch>() {
        @Override
        public boolean isIncluded(@NotNull final Branch item) {
          return FilterUtil.isIncludedByBooleanFilter(defaultDimension, item.isDefaultBranch());
        }
      });
    }

    final Boolean unspecifiedDimension = locator.getSingleDimensionValueAsBoolean(UNSPECIFIED);
    if (unspecifiedDimension != null) {
      result.unspecified = true;
      filter.add(new FilterConditionChecker<Branch>() {
        @Override
        public boolean isIncluded(@NotNull final Branch item) {
          return FilterUtil.isIncludedByBooleanFilter(unspecifiedDimension, Branch.UNSPECIFIED_BRANCH_NAME.equals(item.getName()));
        }
      });
    }

    final Boolean branchedDimension = locator.getSingleDimensionValueAsBoolean(BRANCHED);
    if (branchedDimension != null) {
      filter.add(new FilterConditionChecker<Branch>() {
        @Override
        public boolean isIncluded(@NotNull final Branch item) {
          return FilterUtil.isIncludedByBooleanFilter(branchedDimension, FAKE_DEFAULT_BRANCH != item);
        }
      });
    }

    result.matchesAllBranches = filter.getSubFiltersCount() == 0 &&
                                locator.getUnusedDimensions().isEmpty(); //e.g. "count" or "item" dimension is present
    return result;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final Branch branch) {
    if (branch.isDefaultBranch()) return Locator.getStringLocator(DEFAULT, "true");
    return Locator.getStringLocator(NAME, branch.getName());
  }

  @NotNull
  @Override
  public ItemHolder<Branch> getPrefilteredItems(@NotNull final Locator locator) {
    String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (!StringUtil.isEmpty(buildLocator)) {
      BuildPromotion build = myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItem(buildLocator);
      return getItemHolder(Collections.singleton(build.getBranch()));
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator == null) {
      throw new BadRequestException("No '" + BUILD_TYPE + "' dimension is present but it is required for searching branches. Locator: '" + locator.getStringRepresentation() + "'");
    }
    final List<SBuildType> buildTypes = myBuildTypeFinder.getBuildTypes(null, buildTypeLocator);

    BranchSearchOptions searchOptions = getBranchSearchOptionsWithDefaults(locator);
    Set<Branch> result = new TreeSet<>((o1, o2) -> {
      if (o1 == o2) return 0;
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      return o1.getName().compareToIgnoreCase(o2.getName()); //todo: consider default, same-named branches, same display name, etc.
    });
    for (SBuildType buildType : buildTypes) {
      result.addAll(getBranches(buildType, searchOptions));
    }
    
    return getItemHolder(result);
  }

  private class BranchSearchOptions {
    @NotNull private final BranchesPolicy branchesPolicy;
    private final boolean includeBranchesFromDependencies;

    public BranchSearchOptions(@NotNull final BranchesPolicy branchesPolicy, final boolean includeBranchesFromDependencies) {
      this.branchesPolicy = branchesPolicy;
      this.includeBranchesFromDependencies = includeBranchesFromDependencies;
    }

    @NotNull
    public BranchesPolicy getBranchesPolicy() {
      return branchesPolicy;
    }

    public boolean isIncludeBranchesFromDependencies() {
      return includeBranchesFromDependencies;
    }
  }

  @NotNull
  private BranchSearchOptions getBranchSearchOptionsWithDefaults(final @NotNull Locator locator) {
    final BranchSearchOptions result = getBranchSearchOptionsIfDefined(locator);
    if (result != null) {
      return result;
    }
    return new BranchSearchOptions(BranchesPolicy.ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES, false);
  }

  @Nullable
  private BranchSearchOptions getBranchSearchOptionsIfDefined(final @NotNull Locator locator) {
    BranchesPolicy branchesPolicy = BranchesPolicy.ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES;
    final String policyDimension = locator.getSingleDimensionValue(POLICY);
    if (policyDimension != null) {
      try {
        branchesPolicy = BranchesPolicy.valueOf(policyDimension.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Invalid value '" + policyDimension + "' for '" + POLICY + "' dimension. Supported values are: " + Arrays.toString(BranchesPolicy.values()));
      }
    }

    final Boolean changesFromDependenciesDimension = locator.getSingleDimensionValueAsBoolean(CHANGES_FROM_DEPENDENCIES, false);
    if (changesFromDependenciesDimension == null) {
      throw new BadRequestException("Dimension '" + CHANGES_FROM_DEPENDENCIES + "' supports only true/false values");
    }

    return new BranchSearchOptions(branchesPolicy, changesFromDependenciesDimension);
  }

  private List<BranchEx> getBranches(final @NotNull SBuildType buildType, @NotNull final BranchSearchOptions branchSearchOptions) {
    final BuildTypeEx buildTypeImpl = (BuildTypeEx)buildType; //TeamCity openAPI issue: cast
    return buildTypeImpl.getBranches(branchSearchOptions.getBranchesPolicy(), branchSearchOptions.isIncludeBranchesFromDependencies());
  }

  @NotNull
  public PagedSearchResult<Branch> getItems(@NotNull SBuildType buildType, @Nullable final String locatorText) {
    String baseLocator = locatorText;
    if (locatorText != null && new Locator(locatorText).isSingleValue()) {
      baseLocator = Locator.getStringLocator(NAME, locatorText);
    }
    return getItems(Locator.setDimensionIfNotPresent(baseLocator, BUILD_TYPE, myBuildTypeFinder.getCanonicalLocator(new BuildTypeOrTemplate(buildType))));
  }

  @Nullable
  public PagedSearchResult<Branch> getItemsIfValidBranchListLocator(@Nullable String buildTypesLocator, @Nullable final String locatorText) {
    final Locator locator = new Locator(locatorText);
    if (buildTypesLocator != null && (locator.getSingleDimensionValue(POLICY) != null || locator.getSingleDimensionValue(CHANGES_FROM_DEPENDENCIES) != null)){
      locator.setDimensionIfNotPresent(BUILD_TYPE, buildTypesLocator);
    }
    try {
      return getItems(locator.getStringRepresentation());
    } catch (BadRequestException e) {
      // not a valid branches listing locator
      return null;
    }
  }

  @NotNull
  public TreeSet<Branch> createContainerSet() {
    return new TreeSet<>(new Comparator<Branch>() {
      @Override
      public int compare(final Branch o1, final Branch o2) {
        return ComparisonChain.start()
                              .compareTrueFirst(o1.isDefaultBranch(), o2.isDefaultBranch())
                              .compare(o1.getName(), o2.getName())
                              .result();
      }
    });
  }

  public static class BranchFilterDetails {
    private ItemFilter<Branch> filter;
    private String branchName; // name or display name of the branch, if set. Even if set, there might be other conditions set
    private boolean matchesAllBranches = false;
    private boolean matchesDefaultBranchOrNotBranched = false;
    private boolean unspecified = false;

    public boolean isIncluded(@NotNull final BuildPromotion promotion) {
      if (matchesAllBranches) {
        return true;
      }
      return filter.isIncluded(getBuildBranch(promotion));
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

  @NotNull
  public static Branch getBuildBranch(@NotNull final BuildPromotion build) {
    final Branch buildBranch = build.getBranch();
    if (buildBranch == null) {
      return FAKE_DEFAULT_BRANCH;
    }
    return buildBranch;
  }

  protected static final Branch FAKE_DEFAULT_BRANCH = new Branch() {
    @NotNull
    @Override
    public String getName() {
      return "<not_branched>";
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return "<not_branched>";
    }

    @Override
    public boolean isDefaultBranch() {
      return true;
    }
  };
}