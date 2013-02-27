/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenericBuildsFilter implements BuildsFilter {
  final static Logger LOG = Logger.getInstance(GenericBuildsFilter.class.getName());

  public static final String BRANCH_NAME_ANY = "<any>";

  @Nullable private final String myNumber;
  @Nullable protected Long myStart;
  @Nullable protected Integer myCount;

  @Nullable private final String myStatus;
  @Nullable private final Boolean myPersonal;
  @Nullable private final Boolean myCanceled;
  @Nullable private final Boolean myRunning;
  @Nullable private final Boolean myPinned;
  @Nullable private final List<String> myTags;
  @Nullable private final Locator myBranchLocator;
  @Nullable private final String myAgentName;
  @Nullable private final RangeLimit mySince;
  @Nullable private final RangeLimit myUntil;
  @Nullable private final Long myLookupLimit;
  @Nullable private final ParameterCondition myParameterCondition;
  @Nullable private final SUser myUser;
  @Nullable private final SBuildType myBuildType;

  /**
   * @param buildType       build type to return builds from, can be null to return all builds
   * @param status          status of the builds to include
   * @param number          build number of the builds to include
   * @param user            limit builds to those triggered by user, can be null to return all builds
   * @param personal        if set, limits the builds by personal status (return only personal if "true", only non-personal if "false")
   * @param canceled        if set, limits the builds by canceled status (return only canceled if "true", only non-canceled if "false")
   * @param running         if set, limits the builds by running state (return only running if "true", only finished if "false")
   * @param pinned          if set, limits the builds by pinned status (return only pinned if "true", only non-pinned if "false")
   * @param branchLocator   if not set, only builds from default branch match. The locator supports dimensions: "name"/String, "default"/boolean and "unspecified"/boolean.
   * @param agentName       limit builds to those ran on specified agent, can be null to return all builds
   * @param parameterCondition  limit builds to those with a finish parameter matching the condition specified, can be null to return all builds
   * @param since           the RangeLimit to return only the builds since the limit. If contains build, it is not included, if contains the date, the builds that were started at and later then the date are included
   * @param until           the RangeLimit to return only the builds until the limit. If contains build, it is included, if contains the date, the builds that were started at and before the date are included
   * @param start           the index of the first build to return (begins with 0), 0 by default
   * @param count           the number of builds to return, all by default
   * @param lookupLimit     the number of builds to search. Matching results only within first 'lookupLimit' builds will be returned
   */
  public GenericBuildsFilter(@Nullable final SBuildType buildType,
                             @Nullable final String status,
                             @Nullable final String number,
                             @Nullable final SUser user,
                             @Nullable final Boolean personal,
                             @Nullable final Boolean canceled,
                             @Nullable final Boolean running,
                             @Nullable final Boolean pinned,
                             @Nullable final List<String> tags,
                             @Nullable final Locator branchLocator,
                             @Nullable final String agentName,
                             @Nullable final ParameterCondition parameterCondition,
                             @Nullable final RangeLimit since,
                             @Nullable final RangeLimit until,
                             @Nullable final Long start,
                             @Nullable final Integer count,
                             @Nullable final Long lookupLimit
  ) {
    myStart = start;
    myCount = count;

    myBuildType = buildType;
    myStatus = status;
    myNumber = number;
    myUser = user;
    myPersonal = personal;
    myCanceled = canceled;
    myRunning = running;
    myPinned = pinned;
    myTags = tags;
    myBranchLocator = branchLocator;
    //todo: support agent locator
    myAgentName = agentName;
    mySince = since;
    myUntil = until;
    myLookupLimit = lookupLimit;
    myParameterCondition = parameterCondition;
  }

  @Nullable
  public Long getStart() {
    return myStart;
  }

  public void setStart(@Nullable final Long start) {
    myStart = start;
  }


  @Nullable
  public Integer getCount() {
    return myCount;
  }

  public void setCount(@Nullable final Integer count) {
    myCount = count;
  }

  @Nullable
  public String getStatus() {
    return myStatus;
  }

  @Nullable
  public Boolean getPersonal() {
    return myPersonal;
  }

  @Nullable
  public Boolean getCanceled() {
    return myCanceled;
  }

  @Nullable
  public Boolean getRunning() {
    return myRunning;
  }

  @Nullable
  public Boolean getPinned() {
    return myPinned;
  }

  @Nullable
  public SUser getUser() {
    return myUser;
  }

  @Nullable
  public SBuildType getBuildType() {
    return myBuildType;
  }

  @Nullable
  public Long getLookupLimit() {
    return myLookupLimit;
  }

  @Nullable
  public String getNumber() {
    return myNumber;
  }

  public boolean isIncluded(@NotNull final SBuild build) {
    if (myAgentName != null && !myAgentName.equals(build.getAgentName())) {
      return false;
    }
    if (myBuildType != null && !myBuildType.getBuildTypeId().equals(build.getBuildTypeId())) {
      return false;
    }
    if (myStatus != null && !myStatus.equalsIgnoreCase(build.getStatusDescriptor().getStatus().getText())) {
      return false;
    }
    if (myNumber != null && !myNumber.equals(build.getBuildNumber())) {
      return false;
    }
    if (!isIncludedByBooleanFilter(myPersonal, build.isPersonal())) {
      return false;
    }
    if (!isIncludedByBooleanFilter(myCanceled, build.getCanceledInfo() != null)) {
      return false;
    }
    if (!isIncludedByBooleanFilter(myRunning, !build.isFinished())) {
      return false;
    }
    if (!isIncludedByBooleanFilter(myPinned, build.isPinned())) {
      return false;
    }
    if (myTags != null && myTags.size() > 0 && myTags.get(0).startsWith("format:extended")) {
      @NotNull final List<String> buildTags = build.getTags();
      //unofficial experimental support for "tag:(format:regexp,value:.*)" tag specification
      //todo: locator parsing logic should be moved to build locator parsing
      final Locator tagsLocator;
      try {
        tagsLocator = new Locator(myTags.get(0));
          if (!isTagsMatchLocator(buildTags, tagsLocator)){
            return false;
          }
        final Set<String> unusedDimensions = tagsLocator.getUnusedDimensions();
        if (unusedDimensions.size() > 0) {
          throw new BadRequestException("Unknown dimensions in locator 'tag': " + unusedDimensions);
        }
      } catch (LocatorProcessException e) {
        throw new BadRequestException("Invalid locator 'tag': " + e.getMessage(), e);
      }
    }else if (myTags != null && myTags.size() > 0 && !build.getTags().containsAll(myTags)) {
      return false;
    }
    if (!matchesBranchLocator(myBranchLocator, build)) {
      return false;
    }
    if (myUser != null) {
      final SUser userWhoTriggered = build.getTriggeredBy().getUser();
      if (!build.getTriggeredBy().isTriggeredByUser() || (userWhoTriggered == null) || (myUser.getId() != userWhoTriggered.getId())) {
        return false;
      }
    }
    if (isExcludedBySince(build))
      return false;

    if (myUntil != null) {
      if (myUntil.getDate().before(build.getStartDate())) {
        return false;
      }
    }
    
    if (myParameterCondition != null){
      if (!myParameterCondition.matches(build)){
        return false;
      }
    }
    return true;
  }

  private boolean isTagsMatchLocator(final List<String> buildTags, final Locator tagsLocator) {
    if (!"extended".equals(tagsLocator.getSingleDimensionValue("format"))) {
      throw new BadRequestException("Only 'extended' value is supported for 'format' dimension of 'tag' dimension");
    }
    final Boolean present = tagsLocator.getSingleDimensionValueAsBoolean("present", true);
    final String patternString = tagsLocator.getSingleDimensionValue("regexp");
    if (present == null) {
      return true;
    }
    Boolean tagsMatchPattern = null;
    if (patternString != null) {
      if (StringUtil.isEmpty(patternString)) {
        throw new BadRequestException("'regexp' sub-dimension should not be empty for 'tag' dimension");
      }
      try {
        tagsMatchPattern = tagsMatchPattern(buildTags, patternString);
      } catch (PatternSyntaxException e) {
        throw new BadRequestException(
          "Bad syntax for Java regular expression in 'regexp' sub-dimension of 'tag' dimension: " + e.getMessage(), e);
      }
    }
    if (tagsMatchPattern == null) {
      if ((present && buildTags.size() != 0) || (!present && (buildTags.size() == 0))) {
        return true;
      }
    } else {
      if (present && tagsMatchPattern) {
        return true;
      } else if (!present && !tagsMatchPattern) {
        return true;
      }
    }
    return false;
  }

  private Boolean tagsMatchPattern(@NotNull final List<String> tags, @NotNull final String patternString) throws PatternSyntaxException {
    final Pattern pattern = Pattern.compile(patternString);
    boolean atLestOneMatches = false;
    for (String tag : tags) {
      atLestOneMatches = atLestOneMatches || pattern.matcher(tag).matches();
    }
    return atLestOneMatches;
  }

  private boolean matchesBranchLocator(@Nullable Locator branchLocator, @NotNull final SBuild build) {
    //todo consider optimizing by parsing locator beforehand + validating all locator dimensions are used
    final Branch buildBranch = build.getBranch();
    if (branchLocator == null){
      return buildBranch == null || buildBranch.isDefaultBranch();
    }
    if (branchLocator.isSingleValue()){//treat as logic branch name with special values
      @SuppressWarnings("ConstantConditions")
      @NotNull final String logicalBranchName = branchLocator.getSingleValue();
      //noinspection ConstantConditions
      return matchesBranchName(logicalBranchName, buildBranch);
    }

    final String branchName = branchLocator.getSingleDimensionValue("name");
    final Boolean defaultBranch = branchLocator.getSingleDimensionValueAsBoolean("default");
    final Boolean unspecifiedBranch = branchLocator.getSingleDimensionValueAsBoolean("unspecified");
    final Boolean branched = branchLocator.getSingleDimensionValueAsBoolean("branched");
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

  private boolean matchesBranchName(@NotNull final String branchNameToMatch, @Nullable final Branch buildBranch) {
    if (branchNameToMatch.equals(BRANCH_NAME_ANY)){
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

  public boolean isExcludedBySince(final SBuild build) {
    if (mySince != null) {
      if (mySince.getDate().after(build.getStartDate())) {
        return true;
      } else {
         final SBuild sinceBuild = mySince.getBuild();
        if (sinceBuild != null && sinceBuild.getBuildId() >= build.getBuildId()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isIncludedByBooleanFilter(final Boolean filterValue, final boolean actualValue) {
    return filterValue == null || (!(filterValue ^ actualValue));
  }


  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("Builds filter (");
    if (myBuildType!= null) result.append("buildType:").append(myBuildType).append(", ");
    if (myStatus!= null) result.append("status:").append(myStatus).append(", ");
    if (myNumber!= null) result.append("number:").append(myNumber).append(", ");
    if (myUser!= null) result.append("user:").append(myUser).append(", ");
    if (myPersonal!= null) result.append("personal:").append(myPersonal).append(", ");
    if (myCanceled!= null) result.append("canceled:").append(myCanceled).append(", ");
    if (myRunning!= null) result.append("running:").append(myRunning).append(", ");
    if (myPinned!= null) result.append("pinned:").append(myPinned).append(", ");
    if (myTags!= null) result.append("tag:").append(myTags).append(", ");
    if (myBranchLocator != null) result.append("branchLocator:").append(myBranchLocator).append(", ");
    if (myAgentName!= null) result.append("agentName:").append(myAgentName).append(", ");
    if (myParameterCondition!= null) result.append("parameterCondition:").append(myParameterCondition).append(", ");
    if (mySince!= null) result.append("since:").append(mySince).append(", ");
    if (myUntil!= null) result.append("until:").append(myUntil).append(", ");
    if (myStart!= null) result.append("start:").append(myStart).append(", ");
    if (myCount!= null) result.append("count:").append(myCount);
    if (myLookupLimit!= null) result.append("lookupLimit:").append(myLookupLimit);
    result.append(")");
    return result.toString();
  }
}
