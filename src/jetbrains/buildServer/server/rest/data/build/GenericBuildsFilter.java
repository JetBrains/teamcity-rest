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

package jetbrains.buildServer.server.rest.data.build;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BranchMatcher;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.data.RangeLimit;
import jetbrains.buildServer.server.rest.data.util.FilterUtil;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
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
  @NotNull private final BranchMatcher myBranchMatcher;
  @Nullable private final String myAgentName;
  @NotNull private final ServiceLocator myServiceLocator;
  @Nullable private final Set<SBuildAgent> myAgents;
  @Nullable private final RangeLimit mySince;
  @Nullable private final RangeLimit myUntil;
  @Nullable private final Long myLookupLimit;
  @Nullable private final ParameterCondition myParameterCondition;
  @Nullable private final SUser myUser;
  @Nullable private final SBuildType myBuildType;
  @Nullable private final SProject myProject;

  /**
   * @param buildType       build type to return builds from, can be null to return all builds
   * @param project         build type to return builds from, can be null
   * @param status          status of the builds to include
   * @param number          build number of the builds to include
   * @param user            limit builds to those triggered by user, can be null to return all builds
   * @param personal        if set, limits the builds by personal status (return only personal if "true", only non-personal if "false")
   * @param canceled        if set, limits the builds by canceled status (return only canceled if "true", only non-canceled if "false")
   * @param running         if set, limits the builds by running state (return only running if "true", only finished if "false")
   * @param pinned          if set, limits the builds by pinned status (return only pinned if "true", only non-pinned if "false")
   * @param branchMatcher   if not set, only builds from default branch match. The locator supports dimensions: "name"/String, "default"/boolean and "unspecified"/boolean.
   * @param agentName       limit builds to those ran on specified agent, can be null to return all builds
   * @param agents
   * @param parameterCondition  limit builds to those with a finish parameter matching the condition specified, can be null to return all builds
   * @param since           the RangeLimit to return only the builds since the limit. If contains build, it is not included, if contains the date, the builds that were started at and later then the date are included
   * @param until           the RangeLimit to return only the builds until the limit. If contains build, it is included, if contains the date, the builds that were started at and before the date are included
   * @param start           the index of the first build to return (begins with 0), 0 by default
   * @param count           the number of builds to return, all by default
   * @param lookupLimit     the number of builds to search. Matching results only within first 'lookupLimit' builds will be returned
   * @param serviceLocator
   */
  public GenericBuildsFilter(@Nullable final SBuildType buildType,
                             @Nullable final SProject project,
                             @Nullable final String status,
                             @Nullable final String number,
                             @Nullable final SUser user,
                             @Nullable final Boolean personal,
                             @Nullable final Boolean canceled,
                             @Nullable final Boolean running,
                             @Nullable final Boolean pinned,
                             @Nullable final List<String> tags,
                             @NotNull final BranchMatcher branchMatcher,
                             @Nullable final String agentName,
                             @Nullable final Collection<SBuildAgent> agents,
                             @Nullable final ParameterCondition parameterCondition,
                             @Nullable final RangeLimit since,
                             @Nullable final RangeLimit until,
                             @Nullable final Long start,
                             @Nullable final Integer count,
                             @Nullable final Long lookupLimit,
                             @NotNull final ServiceLocator serviceLocator) {
    myStart = start;
    myCount = count;

    myBuildType = buildType;
    myProject = project;
    myStatus = status;
    myNumber = number;
    myUser = user;
    myPersonal = personal;
    myCanceled = canceled;
    myRunning = running;
    myPinned = pinned;
    myTags = tags;
    myBranchMatcher = branchMatcher;
    myAgentName = agentName;
    myServiceLocator = serviceLocator;

    if (agents == null) {
      myAgents = null;
    } else {
      myAgents = new TreeSet<SBuildAgent>(new Comparator<SBuildAgent>() {
        public int compare(final SBuildAgent o1, final SBuildAgent o2) {
          if (o1.getId() == -1) {
            if (o2.getId() == -1) {
              return o1.getAgentTypeId() - o2.getAgentTypeId();
            } else {
              return -1;
            }
          }
          if (o2.getId() == -1) {
            return 1;
          }
          return o1.getId() - o2.getId();
        }
      });
      myAgents.addAll(agents);
    }

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
  public SProject getProject() {
    return myProject;
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
    if (myProject != null && !isUnderProject(myProject, build)) {
      return false;
    }
    if (myStatus != null && !myStatus.equalsIgnoreCase(build.getStatusDescriptor().getStatus().getText())) {
      return false;
    }
    if (myNumber != null && !myNumber.equals(build.getBuildNumber())) {
      return false;
    }
    if (!FilterUtil.isIncludedByBooleanFilter(myPersonal, build.isPersonal())) {
      return false;
    }
    if (!FilterUtil.isIncludedByBooleanFilter(myCanceled, build.getCanceledInfo() != null)) {
      return false;
    }
    if (!FilterUtil.isIncludedByBooleanFilter(myRunning, !build.isFinished())) {
      return false;
    }
    if (!FilterUtil.isIncludedByBooleanFilter(myPinned, build.isPinned())) {
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
    if (!myBranchMatcher.matches(build.getBuildPromotion())) {
      return false;
    } else {
      //default to only default branch
      if (!myBranchMatcher.isDefined()) {
        @Nullable final Branch buildBranch = build.getBuildPromotion().getBranch();
        if (buildBranch != null && !buildBranch.isDefaultBranch())
        return false;
      }
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
      if (myUntil.before(build)) {
        return false;
      }
    }

    if (myAgents != null && !myAgents.contains(build.getAgent())) {
      return false;
    }

    if (myParameterCondition != null){
      if (!myParameterCondition.matches(Build.getBuildResultingParameters(build.getBuildPromotion(), myServiceLocator))) {
        return false;
      }
    }
    return true;
  }

  private boolean isUnderProject(@NotNull final SProject project, @NotNull final SBuild build) {
    final String projectId = build.getProjectId();
    if (projectId == null){
      return false;
    }
    if (project.getProjectId().equals(projectId)){
      return true;
    }
    return CollectionsUtil.findFirst(project.getProjects(), data -> projectId.equals(data.getProjectId())) != null;
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

  public boolean isExcludedBySince(final SBuild build) {
    if (mySince != null) {
      if (!mySince.before(build)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("Builds filter (");
    if (myBuildType!= null) result.append("buildType:").append(myBuildType).append(", ");
    if (myProject!= null) result.append("project:").append(myProject.describe(false)).append(", ");
    if (myStatus!= null) result.append("status:").append(myStatus).append(", ");
    if (myNumber!= null) result.append("number:").append(myNumber).append(", ");
    if (myUser!= null) result.append("user:").append(myUser).append(", ");
    if (myPersonal!= null) result.append("personal:").append(myPersonal).append(", ");
    if (myCanceled!= null) result.append("canceled:").append(myCanceled).append(", ");
    if (myRunning!= null) result.append("running:").append(myRunning).append(", ");
    if (myPinned!= null) result.append("pinned:").append(myPinned).append(", ");
    if (myTags!= null) result.append("tag:").append(myTags).append(", ");
    if (myBranchMatcher.isDefined()) result.append("branchMatcher:").append(myBranchMatcher).append(", ");
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
