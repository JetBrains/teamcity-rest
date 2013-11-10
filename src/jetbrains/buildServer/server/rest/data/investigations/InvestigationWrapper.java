package jetbrains.buildServer.server.rest.data.investigations;

import java.util.Date;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.BuildTypeResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 10.11.13
 */
public class InvestigationWrapper implements ResponsibilityEntry {
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
    if (isBuildType()) return "BuildType";
    if (isTest()) return "Test";
    if (isProblem()) return "Problem";
    return "Unknown";
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
}
