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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.data.build.GenericBuildsFilter;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specifies branch locator.
 * @author Yegor.Yarko
 *         Date: 18.01.12
 */
public class BranchMatcher {
  protected static final String NAME = "name";
  protected static final String DEFAULT = "default";
  protected static final String UNSPECIFIED = "unspecified";
  protected static final String BRANCHED = "branched";
  @Nullable private final Locator myLocator;

  public BranchMatcher(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)){
      myLocator = null;
    }else{
      myLocator = new Locator(locatorText);  //todo add known dimensions, check for them
    }
  }

  public static String getDefaultBranchLocator(){
    return Locator.getStringLocator(DEFAULT, "true");
  }

  public boolean isDefined(){
    return myLocator != null;
  }

  public boolean matches(@NotNull final BuildPromotion build){
    if (matchesAnyBranch()){
      return true;
    }
    @Nullable final Branch buildBranch = build.getBranch();
    if (myLocator == null){
      return buildBranch == null || buildBranch.isDefaultBranch();
    }
    return BranchMatcher.matchesBranchLocator(myLocator, buildBranch);
  }

  public boolean matchesAnyBranch() {
    if (myLocator == null) { //only default branch
      return false;
    }
    if (myLocator.isSingleValue()) {
      return GenericBuildsFilter.BRANCH_NAME_ANY.equals(myLocator.getSingleValue());
    }

    final String branchName = myLocator.getSingleDimensionValue(NAME);
    final Boolean defaultBranch = myLocator.getSingleDimensionValueAsBoolean(DEFAULT);
    final Boolean unspecifiedBranch = myLocator.getSingleDimensionValueAsBoolean(UNSPECIFIED);
    final Boolean branched = myLocator.getSingleDimensionValueAsBoolean(BRANCHED);
    return branchName == null && defaultBranch == null && unspecifiedBranch == null && branched == null;
  }

  private static boolean matchesBranchLocator(@NotNull final Locator locator, @Nullable final Branch buildBranch) {
    //todo consider optimizing by parsing locator beforehand + validating all locator dimensions are used
    if (locator.isSingleValue()){//treat as logic branch name with special values
      @SuppressWarnings("ConstantConditions")
      @NotNull final String logicalBranchName = locator.getSingleValue();
      //noinspection ConstantConditions
      return matchesBranchName(logicalBranchName, buildBranch);
    }

    final String branchName = locator.getSingleDimensionValue(NAME);
    final Boolean defaultBranch = locator.getSingleDimensionValueAsBoolean(DEFAULT);
    final Boolean unspecifiedBranch = locator.getSingleDimensionValueAsBoolean(UNSPECIFIED);
    final Boolean branched = locator.getSingleDimensionValueAsBoolean(BRANCHED);
    if (defaultBranch != null) {
      if (buildBranch != null && !defaultBranch.equals(buildBranch.isDefaultBranch())) {
        return false;
      }
      if (buildBranch == null && !defaultBranch) { //making default:true match not-branched builds
        return false;
      }
    }
    if (unspecifiedBranch != null) {
      if (buildBranch != null && !unspecifiedBranch.equals(Branch.UNSPECIFIED_BRANCH_NAME.equals(buildBranch.getName()))){
        return false;
      }
      if (buildBranch == null && unspecifiedBranch) {
        return false;
      }
    }
    if (branchName != null && !matchesBranchName(branchName, buildBranch)) {
      return false;
    }
    if (branched != null){
      if (!branched.equals(buildBranch != null)){
        return false;
      }
    }
    //todo: provide a way to get only builds without a branch
    //todo: add fully used locator check: locator.checkLocatorFullyProcessed(); (parse locator on creation?)
    return true;
  }


  private static boolean matchesBranchName(@NotNull final String branchNameToMatch, @Nullable final Branch buildBranch) {
    if (branchNameToMatch.equals(GenericBuildsFilter.BRANCH_NAME_ANY)){
      return true;
    }
    if (buildBranch == null){ //may be can return true if branchNameToMatch.equals("")
      return false;
    }
    return branchNameToMatch.equals(buildBranch.getDisplayName()) || branchNameToMatch.equals(buildBranch.getName());
  }

  @Override
  public String toString() {
    return (myLocator == null ? "<empty>" : myLocator.toString());
  }

  @Nullable
  public String getSingleBranchIfNotDefault() {
    //refactor and reuse code
    if (myLocator == null) {
      return null;
    }
    if (myLocator.isSingleValue() && !GenericBuildsFilter.BRANCH_NAME_ANY.equals(myLocator.getSingleValue())) {
      return myLocator.getSingleValue();
    }

    final String branchName = myLocator.getSingleDimensionValue(NAME);
    final Boolean defaultBranch = myLocator.getSingleDimensionValueAsBoolean(DEFAULT);
    final Boolean unspecifiedBranch = myLocator.getSingleDimensionValueAsBoolean(UNSPECIFIED);
    final Boolean branched = myLocator.getSingleDimensionValueAsBoolean(BRANCHED);
    if (branchName != null &&
        (defaultBranch == null || !defaultBranch) &&
        unspecifiedBranch == null &&
        (branched == null || branched)){
      return branchName;
    }

    return null;
  }
}
