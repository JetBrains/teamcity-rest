package jetbrains.buildServer.server.rest.data.pages.problems;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntriesTreeCollector.Counters;
import jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntriesTreeCollector.NodeComparator;
import jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntriesTreeCollector.NodeScope;
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import jetbrains.buildServer.server.rest.data.util.tree.Scope;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.impl.ProjectVisibilityHolderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class BuildProblemEntryTreeNodeComparatorTest extends BaseServerTestCase {

  private ProjectVisibilityHolderImpl myVisibilityHolder;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    SUser user = myFixture.createUserAccount("myordereduser");
    myVisibilityHolder = new ProjectVisibilityHolderImpl(user, myFixture.getUserDataLoader(), myFixture.getUserUpdate(), myProjectManager);
  }

  public void testUserProjectOrder() {
    ProjectEx p1 = myProject.createProject("p1", "P1");
    ProjectEx p2 = myProject.createProject("p2", "P2");
    ProjectEx p3 = myProject.createProject("p3", "P3");
    
    myVisibilityHolder.setProjectsOrder(Arrays.asList(p2.getProjectId(), p1.getProjectId()));

    NodeScope scope1 = new NodeScope(p1);
    NodeScope scope2 = new NodeScope(p2);
    NodeScope scope3 = new NodeScope(p3);

    Node<BuildProblemEntry, Counters> node1 = new FakeNode(scope1);
    Node<BuildProblemEntry, Counters> node2 = new FakeNode(scope2);
    Node<BuildProblemEntry, Counters> node3 = new FakeNode(scope3);

    NodeComparator comparator = new NodeComparator(myVisibilityHolder.getUserProjectOrder(), myVisibilityHolder.getUserBuildTypeOrder());

    assertTrue("Project 2 should come first according to user preference.", comparator.compare(node2, node1) < 0);
    assertTrue("Project 3 should come last because it's not ordered by user.", comparator.compare(node1, node3) < 0);
    assertTrue("Project 3 should come last because it's not ordered by user.", comparator.compare(node2, node3) < 0);
  }

  public void testUserBuildTypeOrder() {
    BuildTypeEx bt1 = myProject.createBuildType("bt1");
    BuildTypeEx bt2 = myProject.createBuildType("bt2");
    BuildTypeEx bt3 = myProject.createBuildType("bt3");

    myVisibilityHolder.setBuildTypesOrder(myProject, Arrays.asList(bt2, bt1), Arrays.asList(bt3));

    NodeScope scope1 = new NodeScope(bt1);
    NodeScope scope2 = new NodeScope(bt2);
    NodeScope scope3 = new NodeScope(bt3);

    Node<BuildProblemEntry, Counters> node1 = new FakeNode(scope1);
    Node<BuildProblemEntry, Counters> node2 = new FakeNode(scope2);
    Node<BuildProblemEntry, Counters> node3 = new FakeNode(scope3);

    NodeComparator comparator = new NodeComparator(myVisibilityHolder.getUserProjectOrder(), myVisibilityHolder.getUserBuildTypeOrder());

    assertTrue("Bt 2 should come first according to user preference.", comparator.compare(node2, node1) < 0);
    assertTrue("Bt 3 should come last because it's itnended to be less visible by the user.", comparator.compare(node1, node3) < 0);
    assertTrue("Bt 3 should come last because it's itnended to be less visible by the user.", comparator.compare(node2, node3) < 0);
  }

  public void testBuildTypeAlwaysBeforeProject() {
    BuildTypeEx bt1 = myProject.createBuildType("bt1");
    BuildTypeEx bt2 = myProject.createBuildType("bt2");
    ProjectEx p1 = myProject.createProject("p1", "p1");

    myVisibilityHolder.setBuildTypesOrder(myProject, Arrays.asList(bt2), Arrays.asList(bt1));

    NodeScope scopeBt1 = new NodeScope(bt1);
    NodeScope scopeBt2 = new NodeScope(bt2);
    NodeScope scopeP = new NodeScope(p1);

    Node<BuildProblemEntry, Counters> nodeBt1 = new FakeNode(scopeBt1);
    Node<BuildProblemEntry, Counters> nodeBt2 = new FakeNode(scopeBt2);
    Node<BuildProblemEntry, Counters> nodeP = new FakeNode(scopeP);

    NodeComparator comparator = new NodeComparator(myVisibilityHolder.getUserProjectOrder(), myVisibilityHolder.getUserBuildTypeOrder());

    assertTrue("Any build type should come in front of a project.", comparator.compare(nodeBt1, nodeP) < 0);
    assertTrue("Any build type should come in front of a project.", comparator.compare(nodeBt2, nodeP) < 0);
    assertTrue("Bt 1 should come second because it's itnended to be less visible by the user.", comparator.compare(nodeBt2, nodeBt1) < 0);
  }

  private class FakeNode implements Node<BuildProblemEntry, Counters> {
    private final NodeScope myScope;

    public FakeNode(NodeScope scope) {
      myScope = scope;
    }

    @NotNull
    @Override
    public String getId() {
      return "fake_" + myScope;
    }

    @NotNull
    @Override
    public Scope getScope() {
      return myScope;
    }

    @NotNull
    @Override
    public Counters getCounters() {
      return new Counters(0);
    }

    @NotNull
    @Override
    public List<BuildProblemEntry> getData() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<Node<BuildProblemEntry, Counters>> getChildren() {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public Node<BuildProblemEntry, Counters> getChild(@NotNull String childScopeName) {
      return null;
    }

    @Nullable
    @Override
    public Node<BuildProblemEntry, Counters> getParent() {
      return null;
    }

    @Override
    public void mergeCounters(@NotNull Counters counters) {

    }

    @Override
    public void putChild(@NotNull Node<BuildProblemEntry, Counters> child) {

    }
  }
}
