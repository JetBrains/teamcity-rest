package jetbrains.buildServer.server.rest.model.pages.problems;


import java.util.List;
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntriesTreeCollector;
import jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry;
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.tree.AbstractLeaf;
import jetbrains.buildServer.server.rest.model.tree.AbstractNode;
import jetbrains.buildServer.server.rest.model.tree.AbstractScopeTree;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "projectBuildProblemsTree")
@XmlType(name = "projectBuildProblemsTree")
@XmlSeeAlso({BuildProblemEntriesTree.NodeImpl.class, BuildProblemEntriesTree.Leaf.class})
public class BuildProblemEntriesTree
  extends AbstractScopeTree<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters, BuildProblemEntriesTree.NodeImpl, BuildProblemEntriesTree.Leaf> {

  public BuildProblemEntriesTree() {
    super();
  }

  public BuildProblemEntriesTree(@NotNull List<Node<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters>> data,
                                 @NotNull Fields fields,
                                 @NotNull BeanContext beanContext) {
    super(data, fields, beanContext);
  }

  @XmlElement(name = "node")
  @Override
  public List<NodeImpl> getNodes() {
    return super.getNodes();
  }

  @XmlElement(name = "leaf")
  @Override
  public List<BuildProblemEntriesTree.Leaf> getLeafs() {
    return super.getLeafs();
  }


  @Override
  protected NodeImpl buildNode(@NotNull Node<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters> source, @NotNull Fields fields) {
    return new NodeImpl(source, fields);
  }

  @Override
  protected Leaf buildLeaf(@NotNull Node<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters> source, @NotNull Fields fields, @NotNull BeanContext context) {
    return new Leaf(source, fields, context);
  }

  @XmlType(name = "projectBuildProblemsTreeNode")
  public static class NodeImpl extends AbstractNode<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters> {
    public NodeImpl() {
      super();
    }

    public NodeImpl(@NotNull Node<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters> source, @NotNull Fields fields) {
      super(source, fields);
    }

    @XmlElement(name = "problemCount")
    public Integer getCounters() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("problemCount", true, true), myNode.getCounters().getCount());
    }

    @XmlAttribute(name = "type")
    public String getType() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("type"), ((BuildProblemEntriesTreeCollector.NodeScope) myNode.getScope()).getType().name());
    }
  }

  @XmlType(name = "projectBuildProblemsTreeLeaf")
  public static class Leaf extends AbstractLeaf<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters> {
    public Leaf() {
      super();
    }

    public Leaf(@NotNull Node<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters> node, @NotNull Fields fields, @NotNull BeanContext beanContext) {
      super(node, fields, beanContext);
    }

    @XmlElement(name = "problemEntries")
    public BuildProblemEntries getProblemEntries() {
      return ValueWithDefault.decideDefault(
        myFields.isIncluded("problemEntries", true, true),
        () -> new BuildProblemEntries(myNode.getData(), myFields.getNestedField("problemEntries"), myContext)
      );
    }
  }
}
