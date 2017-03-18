/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.Date;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17/03/2017
 */
public abstract class BranchData implements Branch {
  public static BranchData fromBranch(@NotNull final Branch branch) {
    return new BranchData(branch.getName()) {
      @NotNull
      @Override
      public String getDisplayName() {
        return branch.getDisplayName();
      }

      @Override
      public boolean isDefaultBranch() {
        return branch.isDefaultBranch();
      }
    };
  }

  public static BranchData fromBranchEx(@NotNull final BranchEx branch, @NotNull final ServiceLocator serviceLocator) {
    return new BranchData(branch.getName()) {
      @NotNull
      @Override
      public String getDisplayName() {
        return branch.getDisplayName();
      }

      @Override
      public boolean isDefaultBranch() {
        return branch.isDefaultBranch();
      }

      @NotNull
      public Boolean isActive() {
        return branch.isActive();
      }

      @Nullable
      public Date getActivityTimestamp() {
        return branch.getTimestamp();
      }

      @NotNull
      @Override
      public List<ChangeDescriptor> getChanges(@NotNull final SelectPrevBuildPolicy prevBuildPolicy, @Nullable final Boolean includeDependencyChanges) {
        return branch.getDetectedChanges(prevBuildPolicy, includeDependencyChanges);
      }

      @NotNull
      @Override
      public SBuildType getBuildType() {
        return branch.getDummyBuildPromotion().getBuildType(); //TeamCity API issue: would be much more effective to get the build type directly
      }

      @NotNull
      @Override
      public PagedSearchResult<BuildPromotion> getBuilds(@Nullable final String locator) {
        BuildPromotionFinder buildPromotionFinder = serviceLocator.getSingletonService(BuildPromotionFinder.class);
        return buildPromotionFinder.getItems(Locator.setDimensionIfNotPresent(
          BuildPromotionFinder.getLocator(getBuildType(), branch, locator), PagerData.COUNT, String.valueOf(1)));
      }
    };
  }

  public static BranchData mergeSameNamed(@NotNull final BranchData b1, @NotNull final BranchData b2) {
    if (b1 == b2) {
      return b1;
    }

    if (!b1.getName().equals(b2.getName())) {
      throw new OperationException(getMergeConflictMessage(b1, b2, "with different name"));
    }
    if (b1.isDefaultBranch() != b2.isDefaultBranch()) {
      //should never happen as default branch should have "<default>"
      throw new OperationException(getMergeConflictMessage(b1, b2, "with different default state"));
    }

    return new BranchData("") {
      @NotNull
      @Override
      public String getName() {
        return b1.getName();
      }

      @NotNull
      @Override
      public String getDisplayName() {
        String b1_displayName = b1.getDisplayName();
        String b2_displayName = b2.getDisplayName();
        if (b1_displayName.equals(b2_displayName)) return b1_displayName;
        if (b1.isDefaultBranch()) return Branch.DEFAULT_BRANCH_NAME;
        throw new OperationException(getMergeConflictMessage(b1, b2, "with different default state"));
      }

      @Override
      public boolean isDefaultBranch() {
        return b1.isDefaultBranch();
      }

      @Override
      public boolean isUnspecifiedBranch() {
        return Branch.UNSPECIFIED_BRANCH_NAME.equals(b1.getName());
      }

      @Override
      public Boolean isActive() {
        Boolean active1 = b1.isActive();
        Boolean active2 = b2.isActive();
        return (active1 != null && active1) || (active2 != null && active2);
      }

      @Nullable
      @Override
      public Date getActivityTimestamp() {
        if (b1.getActivityTimestamp() == null) return b2.getActivityTimestamp();
        if (b2.getActivityTimestamp() == null) return b1.getActivityTimestamp();
        if (b1.getActivityTimestamp().after(b2.getActivityTimestamp())) return b1.getActivityTimestamp();
        return b2.getActivityTimestamp();
      }

      // merged branches do not support these kind of details

      @Nullable
      @Override
      public SBuildType getBuildType() {
        return null;
      }

      @Nullable
      @Override
      public PagedSearchResult<BuildPromotion> getBuilds(@Nullable final String locator) {
        return null;
      }
    };
  }

  public static BranchData fromBuild(@NotNull final BuildPromotion build) {
    Branch branch = build.getBranch();
    if (branch == null) return NOT_BRANCHED_BUILD;
    return new BranchData(branch.getName()) {
      @NotNull
      @Override
      public String getDisplayName() {
        return branch.getDisplayName();}

      @Override
      public boolean isDefaultBranch() {
        return branch.isDefaultBranch();
      }

      @Nullable
      @Override
      public Date getActivityTimestamp() {
        return build.getServerStartDate();
      }
    };
  }

  public static boolean isBranched(@NotNull BranchData d) {
    return NOT_BRANCHED_BUILD != d;
  }
  private static final BranchData NOT_BRANCHED_BUILD = new BranchData("<not_branched>") {
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


  @NotNull
  private static String getMergeConflictMessage(final @NotNull BranchData b1, final @NotNull BranchData b2, @NotNull final String details) {
    return "While merging branches, found branches " + details + ". Please report to JetBrains." +
           " 1: " + b1.getName() + "/" + b1.getDisplayName() + "/" + b1.isDefaultBranch() +
           ", 2: " + b2.getName() + "/" + b2.getDisplayName() + "/" + b2.isDefaultBranch();
  }

  @NotNull private final String myBranchName;

  public BranchData(@NotNull final String branchName) {
    myBranchName = branchName;
  }

  @NotNull
  @Override
  public String getName() {
    return myBranchName;
  }

  @NotNull
  @Override
  public abstract String getDisplayName();

  @Override
  public abstract boolean isDefaultBranch();

  public boolean isUnspecifiedBranch() {
    return Branch.UNSPECIFIED_BRANCH_NAME.equals(myBranchName);
  }

  /**
   * @return null for build branch
   */
  @Nullable
  public Boolean isActive() {
    return null;
  }

  @Nullable
  public Date getActivityTimestamp() {
    return null;
  }

  /**
   * @return associated build type, null if the operation is not supported for the branch
   */
  @Nullable
  public SBuildType getBuildType() {
    return null;
  }


  /**
   * @return null if the operation is not supported for the branch
   */
  @Nullable
  public PagedSearchResult<BuildPromotion> getBuilds(@Nullable String locator) {
    return null;
  }

  @NotNull
  public List<ChangeDescriptor> getChanges(@NotNull SelectPrevBuildPolicy prevBuildPolicy,
                                            @Nullable Boolean includeDependencyChanges) {
    //todo: implement in more places and use
    throw new OperationException("Should not be called");
  }
}
