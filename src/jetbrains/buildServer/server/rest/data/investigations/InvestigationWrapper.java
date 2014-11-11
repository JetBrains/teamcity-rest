/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.investigations;

import java.util.Date;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.BuildType;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.BuildTypeResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 10.11.13
 */
public class InvestigationWrapper implements ResponsibilityEntry, Comparable<InvestigationWrapper>{
  public static final String TEST_TYPE = "test";
  public static final String PROBLEM_TYPE = "problem";
  public static final String ANY_PROBLEM_TYPE = "anyProblem";
  public static final String UNKNOWN_TYPE = "unknown";

  @NotNull private final ResponsibilityEntry myRE;
  private final BuildTypeResponsibilityEntry myBuildTypeRE;
  private final TestNameResponsibilityEntry myTestRE;
  private final BuildProblemResponsibilityEntry myProblemRE;

  public InvestigationWrapper(@NotNull BuildTypeResponsibilityEntry entry) {
    myRE = entry;
    myBuildTypeRE = entry;
    myTestRE = null;
    myProblemRE = null;
  }

  public InvestigationWrapper(@NotNull TestNameResponsibilityEntry entry) {
    myRE = entry;
    myBuildTypeRE = null;
    myTestRE = entry;
    myProblemRE = null;
  }

  public InvestigationWrapper(@NotNull BuildProblemResponsibilityEntry entry) {
    myRE = entry;
    myBuildTypeRE = null;
    myTestRE = null;
    myProblemRE = entry;
  }

  public boolean isBuildType() {
    return myBuildTypeRE != null;
  }

  public boolean isTest() {
    return myTestRE != null;
  }

  public boolean isProblem() {
    return myProblemRE != null;
  }

  public String getType() {
    if (isBuildType()) return ANY_PROBLEM_TYPE;
    if (isTest()) return TEST_TYPE;
    if (isProblem()) return PROBLEM_TYPE;
    return UNKNOWN_TYPE;
  }

  @NotNull
  public ResponsibilityEntry getRE() {
    return myRE;
  }


  @Nullable
  public BuildTypeResponsibilityEntry getBuildTypeRE() {
    return myBuildTypeRE;
  }

  @Nullable
  public TestNameResponsibilityEntry getTestRE() {
    return myTestRE;
  }

  @Nullable
  public BuildProblemResponsibilityEntry getProblemRE() {
    return myProblemRE;
  }


  @SuppressWarnings("ConstantConditions")
  @NotNull
  public String getId() {
    return InvestigationFinder.getLocator(this);
  }

  @NotNull
  public State getState() {
    return myRE.getState();
  }

  @NotNull
  public User getResponsibleUser() {
    return myRE.getResponsibleUser();
  }

  @Nullable
  public User getReporterUser() {
    return myRE.getReporterUser();
  }

  @NotNull
  public Date getTimestamp() {
    return myRE.getTimestamp();
  }

  @NotNull
  public String getComment() {
    return myRE.getComment();
  }

  @NotNull
  public RemoveMethod getRemoveMethod() {
    return myRE.getRemoveMethod();
  }

  @SuppressWarnings("ConstantConditions")
  @Nullable
  public BuildProject getAssignmentProject() {
    if (isProblem()){
      return getProblemRE().getProject();
    }
    if (isTest()){
      return getTestRE().getProject();
    }
    return null;
  }

  @SuppressWarnings("ConstantConditions")
  @Nullable
  public BuildType getAssignmentBuildType() {
    if (isBuildType()){
      return getBuildTypeRE().getBuildType();
    }
    return null;
  }

  //todo: review all methods below
  @SuppressWarnings("ConstantConditions")
  public int compareTo(@NotNull final InvestigationWrapper o) {
    if (myBuildTypeRE != null && o.myBuildTypeRE == null) return 1;
    if (myBuildTypeRE == null && o.myBuildTypeRE != null) return -1;
    if (myBuildTypeRE != null && o.myBuildTypeRE != null) {
      final int result = myBuildTypeRE.getBuildType().compareTo(o.myBuildTypeRE.getBuildType());
      if (result != 0) {
        return result;
      }
      return compareDetails(myRE, o.myRE);
    }

    if (myProblemRE != null && o.myProblemRE == null) return 1;
    if (myProblemRE == null && o.myProblemRE != null) return -1;
    if (myProblemRE != null && o.myProblemRE != null) {
      final int result = myProblemRE.getBuildProblemInfo().getId() - o.myProblemRE.getBuildProblemInfo().getId();
      if (result != 0) {
        return result;
      }
      return compareDetails(myRE, o.myRE);
    }

    if (myTestRE != null && o.myTestRE == null) return 1;
    if (myTestRE == null && o.myTestRE != null) return -1;
    if (myTestRE != null && o.myTestRE != null) {
      final int result = (int)(myTestRE.getTestNameId() - o.myTestRE.getTestNameId());
      if (result != 0) {
        return result;
      }
      return compareDetails(myRE, o.myRE);
    }

    throw new OperationException("Error in InvestigationWrapper comparator. Contact TeamCity develoeprs");
  }

  private int compareDetails(@NotNull final ResponsibilityEntry a, @NotNull final ResponsibilityEntry b) {
    if (a == b) return 0;

    if (!a.getComment().equals(b.getComment())) return a.getComment().compareTo(b.getComment());
    if (a.getRemoveMethod() != b.getRemoveMethod()) return a.getRemoveMethod().compareTo(b.getRemoveMethod());
    if (a.getReporterUser() != null) {
      if (!a.getReporterUser().equals(b.getReporterUser())) return b.getReporterUser() != null ? (int)(a.getReporterUser().getId() - b.getReporterUser().getId()) : 1;
    } else {
      if (b.getReporterUser() != null) return (int)(a.getReporterUser().getId() - b.getReporterUser().getId());
    }
    if (!a.getResponsibleUser().equals(b.getResponsibleUser())) return (int)(a.getResponsibleUser().getId() - b.getResponsibleUser().getId());
    if (a.getState() != b.getState()) return a.getState().compareTo(b.getState());
    if (!a.getTimestamp().equals(b.getTimestamp())) return a.getTimestamp().compareTo(b.getTimestamp());

    return 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final InvestigationWrapper investigationWrapper = (InvestigationWrapper)o;

    return compareTo(investigationWrapper) == 0;
  }

  @Override
  public int hashCode() {
    int result;

    if (myBuildTypeRE != null) {
      result = hashCodeBT(myBuildTypeRE);
    } else if (myProblemRE != null) {
      result = hashCodeP(myProblemRE);
    } else {
      result = hashCodeT(myTestRE);
    }
    result = 31 * result + hashCode(myRE);
    return result;
  }

  public int hashCode(@NotNull final ResponsibilityEntry a) {
    int result = a.getState().hashCode();
    result = 31 * result + a.getResponsibleUser().hashCode();
    result = 31 * result + (a.getReporterUser() != null ? a.getReporterUser().hashCode() : 0);
    result = 31 * result + a.getTimestamp().hashCode();
    result = 31 * result + a.getComment().hashCode();
    result = 31 * result + a.getRemoveMethod().hashCode();
    return result;
  }

  public int hashCodeBT(@NotNull final BuildTypeResponsibilityEntry a) {
    return a.getBuildType().getBuildTypeId().hashCode();
  }

  public int hashCodeP(@NotNull final BuildProblemResponsibilityEntry a) {
    int result = a.getBuildProblemInfo().getId();
    result = 31 * result + a.getProject().getProjectId().hashCode();
    return result;
  }

  public int hashCodeT(@NotNull final TestNameResponsibilityEntry a) {
    int result = a.getTestName().getAsString().hashCode();
    result = 31 * result + a.getProject().getProjectId().hashCode();
    return result;
  }
}
