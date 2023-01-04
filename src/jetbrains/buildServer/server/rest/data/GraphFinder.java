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

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 15/06/2015
 */
public class GraphFinder<T> extends AbstractFinder<T> {
  private static final Logger LOG = Logger.getInstance(GraphFinder.class.getName());

  protected static final String DIMENSION_FROM = "from";
  protected static final String DIMENSION_TO = "to";
  protected static final String DIMENSION_STOP = "stop";
  protected static final String DIMENSION_RECURSIVE = "recursive";
  protected static final String DIMENSION_INCLUDE_INITIAL = "includeInitial";
  private final LightweightFinder<T> myFinder;
  @NotNull private final Traverser<T> myTraverser;
  private Long myDefaultLookupLimit;

  public GraphFinder(@NotNull Finder<T> finder, @NotNull Traverser<T> traverser) {
    this(locatorText -> finder.getItems(locatorText).getEntries(), traverser);
  }

  public GraphFinder(@NotNull LightweightFinder<T> finder, @NotNull Traverser<T> traverser) {
    super(DIMENSION_FROM, DIMENSION_TO, DIMENSION_RECURSIVE, DIMENSION_INCLUDE_INITIAL, DIMENSION_STOP);
    myFinder = finder;
    myTraverser = traverser;
  }

  @NotNull
  @Override
  public String getName() {
    return myName != null ? myName : getClass().getSimpleName() + " with " + myTraverser.getClass().getSimpleName();
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final T t) {
    throw new OperationException("getItemLocator is not implemented in GraphFinder");
  }

  @NotNull
  @Override
  public ItemFilter<T> getFilter(@NotNull final Locator locator) {
    return new MultiCheckerFilter<T>();
  }

  @Nullable
  @Override
  public Long getDefaultLookupLimit() {
    return myDefaultLookupLimit;
  }

  public void setDefaultLookupLimit(final Long defaultLookupLimit) {
    myDefaultLookupLimit = defaultLookupLimit;
  }

  @NotNull
  @Override
  public ItemHolder<T> getPrefilteredItems(@NotNull final Locator locator) {
    boolean recursive = locator.getSingleDimensionValueAsStrictBoolean(DIMENSION_RECURSIVE, true);

    boolean includeOriginal = locator.getSingleDimensionValueAsStrictBoolean(DIMENSION_INCLUDE_INITIAL, false);

    final List<T> toItems = getItemsFromDimension(locator, DIMENSION_TO);
    final List<T> fromItems = getItemsFromDimension(locator, DIMENSION_FROM);
    final List<T> stopItems = getItemsFromDimension(locator, DIMENSION_STOP);
    Long lookupLimit = locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT, getDefaultLookupLimit());

    Set<T> resultTo = new LinkedHashSet<T>();
    if (!toItems.isEmpty()) {
      if (includeOriginal) {
        resultTo.addAll(toItems);
      }
      collectLinked(resultTo, toItems, CollectionsUtil.join(fromItems, stopItems), lookupLimit, myTraverser.getChildren(), recursive);
    }

    Set<T> resultFrom = new LinkedHashSet<T>();
    if (!fromItems.isEmpty()) {
      if (includeOriginal) {
        resultFrom.addAll(fromItems);
      }
      collectLinked(resultFrom, fromItems, CollectionsUtil.join(toItems, stopItems), lookupLimit, myTraverser.getParents(), recursive);
    }

    ArrayList<T> result;
    if (!resultTo.isEmpty() && !resultFrom.isEmpty()) {
      result = new ArrayList<T>(CollectionsUtil.intersect(resultTo, resultFrom));
    } else {
      result = new ArrayList<T>(!resultTo.isEmpty() ? resultTo : resultFrom);
    }
    return getItemHolder(result); //can improve performance by performing traversing in the holder, not retrieving all upfront, this will also add support for lookupLimit
  }

  @NotNull
  private List<T> getItemsFromDimension(@NotNull final Locator locator, @NotNull final String dimensionName) {
    final List<String> dimensionValues = locator.getDimensionValue(dimensionName);
    if (!dimensionValues.isEmpty()) {
      final ArrayList<T> result = new ArrayList<T>();
      for (String dimensionValue : dimensionValues) {
        result.addAll(myFinder.getItems(dimensionValue));
      }
      return result;
    }
    return Collections.emptyList();
  }

  protected void collectLinked(@NotNull final Set<T> result,
                             @NotNull Collection<T> toProcess,
                             @NotNull Collection<T> stopItems,
                             final Long lookupLimit,
                             @NotNull LinkRetriever<T> linkRetriever,
                             final boolean recursive) {
    final int initialSize = result.size();
    while (!toProcess.isEmpty()) {
      Set<T> linkedItems = new LinkedHashSet<T>();
      for (T item : toProcess) {
        if (stopItems.contains(item)) {
          result.add(item);
        } else {
          final List<T> linked = linkRetriever.getLinked(item);
          result.addAll(linked);
          linkedItems.addAll(linked);
        }
      }
      toProcess = linkedItems;
      if (!recursive) break;
      if (lookupLimit != null && (result.size() - initialSize >= lookupLimit)) {
        LOG.debug("Hit lookupLimit " + lookupLimit + " while traversing graph, result is partial");
        break;
      }
    }
  }

  public interface Traverser<S> {
    /**
     * Get items when "to" is specified
     */
    @NotNull
    LinkRetriever<S> getChildren();

    /**
     * Get items when "from" is specified
     */
    @NotNull
    LinkRetriever<S> getParents();
  }

  public interface LinkRetriever<S> {
    @NotNull
    List<S> getLinked(@NotNull S item);
  }

  @NotNull
  public ParsedLocator<T> getParsedLocator(@NotNull String locatorText) {
    Locator locator = createLocator(locatorText, null);

    return new ParsedLocator<T>() {
      @Nullable
      @Override
      public Integer getCount() {
        Long result = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
        return result == null ? null : result.intValue();
      }

      @NotNull
      @Override
      public List<T> getFromItems() {
        return getItemsFromDimension(locator, DIMENSION_FROM); //should be lazy as it might be never needed based on certain count values
      }

      @Override
      public boolean isRecursive() {
        return locator.getSingleDimensionValueAsStrictBoolean(DIMENSION_RECURSIVE, true);
      }

      @Override
      public boolean isIncludeInitial() {
        return locator.getSingleDimensionValueAsStrictBoolean(DIMENSION_INCLUDE_INITIAL, false);
      }

      @Override
      public boolean isAllDimensionsUsed() {
        return locator.getUnusedDimensions().isEmpty();
      }
    };
  }

  public interface ParsedLocator<S> {
    @Nullable
    Integer getCount();
    @NotNull
    List<S> getFromItems();
    boolean isRecursive();
    boolean isIncludeInitial();
    boolean isAllDimensionsUsed();
  }

  public interface LightweightFinder<ITEM> {
    List<ITEM> getItems(@Nullable String locatorText);
  }
}
