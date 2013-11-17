package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.server.rest.data.investigations.ItemBridge;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.11.13
 */
public class TestBridge extends ItemBridge<STest> {
  @NotNull private final STestManager myTestManager;

  public TestBridge(@NotNull final ProjectManager projectManager, final @NotNull STestManager testManager) {
    myTestManager = testManager;
  }

  @NotNull
  public STest getTest(final long testNameId, final @NotNull String projectInternalId) {
    final STest test = findTest(testNameId, projectInternalId);
    if (test == null){
      throw new InvalidStateException("Cannot find test for responsibility entry. Test name id: '" + testNameId + "', project id: '" + projectInternalId + "'.");
    }
    return test;
  }

  @Nullable
  public STest findTest(final @NotNull Long testNameId, final @NotNull String projectInternalId) {
    return myTestManager.findTest(testNameId, projectInternalId);
  }

  @NotNull
  @Override
  public List<STest> getAllItems() {
    throw new IllegalStateException("Sorry, listing tests is not implemented yet");
  }

  @Nullable
  public STest findTestByName(final String testName) {
    //todo: how to do this via TeamCity API?
    throw new InvalidStateException("Sorry, searching test by name is not supported yet;");
  }
}
