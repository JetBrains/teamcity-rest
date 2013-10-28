package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.data.build.GenericBuildsFilter;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specifies branch locator.
 * @author Yegor.Yarko
 *         Date: 18.01.12
 */
public class BranchMatcher {
  @Nullable private final Locator myLocator;

  public BranchMatcher(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)){
      myLocator = null;
    }else{
      myLocator = new Locator(locatorText);
    }
  }

  public boolean isDefined(){
    return myLocator != null;
  }

  public boolean matches(@NotNull final SBuild build){
    @Nullable final Branch buildBranch = build.getBranch();
    if (myLocator == null){
      return buildBranch == null || buildBranch.isDefaultBranch();
    }
    return BranchMatcher.matchesBranchLocator(myLocator, buildBranch);
  }

  private static boolean matchesBranchLocator(@NotNull final Locator locator, @Nullable final Branch buildBranch) {
    //todo consider optimizing by parsing locator beforehand + validating all locator dimensions are used
    if (locator.isSingleValue()){//treat as logic branch name with special values
      @SuppressWarnings("ConstantConditions")
      @NotNull final String logicalBranchName = locator.getSingleValue();
      //noinspection ConstantConditions
      return matchesBranchName(logicalBranchName, buildBranch);
    }

    final String branchName = locator.getSingleDimensionValue("name");
    final Boolean defaultBranch = locator.getSingleDimensionValueAsBoolean("default");
    final Boolean unspecifiedBranch = locator.getSingleDimensionValueAsBoolean("unspecified");
    final Boolean branched = locator.getSingleDimensionValueAsBoolean("branched");
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
    return true;
  }


  private static boolean matchesBranchName(@NotNull final String branchNameToMatch, @Nullable final Branch buildBranch) {
    if (branchNameToMatch.equals(GenericBuildsFilter.BRANCH_NAME_ANY)){
      return true;
    }
    if (buildBranch == null){ //may be can return true if branchNameToMatch.equals("")
      return false;
    }
    if (branchNameToMatch.equals(buildBranch.getDisplayName()) || branchNameToMatch.equals(buildBranch.getName())){
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return (myLocator == null ? "<empty>" : myLocator.toString());
  }
}
