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

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildAgent;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.data.util.DuplicateChecker;
import jetbrains.buildServer.server.rest.data.util.LocatorUtil;
import jetbrains.buildServer.server.rest.data.util.finderBuilder.DimensionValueMapper;
import jetbrains.buildServer.server.rest.data.util.finderBuilder.TypedFinderDimension;
import jetbrains.buildServer.server.rest.data.util.finderBuilder.TypedFinderDimensionWithDefaultChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * @date 27/04/2016
 */
@SuppressWarnings("WeakerAccess")
public class TypedFinderBuilder<ITEM> {
  //still to implement:
  //sort result or return ItemProcessor

  //consider adding additional checks on configured dimensions: validate that all dimensions were used: that checker is added or it participated in toItems, etc.
  //consider adding special toItem (single) configuration to process them first

  //  private String myDescription; //todo: use this for providing help on what is searched by this locator
  private final LinkedHashMap<String, TypedFinderDimensionImpl<?>> myDimensions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionCondition, NameValuePairs> myDefaultDimensionsConditions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionCondition, ItemsFromDimensions<ITEM>> myItemsConditions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionCondition, ItemHolderFromDimensions<ITEM>> myItemHoldersConditions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionCondition, ItemFilterFromDimensions<ITEM>> myFiltersConditions = new LinkedHashMap<>();
  private ItemsFromDimension<ITEM, String> mySingleDimensionHandler;
  private LocatorProvider<ITEM> myLocatorProvider;
  private Supplier<DuplicateChecker<ITEM>> myDuplicateCheckerSupplier;
  private String myFinderName;

  public class TypedFinderDimensionImpl<TYPE> implements TypedFinderDimension<ITEM, TYPE> {
    @NotNull protected final Dimension<TYPE> myDimension;
    @NotNull protected final DimensionValueMapper<TYPE> myValueMapper;
    protected String myDescription = null;
    protected Boolean myHidden = null;

    public TypedFinderDimensionImpl(@NotNull final Dimension<TYPE> dimension, @NotNull final DimensionValueMapper<TYPE> valueMapper) {
      myDimension = dimension;
      myValueMapper = valueMapper;
    }

    @NotNull
    public Dimension<TYPE> getDimension() {
      return myDimension;
    }

    @NotNull
    public DimensionValueMapper<TYPE> getValueMapper() {
      return myValueMapper;
    }

    @Nullable
    public String getDescription() {
      return myDescription;
    }

    public boolean getHidden() {
      return myHidden == null ? false : myHidden;
    }

    @Override
    @NotNull
    public TypedFinderDimension<ITEM, TYPE> description(@NotNull final String description) {
      if (description.length() == 0) throw new OperationException("Wrong description: empty");
      if (myDescription != null) throw new OperationException("Attempt to redefine description: old: '" + getDescription() + "', new: '" + description + "'");
      myDescription = description;
      return this;
    }

    @Override
    @NotNull
    public TypedFinderDimension<ITEM, TYPE> hidden() {
      if (myHidden != null) throw new OperationException("Attempt to redefine hidden: old: '" + getHidden() + "'");
      myHidden = true;
      return this;
    }

    @Override
    @NotNull
    public TypedFinderDimension<ITEM, TYPE> withDefault(@NotNull final String value) {
      defaults(DimensionCondition.ALWAYS, new NameValuePairs().add(getDimension().name, value));
      return this;
    }

    @Override
    @NotNull
    public TypedFinderDimension<ITEM, TYPE> filter(@NotNull final Filter<TYPE, ITEM> filter) {
      TypedFinderBuilder.this.filter(getDimension(), filter);
      return this;
    }

    @Override
    @NotNull
    public TypedFinderDimension<ITEM, TYPE> toItems(@NotNull final ItemsFromDimension<ITEM, TYPE> filteringMapper) {
      multipleConvertToItems(new DimensionConditionsImpl().when(getDimension(), Condition.PRESENT), dimensions -> {
        List<TYPE> dimensionValues = dimensions.get(getDimension());
        if (dimensionValues == null || dimensionValues.isEmpty()) return null;
        Set<ITEM> result = new LinkedHashSet<>(); //this requires due hash function for the ITEM - might need review!
        for (TYPE dimensionValue : dimensionValues) {
          List<ITEM> items = filteringMapper.get(dimensionValue);
          if (items == null) continue;
          if (result.isEmpty()) {
            result.addAll(items);
          } else {
            result = CollectionsUtil.intersect(items, result);
          }
          if (result.isEmpty()) return new ArrayList<>(result); //already empty intersection -> exit early
        }
        return new ArrayList<>(result);
      });
      return this;
    }

    @Override
    @NotNull
    public <TYPE_FROM_ITEM> TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FROM_ITEM> defaultFilter(@NotNull final Filter<TYPE, TYPE_FROM_ITEM> checker) {
      return new TypedFinderDimensionWithDefaultCheckerImpl<>(this, checker);
    }
  }

  class TypedFinderDimensionWithDefaultCheckerImpl<TYPE, TYPE_FOR_FILTER> implements TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> {
    @NotNull private final TypedFinderDimensionImpl<TYPE> myOriginal;
    @NotNull private final Filter<TYPE, TYPE_FOR_FILTER> myDefaultChecker;

    private TypedFinderDimensionWithDefaultCheckerImpl(final @NotNull TypedFinderDimensionImpl<TYPE> original,
                                                       final @NotNull Filter<TYPE, TYPE_FOR_FILTER> defaultChecker) {
      myOriginal = original;
      myDefaultChecker = defaultChecker;
    }

    @NotNull
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> valueForDefaultFilter(@NotNull TypeFromItem<TYPE_FOR_FILTER, ITEM> retriever) {
      TypedFinderBuilder.this.filter(myOriginal.getDimension(), (value, item) -> {
        TYPE_FOR_FILTER valueForFilter = retriever.get(item);
        if (valueForFilter == null) return false; //no value, but filter is present -> should not match
        return myDefaultChecker.isIncluded(value, valueForFilter);
      });
      return this;
    }

    @NotNull
    @Override
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> description(@NotNull final String description) {
      myOriginal.description(description);
      return this;
    }

    @NotNull
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> hidden() {
      myOriginal.hidden();
      return this;
    }

    @NotNull
    @Override
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> withDefault(@NotNull final String value) {
      myOriginal.withDefault(value);
      return this;
    }

    @NotNull
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> filter(@NotNull Filter<TYPE, ITEM> checker) {
      myOriginal.filter(checker);
      return this;
    }

    @NotNull
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> toItems(@NotNull ItemsFromDimension<ITEM, TYPE> filteringMapper) {
      myOriginal.toItems(filteringMapper);
      return this;
    }

    @NotNull
    @Override
    public <TYPE_FROM_ITEM> TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FROM_ITEM> defaultFilter(@NotNull final Filter<TYPE, TYPE_FROM_ITEM> checker) {
      throw new OperationException("Attempt to call defaultFilter for TypedFinderDimensionWithDefaultChecker");
    }
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, Long, Long> dimensionLong(@NotNull final Dimension<Long> dimension) {
    return dimension(dimension, mapper(TypedFinderBuilder::getLong).acceptingType("number")).defaultFilter(Long::equals);
  }

  @NotNull
  private static Long getLong(@NotNull final String dimensionValue) {
    try {
      return Long.parseLong(dimensionValue);
    } catch (NumberFormatException e) {
      throw new LocatorProcessException("Invalid value '" + dimensionValue + "'. Should be a number.");
    }
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, String, String> dimensionString(@NotNull final Dimension<String> dimension) {
    return dimension(dimension, mapper(dimensionValue -> dimensionValue).acceptingType("text")).defaultFilter(String::equals);
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, Boolean, Boolean> dimensionBoolean(@NotNull final Dimension<Boolean> dimension) {
    return dimension(dimension, mapper(LocatorUtil::getBooleanAllowingAny).acceptingType("boolean"))
      .defaultFilter(FilterUtil::isIncludedByBooleanFilter);
  }

  @NotNull
  public TypedFinderDimensionWithDefaultChecker<ITEM, Long, Long> dimensionLookupLimit() {
    return dimensionLong(PagerDimensions.dimensionLookupLimit()).description("maximum number of items to process when filtering").withDefault(String.valueOf(1000L));
  }

  @NotNull
  public TypedFinderDimensionWithDefaultChecker<ITEM, Long, Long> dimensionStart() {
    return dimensionLong(PagerDimensions.dimensionStart()).description("number of items to skip");
  }

  @NotNull
  public TypedFinderDimensionWithDefaultChecker<ITEM, Long, Long> dimensionCount() {
    return dimensionLong(PagerDimensions.dimensionCount()).description("number of items to return").withDefault(String.valueOf(Constants.getDefaultPageItemsCount()));
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<BuildPromotion>, Set<BuildPromotion>> dimensionBuildPromotions(@NotNull final Dimension<List<BuildPromotion>> dimension,
                                                                                                                          @NotNull final ServiceLocator serviceLocator) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(BuildPromotionFinder.class), "build locator");
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<BuildTypeOrTemplate>, Set<BuildTypeOrTemplate>> dimensionBuildTypes(@NotNull final Dimension<List<BuildTypeOrTemplate>> dimension,
                                                                                                                               @NotNull final ServiceLocator serviceLocator) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(BuildTypeFinder.class), "buildType locator").description("build types locator");
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<SBuildAgent>, Set<SBuildAgent>> dimensionAgents(@NotNull final Dimension<List<SBuildAgent>> dimension,
                                                                                                           @NotNull final ServiceLocator serviceLocator) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(AgentFinder.class), BuildAgent::getId, "agent locator");
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<SProject>, Set<SProject>> dimensionProjects(@NotNull final Dimension<List<SProject>> dimension,
                                                                                                       @NotNull final ServiceLocator serviceLocator) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(ProjectFinder.class), "project locator");
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<SVcsRoot>, Set<SVcsRoot>> dimensionVcsRoots(@NotNull final Dimension<List<SVcsRoot>> dimension,
                                                                                                       @NotNull final ServiceLocator serviceLocator) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(VcsRootFinder.class), "vcs root locator");
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<SUser>, Set<SUser>> dimensionUsers(@NotNull final Dimension<List<SUser>> dimension,
                                                                                              @NotNull final ServiceLocator serviceLocator) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(UserFinder.class), "user locator");
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<STest>, Set<STest>> dimensionTests(@NotNull final Dimension<List<STest>> dimension,
                                                                                              @NotNull final ServiceLocator serviceLocator) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(TestFinder.class), "test locator");
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, List<ProblemWrapper>, Set<ProblemWrapper>> dimensionProblems(
    @NotNull final Dimension<List<ProblemWrapper>> dimension,
    @NotNull final ServiceLocator serviceLocator
  ) {
    return dimensionWithFinder(dimension, () -> serviceLocator.getSingletonService(ProblemFinder.class), "problem locator");
  }

  /**
   * Defines a dimension, which supplies intermediate items for purpouses of filtering or further extracting final ITEMs. <br/>
   * <br/>
   * Use with caution: should be able to find items in Set!
   */
  public <FINDER_TYPE> TypedFinderDimensionWithDefaultChecker<ITEM, List<FINDER_TYPE>, Set<FINDER_TYPE>> dimensionWithFinder(
    @NotNull final Dimension<List<FINDER_TYPE>> dimension,
    @NotNull final Supplier<Finder<FINDER_TYPE>> finderValue,
    @NotNull String typeDescription
  ) {
    return dimension(dimension, mapper(dimensionValue -> getNotEmptyItems(finderValue.get(), dimensionValue)).acceptingType(typeDescription))
      .defaultFilter((fromFilter, fromItem) -> {
        for (FINDER_TYPE item : fromFilter) {
          if (fromItem.contains(item)) return true;
        }
        return false;
      });
  }

  @NotNull
  private <FINDER_TYPE> List<FINDER_TYPE> getNotEmptyItems(final @NotNull Finder<FINDER_TYPE> finder, final @NotNull String dimensionValue) {
    List<FINDER_TYPE> result = finder.getItems(dimensionValue).myEntries;
    if (result.isEmpty()) throw new LocatorProcessException("Nothing found by locator '" + dimensionValue + "'");
    return result;
  }

  /**
   * @param <MIDDLE> - type which can be used in Set
   * @return typed finder
   */
  public <FINDER_TYPE, MIDDLE> TypedFinderDimensionWithDefaultChecker<ITEM, List<FINDER_TYPE>, Set<FINDER_TYPE>>
  dimensionWithFinder(@NotNull final Dimension<List<FINDER_TYPE>> dimension, @NotNull final Supplier<Finder<FINDER_TYPE>> finderValue,
                      @NotNull final Function<FINDER_TYPE, MIDDLE> converter, @NotNull String typeDescription) {
    return dimension(dimension, mapper(dimensionValue -> getNotEmptyItems(finderValue.get(), dimensionValue)).acceptingType(typeDescription))
      .defaultFilter((fromFilter, fromItem) -> {
        Set<MIDDLE> middleSet = new HashSet<>();
        for (FINDER_TYPE item : fromItem) {
          middleSet.add(converter.apply(item));
        }
        for (FINDER_TYPE item : fromFilter) {
          if (middleSet.contains(converter.apply(item))) return true;
        }
        return false;
      });
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, ParameterCondition, ParametersProvider> dimensionParameterCondition(@NotNull final Dimension<ParameterCondition> dimension) {
    return dimension(dimension, mapper(ParameterCondition::create).acceptingType("parameter condition"))
      .defaultFilter(ParameterCondition::matches);
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, ParameterCondition, InheritableUserParametersHolder> dimensionOwnParameterCondition(@NotNull final Dimension<ParameterCondition> dimension) {
    return dimension(dimension, mapper(ParameterCondition::create).acceptingType("parameter condition with inherited support"))
      .defaultFilter(ParameterCondition::matches);
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, ValueCondition, String> dimensionValueCondition(@NotNull final Dimension<ValueCondition> dimension) {
    return dimension(dimension, mapper(ParameterCondition::createValueCondition).acceptingType("value condition"))
      .defaultFilter(ValueCondition::matches);
  }

  public <T extends Enum<T>> TypedFinderDimensionWithDefaultChecker<ITEM, T, T> dimensionEnum(@NotNull final Dimension<T> dimension, @NotNull final Class<T> enumClass) {
    return dimension(dimension, mapper(dimensionValue -> getEnumValue(dimensionValue, enumClass)).acceptingType("one of " + getValues(enumClass)))
      .defaultFilter(Enum::equals);
  }

  public <T extends Enum<T>> TypedFinderDimensionWithDefaultChecker<ITEM, Set<T>, T> dimensionEnums(@NotNull final Dimension<Set<T>> dimension, @NotNull final Class<T> enumClass) {
    return dimensionSetOf(dimension, String.valueOf(getValues(enumClass)), s -> getValue(s, enumClass));
  }

  /**
   * T should have due equals/hashcode to make Set<T>::contains work duly
   */
  public <T> TypedFinderDimensionWithDefaultChecker<ITEM, Set<T>, T> dimensionSetOf(@NotNull final Dimension<Set<T>> dimension,
                                                                                    @NotNull final String helpTextDescribingItems,
                                                                                    @NotNull final Function<String, T> dimensionValueToItem) {
    return dimension(dimension,
                     mapper(dimensionValue -> getValues(dimensionValue, helpTextDescribingItems, dimensionValueToItem)).acceptingType("one or more of " + helpTextDescribingItems))
      .defaultFilter(Set::contains);
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, String, String> dimensionFixedText(@NotNull final Dimension<String> dimension,
                                                                                         @NotNull final String... values) {
    Set<String> lowerCaseValues = Arrays.stream(values).map(String::toLowerCase).collect(Collectors.toSet());
    String supportedValuesText = StringUtil.join(values, ", ");

    return dimension(dimension, mapper(dimensionValue -> {
      if (lowerCaseValues.contains(dimensionValue.toLowerCase())) {
        return dimensionValue;
      }
      throw new BadRequestException("Unsupported value '" + dimensionValue + "'. Supported values are: " + supportedValuesText);

    }).acceptingType("one of " + supportedValuesText))
      .defaultFilter(String::equalsIgnoreCase);
  }

  public TypedFinderDimensionWithDefaultChecker<ITEM, TimeCondition.ParsedTimeCondition, Date> dimensionTimeCondition(@NotNull final Dimension<TimeCondition.ParsedTimeCondition> dimension,
                                                                                                                      @NotNull final TimeCondition timeCondition) {
    return dimension(dimension, mapper(timeCondition::getTimeCondition).acceptingType("time condition"))
      .defaultFilter(TimeCondition.ParsedTimeCondition::matches);
  }

  public <F> TypedFinderDimensionWithDefaultChecker<ITEM, ItemFilter<F>, F> dimensionFinderFilter(@NotNull final Dimension<ItemFilter<F>> dimension,
                                                                                                  @NotNull final Finder<F> finder,
                                                                                                  @NotNull final String description) {
    return dimension(dimension, mapper(finder::getFilter).acceptingType("").acceptingType(description)).defaultFilter(FilterConditionChecker::isIncluded);
  }

  @Nullable
  public static <T> Set<T> getIntersected(@Nullable List<Set<T>> dimensions) {
    if (dimensions == null || dimensions.size() == 0) {
      return null;
    }
    Set<T> intersected = new HashSet<>(dimensions.get(0));
    dimensions.stream().skip(1).forEach(intersected::retainAll);
    return intersected;
  }

  //============================= Main definition methods =============================

  //public AbstractTypedFinder<ITEM> description(@NotNull final String description) {
  //  myDescription = description;
  //  return this;
  //}

  public void name(@NotNull String finderName) {
    myFinderName = finderName;
  }

  /**
   * Defines a dimension value mapper, String -> {@code TYPE}, making the dimension typed. <br/>
   * Example: in case <em>sequentialNumber</em> dimension is an integer, following may be implemented:
   * <pre>
   * builder.dimension(
   *   new Dimension&lt;Integer>("sequentialNumber"),
   *   type(Integer::valueOf).description("Extract sequantial number assigned to this item.")
   * ).defaultFilter(Integer::equals);
   * </pre>
   *
   * @param dimension actual typed dimension description
   * @param dimensionValueMapper retrieves result of TYPE given dimension value
   * @param <TYPE> target type used in filters, etc.
   */
  public <TYPE> TypedFinderDimension<ITEM, TYPE> dimension(@NotNull final Dimension<TYPE> dimension, @NotNull final DimensionValueMapper<TYPE> dimensionValueMapper) {
    if (myDimensions.containsKey(dimension.name)) throw new OperationException("Dimension with name '" + dimension.name + "' was already added");
    @NotNull TypedFinderDimensionImpl<TYPE> value = new TypedFinderDimensionImpl<>(dimension, dimensionValueMapper);
    myDimensions.put(dimension.name, value);
    return value;
  }

  /**
   * Define behaviour when single value is passed in the locator. Usually, it's assumed that single dimension value represents an item id.
   */
  public void singleDimension(@NotNull final ItemsFromDimension<ITEM, String> singleDimensionHandler) {
    mySingleDimensionHandler = singleDimensionHandler;
  }

  public TypedFinderBuilder<ITEM> multipleConvertToItems(@NotNull final DimensionCondition conditions,
                                                         @NotNull final ItemsFromDimensions<ITEM> parsedObjectsIfConditionsMatched) {
    myItemsConditions.put(conditions, parsedObjectsIfConditionsMatched);
    return this;
  }

  public TypedFinderBuilder<ITEM> multipleConvertToItemHolder(@NotNull final DimensionCondition conditions,
                                                              @NotNull final ItemHolderFromDimensions<ITEM> parsedObjectsIfConditionsMatched) {
    myItemHoldersConditions.put(conditions, parsedObjectsIfConditionsMatched);
    return this;
  }

  public TypedFinderBuilder<ITEM> filter(@NotNull final DimensionCondition conditions, @NotNull final ItemFilterFromDimensions<ITEM> parsedObjectsIfConditionsMatched) {
    ItemFilterFromDimensions<ITEM> previous = myFiltersConditions.put(conditions, parsedObjectsIfConditionsMatched);
    if (previous != null) throw new OperationException("Overriding dimension condition '" + conditions.toString() + "'");
    return this;
  }

  @NotNull
  public TypedFinderBuilder<ITEM> locatorProvider(@NotNull LocatorProvider<ITEM> locatorProvider) {
    myLocatorProvider = locatorProvider;
    return this;
  }

  @NotNull
  public TypedFinderBuilder<ITEM> duplicateCheckerSupplier(@NotNull Supplier<DuplicateChecker<ITEM>> duplicateCheckerSupplier) {
    myDuplicateCheckerSupplier = duplicateCheckerSupplier;
    return this;
  }

  public TypedFinderBuilder<ITEM> defaults(@NotNull final DimensionCondition conditions, @NotNull final NameValuePairs defaults) {
    myDefaultDimensionsConditions.compute(conditions, (dimensionCondition, nameValuePairs) -> NameValuePairs.merge(nameValuePairs, defaults));
    return this;
  }

  @NotNull
  public Finder<ITEM> build() {
//    if (myLocatorProvider == null) throw new OperationException("Should set locator provider via a call to locatorProvider()");
    FinderImpl<ITEM> result = new FinderImpl<>(new TypedFinderDataBindingImpl());
    if (myFinderName != null) {
      result.setName(myFinderName);
    } else {
      result.setName(getClass().getName());
    }
    return result;
  }

  //============================= helper methods =============================

  private <TYPE> TypedFinderBuilder<ITEM> filter(@NotNull final Dimension<TYPE> dimension, @NotNull final Filter<TYPE, ITEM> filteringMapper) {
    DimensionCondition condition;
    if (Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME.equals(dimension.name)) {
      condition = Locator::isSingleValue;
    } else {
      condition = new DimensionConditionsImpl().when(dimension, Condition.PRESENT);
    }
    return filter(condition, dimensions -> {
      final List<TYPE> values = dimensions.get(dimension);
      if (values == null || values.isEmpty()) return null;
      MultiCheckerFilter<ITEM> result = new MultiCheckerFilter<>();
      for (TYPE value : values) {
        result.add(new ItemFilter<ITEM>() {
          @Override
          public boolean shouldStop(@NotNull final ITEM item) {
            return false;
          }

          @Override
          public boolean isIncluded(@NotNull final ITEM item) {
            return filteringMapper.isIncluded(value, item);
          }
        });
      }
      return result;
    });
  }

  @NotNull
  public <TYPE> DimensionValueMapper<TYPE> mapper(@NotNull final DimensionValueMapper.ValueRetriever<TYPE> retriever) {
    return new DimensionValueMapper<>(retriever);
  }

  @NotNull
  public static <T> Set<T> getValues(@NotNull final String value, @NotNull final String helpTextDescribingItems, @NotNull final Function<String, T> dimensionValueToItem) {
    Locator.processHelpRequest(value, "One value or multiple comma-separated \"item:<value>\" of supported values: " + helpTextDescribingItems);
    if (!value.contains(",")) {
      return Collections.singleton(dimensionValueToItem.apply(value));
    }
    return StringUtil.split(value, ",").stream().map(s -> {
      Locator locator = new Locator(s, "item");
      String item = locator.getSingleDimensionValue("item");
      if (item == null) throw new BadRequestException("Unknown value \"" + s + "\": should be single value or contain \"item\" dimensions");
      T result = dimensionValueToItem.apply(item);
      locator.checkLocatorFullyProcessed();
      return result;
    }).collect(Collectors.toSet());
  }

  @NotNull
  public static <T extends Enum<T>> T getEnumValue(@NotNull final String value, @NotNull final Class<T> enumClass) {
    Locator.processHelpRequest(value, "Supported values are: " + getValues(enumClass));
    return getValue(value, enumClass);
  }

  @NotNull
  public static <T extends Enum<T>> T getValue(@NotNull final String value, @NotNull final Class<T> enumClass) {
    T[] consts = enumClass.getEnumConstants();
    assert consts != null;
    for (T c : consts) {
      if (value.equalsIgnoreCase(c.name())) return c;
    }
    throw new BadRequestException("Unsupported value '" + value + "'. Supported values are: " + getValues(enumClass));
  }

  private static <T extends Enum<T>> List<String> getValues(final @NotNull Class<T> enumClass) {
    return CollectionsUtil.convertCollection(Arrays.asList(enumClass.getEnumConstants()), source -> source.name().toLowerCase());
  }

  //============================= Public subclasses =============================

  /**
   * TypedFinder dimension descriptor.
   *
   * @param <TYPE> type of the items, produced by dimension data retrievers.
   */
  @SuppressWarnings("unused")
  public static class Dimension<TYPE> { //type is important here to let type inference for all the rest of the class usages
    @NotNull public final String name;

    public Dimension(@NotNull final String name) {
      if (name.length() == 0) throw new OperationException("Wrong name: empty");
      this.name = name;
    }

    @NotNull
    public static <T> Dimension<T> single() {
      return new Dimension<>(Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    }

    @Override
    public String toString() {
      return "Dimension '" + name + "'";
    }
  }

  public interface DimensionObjects {
    @Nullable
    <TYPE> List<TYPE> get(@NotNull Dimension<TYPE> dimension);

    default <TYPE> Optional<TYPE> single(@NotNull Dimension<TYPE> dimension) {
      List<TYPE> values = get(dimension);
      if (values == null || values.isEmpty()) return Optional.empty();

      if (values.size() > 1) {
        throw new BadRequestException("Multiple '" + dimension.name + "' dimensions are not supported");
      }

      return Optional.ofNullable(values.get(0));
    }

    /**
     * Same as get, but does not mark the dimension as used
     */
    @Nullable
    <TYPE> List<TYPE> lookup(@NotNull Dimension<TYPE> dimension);

    Set<String> getUsedDimensions();

    Set<String> getUnusedDimensions();
  }

  public interface DimensionConditions {
    @NotNull
    DimensionConditions when(@NotNull Dimension<?> dimension, @NotNull Condition conditionBasedOnValue);
  }

  public interface DimensionCondition {
    boolean complies(@NotNull Locator locator);

    DimensionCondition ALWAYS = new DimensionCondition() {
      @Override
      public boolean complies(@NotNull final Locator locator) {
        return true;
      }

      @Override
      public String toString() {
        return "Conditions 'always'";
      }
    };
  }

  public static abstract class Condition {
    abstract boolean get(@NotNull String dimensionValue);

    static Condition equals(@NotNull String value) {
      return new Condition() {
        @Override
        public boolean get(@NotNull final String dimensionValue) {
          return value.equals(dimensionValue);
        }

        @Override
        public String toString() {
          return "Condition 'equals'";
        }
      };
    }

    final static Condition PRESENT = new Condition() {
      @Override
      public boolean get(@Nullable final String dimensionValue) {
        return dimensionValue != null; //need to get dimension from locator to differentiate between "any" and null ofr boolean
      }

      @Override
      public String toString() {
        return "Condition 'present'";
      }
    };

    @Override
    public String toString() {
      return "Condition 'custom'";
    }
  }


  public interface LocatorProvider<ITEM> {
    @NotNull
    String getLocator(@NotNull final ITEM item);
  }

  public interface ItemsFromDimension<ITEM, TYPE> {
    /**
     * @return the list of items matching dimensions, 'null' is this provider should be ignored
     */
    @Nullable
    List<ITEM> get(@NotNull final TYPE dimension);
  }

  public interface ItemsFromDimensions<ITEM> {
    /**
     * @return the list of items matching dimensions, 'null' is this provider should be ignored
     */
    @Nullable
    List<ITEM> get(@NotNull final DimensionObjects dimensions);
  }

  public interface ItemHolderFromDimensions<ITEM> {
    /**
     * Provide items which satisfy conditions applied by some or all of the given dimesnion values.
     * Should mark dimensions which were taken into account as used.
     */
    @NotNull
    FinderDataBinding.ItemHolder<ITEM> get(@NotNull final DimensionObjects dimensions);
  }

  public interface ItemFilterFromDimensions<ITEM> {
    /**
     * Should get all dimensions on 'get' method call, otherwise they will not be recorded properly as used
     * Should not get any dimensions which will not be used.
     *
     * @param dimensions available dimensions
     * @return 'null' if no filtering is necessary
     */
    @Nullable
    ItemFilter<ITEM> get(@NotNull final DimensionObjects dimensions);
  }

  public interface Filter<CHECK_VALUE, ACTUAL_VALUE> {
    boolean isIncluded(@NotNull CHECK_VALUE value, @NotNull ACTUAL_VALUE item);
  }

  public interface TypeFromItem<TYPE, ITEM> {
    @Nullable
    TYPE get(@NotNull final ITEM item);
  }

  //============================= Helper subclasses =============================

  private static class DimensionConditionsImpl implements DimensionConditions, DimensionCondition {
    @NotNull private final List<DimensionCondition> conditions = new ArrayList<>();

    @NotNull
    public DimensionConditionsImpl when(@NotNull Dimension<?> dimension, @NotNull Condition conditionBasedOnValue) {
      conditions.add(new DimensionCondition(dimension, conditionBasedOnValue));
      return this;
    } //later can add context here (pass as input only only dimension value, but also some context returned from the previous conditions)

    @Override
    public boolean complies(@NotNull Locator locator) {
      for (DimensionCondition condition : conditions) {
        if (!complies(condition, locator)) return false;
      }
      return true;
    }

    private boolean complies(@NotNull final DimensionCondition condition, @NotNull final Locator locator) {
      List<String> values = locator.lookupDimensionValue(condition.dimension.name);
      if (!values.isEmpty()) {  //conditions only defined nor non-null values
        for (String value : values) { //if at least one of multi-dimension values complies
          if (condition.conditionBasedOnValue.get(value)) return true;
        }
      }
      return false;
    }

    @Override
    public String toString() {
      return "Conditions: {" + StringUtil.join(conditions, DimensionCondition::toString, ", ") + "}";
    }

    private static class DimensionCondition {
      @NotNull private final Dimension<?> dimension;
      @NotNull private final Condition conditionBasedOnValue;
      //later can add context here (pass as input only dimension value, but also some context returned from the previous conditions)
      // use builders to construct Check

      DimensionCondition(@NotNull final Dimension<?> dimension, @NotNull final Condition conditionBasedOnValue) {
        this.dimension = dimension;
        this.conditionBasedOnValue = conditionBasedOnValue;
      }

      @Override
      public String toString() {
        return dimension.toString() + " -> " + conditionBasedOnValue.toString();
      }
    }
  }


  public static class NameValuePairs {
    private final TreeMap<String, String> myPairs = new TreeMap<>();

    public NameValuePairs add(@NotNull String name, @NotNull String value) {
      myPairs.put(name, value);
      return this;
    }

    @Nullable
    public static NameValuePairs merge(@Nullable NameValuePairs one, @Nullable NameValuePairs two) {
      if (one == null) return two;
      if (two == null) return one;
      NameValuePairs result = new NameValuePairs();
      result.myPairs.putAll(one.myPairs);
      result.myPairs.putAll(two.myPairs);
      return result;
    }

    @NotNull
    String get(@NotNull String name) {
      return myPairs.get(name);
    }

    @NotNull
    Iterator<NameValue> iterator() {
      final Iterator<Map.Entry<String, String>> iterator = myPairs.entrySet().iterator();
      return new Iterator<NameValue>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public NameValue next() {
          Map.Entry<String, String> next = iterator.next();
          return new NameValue(next.getKey(), next.getValue());
        }
      };
    }

    private static class NameValue {
      @NotNull private final String name;
      @NotNull private final String value;

      NameValue(@NotNull final String name, @NotNull final String value) {

        this.name = name;
        this.value = value;
      }
    }
  }


  //============================= AbstractFinder implementation =============================

  private class TypedFinderDataBindingImpl implements FinderDataBinding<ITEM> {
    @Nullable
    @Override
    public Long getDefaultPageItemsCount() {
      return null;
    }

    @Nullable
    @Override
    public Long getDefaultLookupLimit() {
      return null;
    }

    @NotNull
    @Override
    public LocatorDataBinding<ITEM> getLocatorDataBinding(@NotNull final Locator locator) {
      return new LocatorDataBinding<ITEM>() {
        private DimensionObjects myDimensionObjects;
        private final Locator myLocator = locator;
        private FinderDataBinding.ItemHolder<ITEM> myItemHolderResult;
        private ItemFilter<ITEM> myItemFilterResult;

        private DimensionObjects getDimensionObjects() {
          if (myDimensionObjects == null) {
            myDimensionObjects = TypedFinderBuilder.this.getDimensionObjects(myLocator);
          }
          return myDimensionObjects;
        }

        @NotNull
        @Override
        public ItemHolder<ITEM> getPrefilteredItems() {
          if (myItemHolderResult == null) {
            myItemHolderResult = TypedFinderDataBindingImpl.this.getPrefilteredItems(myLocator, getDimensionObjects());
          }
          return myItemHolderResult;
        }

        @NotNull
        @Override
        public ItemFilter<ITEM> getFilter() {
          if (myItemFilterResult == null) {
            myItemFilterResult = TypedFinderDataBindingImpl.this.getFilter(myLocator, getDimensionObjects());
          }
          return myItemFilterResult;
        }
      };
    }

    @NotNull
    @Override
    public String[] getKnownDimensions() {
      return TypedFinderBuilder.this.getKnownDimensions();
    }

    @NotNull
    @Override
    public String[] getHiddenDimensions() {
      return TypedFinderBuilder.this.getHiddenDimensions();
    }

    @NotNull
    @Override
    public Locator.DescriptionProvider getLocatorDescriptionProvider() {
      return TypedFinderBuilder.this.getLocatorDescriptionProvider();
    }

    @Nullable
    @Override
    public ITEM findSingleItem(@NotNull final Locator locator) {
      return null;
    }

    @NotNull
    protected ItemHolder<ITEM> getPrefilteredItems(@NotNull final Locator locator, @NotNull DimensionObjects dimensionObjects) {
      if (mySingleDimensionHandler != null && locator.isSingleValue()) {
        //noinspection ConstantConditions
        List<ITEM> items = mySingleDimensionHandler.get(locator.getSingleValue());
        if (items == null) throw new OperationException("Single value items provider returned 'null', but it cannot be ignored");
        return FinderDataBinding.getItemHolder(items);
      }

      for (DimensionCondition conditions : myItemsConditions.keySet()) {
        if (conditions.complies(locator)) {
          ItemsFromDimensions<ITEM> itemsFromDimensions = myItemsConditions.get(conditions);
          DimensionObjectsWrapper wrapper = new DimensionObjectsWrapper(dimensionObjects);
          List<ITEM> itemsList = itemsFromDimensions.get(wrapper);
          if (itemsList != null) {
            if (!wrapper.getUsedDimensions().isEmpty()) {
              locator.markUsed(wrapper.getUsedDimensions());
            }
            return FinderDataBinding.getItemHolder(itemsList);
          }
          //add debug mode to collect all toItems and report their sizes if another order would be more effective
        }
        //consider processing other items as well and intersecting???

      }
      for (Map.Entry<DimensionCondition, ItemHolderFromDimensions<ITEM>> entry : myItemHoldersConditions.entrySet()) {
        if (entry.getKey().complies(locator)) {
          DimensionObjectsWrapper wrapper = new DimensionObjectsWrapper(dimensionObjects);
          ItemHolder<ITEM> itemItemHolder = entry.getValue().get(wrapper);
          if (!wrapper.getUsedDimensions().isEmpty()) {
            locator.markUsed(wrapper.getUsedDimensions());
          }
          return itemItemHolder;
        }
        //consider processing other items as well and intersecting???
      }

      //todo: improve this to provide a message with available conditions or require providing "getAll" at configuration time
      throw new OperationException("No conditions matched. Use multipleConvertToItems and alike methods"); //exception type
    }


    @NotNull
    protected ItemFilter<ITEM> getFilter(@NotNull final Locator locator, @NotNull DimensionObjects dimensionObjects) {
      final Locator locatorCopy = new Locator(locator); //wrapping locator so that original locator is not marked as used on condition checking

      MultiCheckerFilter<ITEM> result = new MultiCheckerFilter<>();
      for (Map.Entry<DimensionCondition, ItemFilterFromDimensions<ITEM>> entry : myFiltersConditions.entrySet()) {
        if (entry.getKey().complies(locatorCopy)) {
          final Set<String> alreadyUsedDimensions = new HashSet<>(dimensionObjects.getUsedDimensions());
          DimensionObjectsWrapper wrapper = new DimensionObjectsWrapper(dimensionObjects);
          ItemFilter<ITEM> checker = entry.getValue().get(wrapper);
          if (!wrapper.getUsedDimensions().isEmpty()) {
            locator.markUsed(wrapper.getUsedDimensions());
            if (alreadyUsedDimensions.containsAll(wrapper.getUsedDimensions())) continue; //all the dimensions were already used. Is this logic at all needed?
          }
          if (checker == null) continue;
          result.add(checker); //also support shouldStop
        }
      }
      return result;
    }

    @NotNull
    @Override
    public String getItemLocator(@NotNull final ITEM item) {
      if (myLocatorProvider == null) throw new OperationException("Incorrect configuration of the typed finder: locator provider not set");
      return myLocatorProvider.getLocator(item);
    }

    @Nullable
    @Override
    public DuplicateChecker<ITEM> createDuplicateChecker() {
      return myDuplicateCheckerSupplier == null ? null : myDuplicateCheckerSupplier.get();
    }

    private class DimensionObjectsWrapper implements DimensionObjects {
      @NotNull private final Set<String> usedDimensions = new HashSet<>();
      @NotNull private final DimensionObjects myDimensionObjects;

      public DimensionObjectsWrapper(final @NotNull DimensionObjects dimensionObjects) {
        myDimensionObjects = dimensionObjects;
      }

      @Nullable
      @Override
      public <TYPE> List<TYPE> get(@NotNull final Dimension<TYPE> dimension) {
        usedDimensions.add(dimension.name);
        return myDimensionObjects.get(dimension);
      }

      @Nullable
      @Override
      public <TYPE> List<TYPE> lookup(@NotNull final Dimension<TYPE> dimension) {
        return myDimensionObjects.lookup(dimension);
      }

      @NotNull
      @Override
      public Set<String> getUsedDimensions() {
        return usedDimensions;
      }

      @NotNull
      public Set<String> getUnusedDimensions() {
        return myDimensionObjects.getUnusedDimensions();
      }
    }
  }

  @NotNull
  public String[] getKnownDimensions() {
    ArrayList<String> result = new ArrayList<>();
    for (TypedFinderDimensionImpl<?> dimension : myDimensions.values()) {
      if (!dimension.getHidden()) result.add(dimension.getDimension().name);
    }
    if (mySingleDimensionHandler != null) result.add(Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    return CollectionsUtil.toArray(result, String.class);
  }

  @NotNull
  public String[] getHiddenDimensions() {
    List<String> result = new ArrayList<>();
    for (TypedFinderDimensionImpl<?> dimension : myDimensions.values()) {
      if (dimension.getHidden()) result.add(dimension.getDimension().name);
    }
    return CollectionsUtil.toArray(result, String.class);
  }

  @NotNull
  public Locator.DescriptionProvider getLocatorDescriptionProvider() {
    return (locator, includeHidden) -> {
      StringBuilder result = new StringBuilder();
      result.append("Supported locator dimensions:\n");
      for (TypedFinderDimensionImpl<?> dimension : myDimensions.values()) {
        if (!includeHidden && dimension.getHidden() && locator.getDimensionValue(dimension.getDimension().name).isEmpty()) {
          continue;
        }
        result.append(dimension.getDimension().name);
        String dimensionDescription = dimension.getDescription();
        if (dimensionDescription != null) {
          result.append(" - ");
          result.append(dimensionDescription);
        }
        String typeDescription = dimension.getValueMapper().getLocatorTypeDescription();
        if (typeDescription != null) {
          result.append(" (type: ").append(typeDescription).append(")");
        }
        result.append("\n");
      }
      return result.toString();
    };
  }

  @NotNull
  public DimensionObjects getDimensionObjects(@NotNull Locator locator) {
    patchWithDefaultValues(locator);
    return new DimensionObjectsImpl(locator);
  }

  private void patchWithDefaultValues(final @NotNull Locator locator) {
    if (!locator.isSingleValue()) {
      for (Map.Entry<DimensionCondition, NameValuePairs> entry : myDefaultDimensionsConditions.entrySet()) {
        DimensionCondition conditions = entry.getKey();
        if (conditions.complies(locator)) {
          NameValuePairs value = entry.getValue();
          Iterator<NameValuePairs.NameValue> iterator = value.iterator();
          while (iterator.hasNext()) {
            NameValuePairs.NameValue next = iterator.next();
            locator.setDimensionIfNotPresent(next.name, next.value);
          }
        }
      }
    }
  }

  class DimensionObjectsImpl implements DimensionObjects {
    private final Map<String, List<?>> myCache = new HashMap<>();
    private final Set<String> myUsedDimensions = new HashSet<>();

    public DimensionObjectsImpl(@NotNull final Locator locator) {
      for (TypedFinderDimensionImpl<?> dimension : myDimensions.values()) {
        List<?> values = getTypedDimensionByLocator(locator, dimension);
        if (values != null && !values.isEmpty()) myCache.put(dimension.getDimension().name, values);
      }
    }

    @Nullable
    private <TYPE> List<TYPE> getTypedDimensionByLocator(@NotNull final Locator locator, @NotNull final TypedFinderDimensionImpl<TYPE> typedDimension) {
      if (Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME.equals(typedDimension.getDimension().name)) {
        String singleValue = locator.lookupSingleValue();
        if (singleValue != null) {
          return Collections.singletonList(getByDimensionValue(typedDimension, singleValue));
        }
      }
      List<String> dimensionValues = locator.lookupDimensionValue(typedDimension.getDimension().name);
      if (dimensionValues.isEmpty()) return null;
      List<TYPE> results = new ArrayList<>(dimensionValues.size());
      for (String dimensionValue : dimensionValues) {
        TYPE result = getByDimensionValue(typedDimension, dimensionValue);
        if (result == null) continue; //dimension returned null (e.g. Boolean "any") - proceed as if not filtering is required by the dimension

        results.add(result);
      }
      return results;
    }

    private <TYPE> TYPE getByDimensionValue(final @NotNull TypedFinderDimensionImpl<TYPE> typedDimension, final String dimensionValue) {
      TYPE result;
      try {
        result = typedDimension.getValueMapper().get(dimensionValue);
      } catch (LocatorProcessException e) {
        if (Locator.HELP_DIMENSION.equals(dimensionValue)) throw e;
        throw new LocatorProcessException("Error in dimension '" + typedDimension.getDimension().name + "', value: '" + dimensionValue + "'", e);
      }
      return result;
    }

    @NotNull
    public Set<String> getUnusedDimensions() {
      HashSet<String> result = new HashSet<>(myCache.keySet());
      result.removeAll(myUsedDimensions);
      return result;
    }

    @Nullable
    @Override
    public <TYPE> List<TYPE> get(@NotNull final Dimension<TYPE> dimension) {
      myUsedDimensions.add(dimension.name);
      return lookup(dimension);
    }

    @Nullable
    @Override
    public <TYPE> List<TYPE> lookup(@NotNull final Dimension<TYPE> dimension) {
      //noinspection unchecked
      return (List<TYPE>)myCache.get(dimension.name);
    }

    @Override
    public Set<String> getUsedDimensions() {
      return myUsedDimensions;
    }
  }
}
