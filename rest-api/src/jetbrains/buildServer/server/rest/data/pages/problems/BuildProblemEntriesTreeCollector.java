package jetbrains.buildServer.server.rest.data.pages.problems;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.locator.*;
import jetbrains.buildServer.server.rest.data.locator.definition.LocatorDefinition;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.data.util.tree.*;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.util.VirtualBuildsUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.impl.UserEx;
import jetbrains.buildServer.util.impl.Lazy;
import jetbrains.buildServer.web.problems.BuildProblemsBean;
import jetbrains.buildServer.web.util.UserBuildTypeOrder;
import jetbrains.buildServer.web.util.UserProjectOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@JerseyInjectable
@Component
public class BuildProblemEntriesTreeCollector {
  private static final Orders<Node<BuildProblemEntry, Counters>> SUPPORTED_ORDERS = new Orders<Node<BuildProblemEntry, Counters>>()
    .add("count", Comparator.comparingInt(node -> node.getCounters().getCount()));
  private static final String MISSING_BT_ID = "$$MISSING$$";

  public static class Definition implements LocatorDefinition {
    public static final Dimension MAX_CHILDREN = Dimension.ofName("maxChildren")
                                                          .description("Maximum children in nodes of the returned tree.")
                                                          .syntax(PlainValue.int64()).build();
    public static final Dimension ORDER_BY = Dimension.ofName("orderBy")
                                                      .description("In which order to sort nodes of the tree.")
                                                      .syntax(SUPPORTED_ORDERS.getSyntax())
                                                      .build();
    public static final Dimension SUB_TREE_ROOT_ID = Dimension.ofName("subTreeRootId")
                                                              .description("") // TODO
                                                              .syntax(PlainValue.string())
                                                              .build();
  }
  private final BuildProblemEntriesFinder myBuildProblemEntriesFinder;

  public BuildProblemEntriesTreeCollector(@NotNull BuildProblemEntriesFinder buildProblemEntriesFinder,
                                          @NotNull ProjectManager projectManager,
                                          @NotNull SecurityContext securityContext) {
    myBuildProblemEntriesFinder = buildProblemEntriesFinder;
    SUPPORTED_ORDERS.add("userPreference", () -> {
      AuthorityHolder holder = securityContext.getAuthorityHolder();
      if(holder instanceof UserEx) {
        UserEx user = (UserEx) holder;
        return new NodeComparator(new UserProjectOrder(user), new UserBuildTypeOrder(user));
      }

      // Default to system-wide order of projects and build types.
      return new NodeComparator(projectManager.getProjectsComparator(), projectManager.getBuildTypesComparator());
    });
  }

  @NotNull
  public List<Node<BuildProblemEntry, Counters>> getTree(@NotNull Locator locator) {
    TreeSlicingOptions<BuildProblemEntry, Counters> slicingOptions = getSlicingOptions(locator);

    String subtree = locator.getSingleDimensionValue(Definition.SUB_TREE_ROOT_ID);
    if(subtree == null) {
      subtree = NodeScope.ROOT_PROJECT_ID;
    }

    ScopeTree<BuildProblemEntry, Counters> tree = getTreeByLocator(locator);

    return tree.getFullNodeAndSlicedOrderedSubtree(subtree, slicingOptions);
  }

  @NotNull
  private TreeSlicingOptions<BuildProblemEntry, Counters> getSlicingOptions(@NotNull Locator locator) {
    String order = locator.getSingleDimensionValue(Definition.ORDER_BY);
    return new TreeSlicingOptions<BuildProblemEntry, Counters>(
      locator.getSingleDimensionValueAsLong(Definition.MAX_CHILDREN, 5L).intValue(),
      Comparator.comparing(bpe -> bpe.getProblem().getId()),
      SUPPORTED_ORDERS.getComparator(order != null ? order : "userPreference:asc")
    );
  }

  @NotNull
  private ScopeTree<BuildProblemEntry, Counters> getTreeByLocator(@NotNull Locator fullLocator) {
    String problemsLocator = getLocatorForProblemEntries(fullLocator);

    Stream<BuildProblemEntry> filteredProblems = myBuildProblemEntriesFinder.getItems(problemsLocator).getEntries().stream();

    List<LeafInfo<BuildProblemEntry, Counters>> problems = groupProblems(filteredProblems);

    return new ScopeTree<>(
      new NodeScope(NodeScope.ROOT_PROJECT_ID, SProject.ROOT_PROJECT_ID, NodeScopeType.PROJECT),
      new Counters(0),
      problems
    );
  }

  @NotNull
  private String getLocatorForProblemEntries(@NotNull Locator fullLocator) {
    return fullLocator.getUnusedDimensions().stream()
                      .map(dimensionName -> String.format("%s:(%s)", dimensionName, fullLocator.get(dimensionName)))
                      .collect(Collectors.joining(","));
  }

  @NotNull
  private List<LeafInfo<BuildProblemEntry, Counters>> groupProblems(@NotNull Stream<BuildProblemEntry> buildProblemEntries) {
    Map<String, List<BuildProblemEntry>> groupedProblemEntries = buildProblemEntries
      .collect(Collectors.groupingBy(
        bpe -> getBuildTypeIdResolvingVirtual(bpe.getBuildPromotion()),
        Collectors.mapping(bpe -> bpe, Collectors.toList())
      ));

    return groupedProblemEntries.values().stream()
                                .map(bpes -> new GroupedProblems(bpes))
                                .collect(Collectors.toList());
  }

  @NotNull
  private String getBuildTypeIdResolvingVirtual(@NotNull BuildPromotion promotion) {
    BuildPromotion originalPromotion = VirtualBuildsUtil.getVirtualHead(promotion);

    BuildPromotion source = originalPromotion == null ? promotion : originalPromotion;

    SBuildType bt = source.getParentBuildType();
    return bt == null ? MISSING_BT_ID : bt.getExternalId();
  }

  public static class GroupedProblems implements LeafInfo<BuildProblemEntry, Counters> {
    private final List<BuildProblemEntry> myData;
    private final Lazy<List<Scope>> myPath;

    /**
     * All problems must be in the same buildType.
     */
    public GroupedProblems(@NotNull List<BuildProblemEntry> problems) {
      assert problems.size() > 0;

      myData = problems;
      myPath = Lazy.create(this::calculatePath);
    }

    @NotNull
    @Override
    public Counters getCounters() {
      return new Counters(myData.size());
    }

    @NotNull
    @Override
    public Iterable<Scope> getPath() {
      return myPath.get();
    }

    @NotNull
    private List<Scope> calculatePath() {
      List<Scope> path = new ArrayList<>();

      BuildPromotion promotionFromProblem = myData.get(0).getBuildPromotion();
      BuildPromotion virtualHead = VirtualBuildsUtil.getVirtualHead(promotionFromProblem);
      BuildPromotion source = virtualHead == null ? promotionFromProblem : virtualHead;

      SBuildType bt = source.getParentBuildType();

      if(bt == null) {
        path.add(new NodeScope(NodeScope.ROOT_PROJECT_ID, SProject.ROOT_PROJECT_ID, NodeScopeType.PROJECT));
        path.add(new NodeScope(encode("BT_" + MISSING_BT_ID), "NonExistingBt", NodeScopeType.BUILD_TYPE));
        return path;
      }

      for(SProject ancestor : bt.getProject().getProjectPath()) {
        path.add(new NodeScope(ancestor));
      }

      path.add(new NodeScope(bt));

      return path;
    }

    @NotNull
    @Override
    public Collection<BuildProblemEntry> getData() {
      return myData;
    }
  }

  public static class NodeScope implements Scope {
    private final NodeScopeType myType;
    private final String myName;
    private final String myId;
    private final SProject myProject;
    private final SBuildType myBuildType;

    public NodeScope(@NotNull SBuildType bt) {
      myType = NodeScopeType.BUILD_TYPE;
      myName = bt.getExternalId();
      myId = generateId(bt);
      myBuildType = bt;
      myProject = bt.getProject();
    }

    public NodeScope(@NotNull SProject project) {
      myType = NodeScopeType.PROJECT;
      myName = project.getExternalId();
      myProject = project;
      myBuildType = null;
      myId = generateId(project);
    }

    public NodeScope(@NotNull String id, @NotNull String name, @NotNull NodeScopeType type) {
      myType = type;
      myName = name;
      myId = id;
      myProject = null;
      myBuildType = null;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getId() {
      return myId;
    }

    @NotNull
    public NodeScopeType getType() {
      return myType;
    }

    @Nullable
    public SProject getProject() {
      return myProject;
    }

    @Nullable
    public SBuildType getBuildType() {
      return myBuildType;
    }

    @Override
    public boolean isLeaf() {
      return myType == NodeScopeType.BUILD_TYPE;
    }

    @NotNull
    public static String generateId(@NotNull SProject project) {
      return encode("P_" + project.getExternalId());
    }

    @NotNull
    public static String generateId(@NotNull SBuildType bt) {
      return encode("BT_" + bt.getExternalId());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      NodeScope nodeScope = (NodeScope)o;
      return Objects.equals(myId, nodeScope.myId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myId);
    }

    @Override
    public String toString() {
      return "NodeScope{type=" + myType + ", name='" + myName + "'}";
    }

    public static final String ROOT_PROJECT_ID = encode("P_" + SProject.ROOT_PROJECT_ID);
  }

  @NotNull
  private static String encode(@NotNull String s) {
    return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  public enum NodeScopeType {
    PROJECT, BUILD_TYPE
  }

  public static class Counters implements TreeCounters<Counters> {
    private final int myCount;

    public Counters(int count) {
      myCount = count;
    }

    public int getCount() {
      return myCount;
    }

    @Override
    public Counters combinedWith(@NotNull Counters other) {
      return new Counters(myCount + other.myCount);
    }
  }

  static class NodeComparator implements Comparator<Node<BuildProblemEntry, Counters>> {
    private final Comparator<SProject> myProjectComparator;
    private final Comparator<SBuildType> myBtComparator;

    public NodeComparator(Comparator<SProject> projectComparator, Comparator<SBuildType> btComparator) {
      myProjectComparator = projectComparator;
      myBtComparator = btComparator;
    }

    @Override
    public int compare(Node<BuildProblemEntry, Counters> node1, Node<BuildProblemEntry, Counters> node2) {
      NodeScope scope1 = ((NodeScope) node1.getScope());
      NodeScope scope2 = ((NodeScope) node2.getScope());

      if(scope1.getType() == scope2.getType()) {
        if(scope1.getType() == NodeScopeType.PROJECT) { // both are projects
          return myProjectComparator.compare(scope1.getProject(), scope2.getProject());
        }

        // both are buildTypes
        return myBtComparator.compare(scope1.getBuildType(), scope2.getBuildType());
      }

      // In case one is project and another one is a buildType, buildType always come first
      if (scope1.getType() == NodeScopeType.BUILD_TYPE && scope2.getType() == NodeScopeType.PROJECT) {
        return -1;
      }

      return 1;
    }
  }
}
