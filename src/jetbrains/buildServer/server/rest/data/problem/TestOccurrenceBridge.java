package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.server.rest.data.investigations.ItemBridge;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STestManager;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.13
 */
public class TestOccurrenceBridge extends ItemBridge<STestRun> {
  @NotNull private final STestManager myTestManager;

  public TestOccurrenceBridge(@NotNull final ProjectManager projectManager, final @NotNull STestManager testManager) {
    myTestManager = testManager;
  }

  @Nullable
  public STestRun findTest(final @NotNull Long testNameId, final @NotNull SBuild build) {
    final List<STestRun> allTests = build.getFullStatistics().getAllTests();
    for (STestRun test : allTests) {
      if (testNameId == test.getTest().getTestNameId()) return test; //todo: does this support multiple test runs???
    }
    return null;
  }

  @NotNull
  @Override
  public List<STestRun> getAllItems() {
    throw new IllegalStateException("Sorry, listing tests is not implemented yet");
  }

  @Nullable
  public STestRun findTest(final Long testRunId) {
    throw new InvalidStateException("Searching by test run id is not yet supported"); //todo: (TeamCity) how to implement this?
  }
}
