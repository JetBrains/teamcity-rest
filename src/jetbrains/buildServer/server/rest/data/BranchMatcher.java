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

import jetbrains.buildServer.server.rest.data.build.GenericBuildsFilter;
import jetbrains.buildServer.server.rest.data.finder.impl.BranchFinder;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specifies branch locator.
 * @deprected see {@link BranchFinder}
 * @author Yegor.Yarko
 *         Date: 18.01.12
 */
public class BranchMatcher {
  protected static final String NAME = "name";
  protected static final String DEFAULT = "default";
  protected static final String UNSPECIFIED = "unspecified";
  protected static final String BRANCHED = "branched";
  @Nullable private final Locator myLocator;
  @Nullable private final String mySingleValue;
  @Nullable private final String myBranchName;
  @Nullable private final Boolean myDefaultBranch;
  @Nullable private final Boolean myUnspecifiedBranch;
  @Nullable private final Boolean myBranched;

  public BranchMatcher(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)){
      myLocator = null;
      mySingleValue = null;
      myBranchName = null;
      myDefaultBranch = null;
      myUnspecifiedBranch = null;
      myBranched = null;
    }else{
      myLocator = new Locator(locatorText, NAME, DEFAULT, UNSPECIFIED, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
      myLocator.addHiddenDimensions(BRANCHED);
      mySingleValue = myLocator.getSingleValue();
      myBranchName = myLocator.getSingleDimensionValue(NAME);
      myDefaultBranch = myLocator.getSingleDimensionValueAsBoolean(DEFAULT);
      myUnspecifiedBranch = myLocator.getSingleDimensionValueAsBoolean(UNSPECIFIED);
      myBranched = myLocator.getSingleDimensionValueAsBoolean(BRANCHED);
      myLocator.checkLocatorFullyProcessed(); //might need checking that the values retrieved are actually used
    }
  }

  public boolean isDefined(){
    return myLocator != null;
  }

  public boolean matches(@NotNull final BuildPromotion build){
    if (matchesAnyBranch()){
      return true;
    }
    return matchesBranch(build.getBranch());
  }

  public boolean matchesAnyBranch() {
    if (myLocator == null) {
      return true;
    }
    if (mySingleValue != null) {
      return GenericBuildsFilter.BRANCH_NAME_ANY.equals(mySingleValue);
    }

    return myBranchName == null && myDefaultBranch == null && myUnspecifiedBranch == null && myBranched == null;
  }

  //see also matchesDefaultBranchOrNotBranchedBuildsOnly()
  private boolean matchesBranch(@Nullable final Branch buildBranch) {
    if (myLocator == null){
      return true; //buildBranch == null || buildBranch.isDefaultBranch();
    }
    if (mySingleValue != null) {//treat as logic branch name with special values
      return matchesBranchName(mySingleValue, buildBranch);
    }

    if (myDefaultBranch != null) {
      if (buildBranch != null && !myDefaultBranch.equals(buildBranch.isDefaultBranch())) {
        return false;
      }
      if (buildBranch == null && !myDefaultBranch) { //making default:true match not-branched builds
        return false;
      }
    }
    if (myUnspecifiedBranch != null) {
      if (buildBranch != null && !myUnspecifiedBranch.equals(Branch.UNSPECIFIED_BRANCH_NAME.equals(buildBranch.getName()))) {
        return false;
      }
      if (buildBranch == null && myUnspecifiedBranch) {
        return false;
      }
    }
    if (myBranchName != null && !matchesBranchName(myBranchName, buildBranch)) {
      return false;
    }
    if (myBranched != null) {
      if (!myBranched.equals(buildBranch != null)) {
        return false;
      }
    }
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

}
