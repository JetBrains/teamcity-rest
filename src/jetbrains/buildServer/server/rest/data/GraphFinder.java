/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 15/06/2015
 */
public class GraphFinder<T> extends AbstractFinder<T> {

  protected static final String DIMENSION_FROM = "from";
  protected static final String DIMENSION_TO = "to";
  protected static final String DIMENSION_RECURSIVE = "recursive";
  protected static final String DIMENSION_INCLUDE_INITIAL = "includeInitial";
  private final AbstractFinder<T> myFinder;
  private final Traverser<T> myTraverser;

  public GraphFinder(@NotNull AbstractFinder<T> finder, Traverser<T> traverser) {
    super(new String[]{DIMENSION_FROM, DIMENSION_TO, DIMENSION_RECURSIVE, DIMENSION_INCLUDE_INITIAL});
    myFinder = finder;
    myTraverser = traverser;
  }

  @Nullable
  @Override
  public ItemHolder<T> getAllItems() {
    return null;
  }

  @NotNull
  @Override
  protected ItemFilter<T> getFilter(@NotNull final Locator locator) {
    return new MultiCheckerFilter<T>();
  }

  @NotNull
  @Override
  protected ItemHolder<T> getPrefilteredItems(@NotNull final Locator locator) {
    Boolean recursive = locator.getSingleDimensionValueAsBoolean(DIMENSION_RECURSIVE, true);
    if (recursive == null) recursive = true;

    Boolean includeOriginal = locator.getSingleDimensionValueAsBoolean(DIMENSION_INCLUDE_INITIAL, false);
    if (includeOriginal == null) includeOriginal = false;

    Set<T> resultTo = new LinkedHashSet<T>();
    final String toItemsDimension = locator.getSingleDimensionValue(DIMENSION_TO);
    if (toItemsDimension != null) {
      final List<T> toItems = myFinder.getItems(toItemsDimension).myEntries;
      if (includeOriginal) {
        resultTo.addAll(toItems);
      }
      collectLinked(resultTo, toItems, myTraverser.getChildren(), recursive);
    }

    Set<T> resultFrom = new LinkedHashSet<T>();
    final String fromItemsDimension = locator.getSingleDimensionValue(DIMENSION_FROM);
    if (fromItemsDimension != null) {
      final List<T> toItems = myFinder.getItems(fromItemsDimension).myEntries;
      if (includeOriginal) {
        resultFrom.addAll(toItems);
      }
      collectLinked(resultFrom, toItems, myTraverser.getParents(), recursive);
    }

    ArrayList<T> result;
    if (!resultTo.isEmpty() && !resultFrom.isEmpty()) {
      result = new ArrayList<T>(CollectionsUtil.intersect(resultTo, resultFrom));
    } else {
      result = new ArrayList<T>(!resultTo.isEmpty() ? resultTo : resultFrom);
    }
    return getItemHolder(result);
  }

  private void collectLinked(@NotNull final Collection<T> result, @NotNull Collection<T> toProcess, @NotNull LinkRetriever<T> linkRetriever, final boolean recursive) {
    while (!toProcess.isEmpty()) {
      Set<T> linkedItems = new LinkedHashSet<T>();
      for (T item : toProcess) {
        linkedItems.addAll(linkRetriever.getLinked(item));
      }
      result.addAll(linkedItems);
      toProcess = linkedItems;
      if (!recursive) break;
    }
  }

  public interface Traverser<S> {
    @NotNull
    LinkRetriever<S> getChildren();

    @NotNull
    LinkRetriever<S> getParents();
  }

  public interface LinkRetriever<S> {
    @NotNull
    List<S> getLinked(@NotNull S item);
  }
}
