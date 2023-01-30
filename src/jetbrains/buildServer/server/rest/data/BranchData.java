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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
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

  public static BranchData fromBranchEx(@NotNull final BranchEx branch,
                                        @NotNull final ServiceLocator serviceLocator,
                                        @Nullable final Boolean overrideActive,
                                        final boolean disableActive) {
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

      @Override
      @Nullable
      public Boolean isActive() {
        if (disableActive) return null;
        return overrideActive != null ? overrideActive : branch.isActive(); //call to isActive can be expensive
      }

      @Override
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
        return branch.getBuildType();
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

  public static BranchData fromBranchGroup(@NotNull final BranchGroupsProvider.BranchGroup branchGroup) {
    return new BranchData(branchGroup.getId()) {
      @NotNull
      @Override
      public String getDisplayName() {
        return branchGroup.getName();
      }

      @Override
      public boolean isDefaultBranch() {
        return false;
      }

      @Override
      public boolean isGroup() {
        return true;
      }
    };
  }


  public static BranchData mergeSameNamed(@NotNull final BranchData b1, @NotNull final BranchData b2) {
    if (b1 == b2) {
      return b1;
    }

    if (b1 instanceof MergingBranchData) {
      return ((MergingBranchData)b1).add(b2);
    }
    if (b2 instanceof MergingBranchData) {
      return ((MergingBranchData)b2).add(b1);
    }
    return new MergingBranchData(b1, b2);
  }

  /**
   * Collects branches from given sourceBuilds and produces a list of unique branch names (taking default status into account).
   */
  @NotNull
  public static List<BranchData> distinctFromBuilds(@NotNull final Collection<SBuild> sourceBuilds) {
    Stream<BranchData> unmergeBranches = sourceBuilds.stream()
                                                     .filter(Objects::nonNull)
                                                     .map(SBuild::getBuildPromotion)
                                                     .map(buildPromotion -> BranchData.fromBuild(buildPromotion));

    // Group branches collected from builds by pair (name, isDefault) and merge them.
    // At this point we don't care that these branches may come from different repositories, as we only need this info to show branch labels in the UI.
    Map<MergingBranchData.MergeKey, BranchData> mergedBranchData = unmergeBranches.collect(
      Collectors.groupingBy(branchData -> new MergingBranchData.MergeKey(branchData), Collectors.reducing(null, (left, right) -> left == null ? right : left))
    );

    return new ArrayList<>(mergedBranchData.values());
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


  @Override
  public String toString() {
    return myBranchName;
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

  public boolean isGroup() {
    return false;
  }

  private static class MergingBranchData extends BranchData {
    private @NotNull final String myName;
    private final BranchData myFirstBranch;
    private final boolean myIsDefault;
    private Boolean myActive;
    private String myDisplayName;
    private Date myActivityTimestamp;

    public MergingBranchData(@NotNull final BranchData b1, @NotNull final BranchData b2) {
      super(StringUtil.EMPTY);
      myName = b1.getName();
      myIsDefault = b1.isDefaultBranch();
      myFirstBranch = b1;

      add(b1);
      add(b2);
    }

    public MergingBranchData add(@NotNull final BranchData b) {
      check(b);

      updateActiveState(b);
      updateActivityTimestamp(b);
      updateDisplayName(b);

      return this;
    }

    private void updateDisplayName(@NotNull BranchData b) {
      if (Branch.DEFAULT_BRANCH_NAME.equals(myDisplayName)) return;

      if (myDisplayName == null) {
        myDisplayName = b.getDisplayName();
      } else {
        String displayName = b.getDisplayName();
        if (!myDisplayName.equals(displayName)) {
          if (myIsDefault) {
            myDisplayName = Branch.DEFAULT_BRANCH_NAME;
          } else {
            throw new OperationException(getMergeConflictMessage(myFirstBranch, b, "with different display names"));
          }
        }
      }
    }

    private void updateActivityTimestamp(@NotNull BranchData b) {
      Date activityTime = b.getActivityTimestamp();
      if (activityTime != null) {
        if (myActivityTimestamp == null || myActivityTimestamp.before(activityTime)) {
          myActivityTimestamp = activityTime;
        }
      }
    }

    private void updateActiveState(@NotNull BranchData b) {
      if (myActive != null && myActive) return;

      Boolean active = b.isActive();
      if (myActive == null || active != null) {
        myActive = active;
      }
    }

    private void check(@NotNull final BranchData b) {
      if (!myName.equals(b.getName())) {
        throw new OperationException(getMergeConflictMessage(myFirstBranch, b, "with different name"));
      }
      if (myIsDefault != b.isDefaultBranch()) {
        //should never happen as default branch should have "<default>"
        throw new OperationException(getMergeConflictMessage(myFirstBranch, b, "with different default state"));
      }
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myDisplayName;
    }

    @Override
    public boolean isDefaultBranch() {
      return myIsDefault;
    }

    @Override
    public boolean isUnspecifiedBranch() {
      return Branch.UNSPECIFIED_BRANCH_NAME.equals(myName);
    }

    @Override
    public Boolean isActive() {
      return myActive;
    }

    @Nullable
    @Override
    public Date getActivityTimestamp() {
      return myActivityTimestamp;
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

    private static class MergeKey {
      private final boolean isDefault;
      private final String name;

      MergeKey(@NotNull BranchData branch) {
        isDefault = branch.isDefaultBranch();
        name = branch.getName();
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MergeKey that = (MergeKey)o;

        if (isDefault != that.isDefault) return false;
        return name.equals(that.name);
      }

      @Override
      public int hashCode() {
        int result = (isDefault ? 1 : 0);
        result = 31 * result + name.hashCode();
        return result;
      }
    }
  }
}
