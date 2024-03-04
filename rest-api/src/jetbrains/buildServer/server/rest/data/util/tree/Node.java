package jetbrains.buildServer.server.rest.data.util.tree;

import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Node<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  @NotNull
  String getId();

  @NotNull
  Scope getScope();

  @NotNull
  COUNTERS getCounters();

  @NotNull
  List<DATA> getData();

  @NotNull
  Collection<Node<DATA, COUNTERS>> getChildren();

  @Nullable
  Node<DATA, COUNTERS> getChild(@NotNull String childScopeName);

  @Nullable
  Node<DATA, COUNTERS> getParent();

  void mergeCounters(@NotNull COUNTERS counters);

  void putChild(@NotNull Node<DATA, COUNTERS> child);
}
