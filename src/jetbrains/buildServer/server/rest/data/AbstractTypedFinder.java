/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.InheritableUserParametersHolder;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 27/04/2016
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractTypedFinder<ITEM> extends AbstractFinder<ITEM> {
  //still to implement:
  //sort result or return ItemProcessor

  //consider adding additional checks on condifured dimensions: validate that all dimensions were used: that checker is added or it participated in toItems, etc.
  //consider adding special toItem (single) configuration to process them first

  private final LinkedHashMap<String, TypedFinderDimensionImpl> myDimensions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionConditionsImpl, NameValuePairs> myDefaultDimensionsConditions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionConditionsImpl, ItemsFromDimensions<ITEM>> myItemsConditions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionConditionsImpl, ItemHolderFromDimensions<ITEM>> myItemHoldersConditions = new LinkedHashMap<>();
  private final LinkedHashMap<DimensionConditionsImpl, ItemFilterFromDimensions<ITEM>> myFiltersConditions = new LinkedHashMap<>();
  private ItemsFromDimension<ITEM, String> mySingleDimensionHandler;

  public interface TypedFinderDimension<ITEM, TYPE> {
    @NotNull
    TypedFinderDimension<ITEM, TYPE> description(@NotNull String description);

    @NotNull
    TypedFinderDimension<ITEM, TYPE> hidden();

    @NotNull
    TypedFinderDimension<ITEM, TYPE> withDefault(@NotNull String value);

    @NotNull
    TypedFinderDimension<ITEM, TYPE> filter(@NotNull Filter<TYPE, ITEM> filter);

    @NotNull
    TypedFinderDimension<ITEM, TYPE> toItems(@NotNull ItemsFromDimension<ITEM, TYPE> filteringMapper);

    @NotNull
    TypedFinderDimension<ITEM, TYPE> dimensionChecker(@NotNull Checker<TYPE> checker);

    @NotNull
    <TYPE_FOR_FILTER> TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> defaultFilter(@NotNull Filter<TYPE, TYPE_FOR_FILTER> checker);
  }

  public interface TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> extends TypedFinderDimension<ITEM, TYPE> {
    @NotNull
    TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> valueForDefaultFilter(@NotNull TypeFromItem<TYPE_FOR_FILTER, ITEM> retriever);

    // all the same as in parent interface, with more precise return type

    @NotNull
    @Override
    TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> description(@NotNull String description);

    @NotNull
    @Override
    TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> hidden();

    @NotNull
    @Override
    TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> withDefault(@NotNull String value);

    @NotNull
    @Override
    TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> filter(@NotNull Filter<TYPE, ITEM> checker);

    @NotNull
    @Override
    TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> toItems(@NotNull ItemsFromDimension<ITEM, TYPE> filteringMapper);

    @NotNull
    @Override
    TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> dimensionChecker(@NotNull Checker<TYPE> checker);
  }

  @SuppressWarnings("WeakerAccess")
  public class TypedFinderDimensionImpl<TYPE> implements TypedFinderDimension<ITEM, TYPE> {
    @NotNull protected final Dimension<TYPE> myDimension;
    @NotNull protected final Type<TYPE> myType;
    protected Checker<TYPE> myChecker = null;
    protected String myDescription = null;
    protected Boolean myHidden = null;

    public TypedFinderDimensionImpl(@NotNull final Dimension<TYPE> dimension, @NotNull final Type<TYPE> type) {
      myDimension = dimension;
      myType = type;
    }

    @NotNull
    public Dimension<TYPE> getDimension() {
      return myDimension;
    }

    @NotNull
    public Type<TYPE> getType() {
      return myType;
    }

    @Nullable
    public Checker<TYPE> getChecker() {
      return myChecker;
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
    public TypedFinderDimension<ITEM, TYPE> dimensionChecker(@NotNull final Checker<TYPE> checker) {
      myChecker = checker;
      return this;
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
      defaults(DimensionConditionsImpl.ALWAYS, new NameValuePairs().add(getDimension().name, value));
      return this;
    }

    @Override
    @NotNull
    public TypedFinderDimension<ITEM, TYPE> filter(@NotNull final Filter<TYPE, ITEM> filter) {
      AbstractTypedFinder.this.filter(getDimension(), filter);
      return this;
    }

    @Override
    @NotNull
    public TypedFinderDimension<ITEM, TYPE> toItems(@NotNull final ItemsFromDimension<ITEM, TYPE> filteringMapper) {
      multipleConvertToItems(new DimensionConditionsImpl().when(getDimension(), Condition.PRESENT), new ItemsFromDimensions<ITEM>() {
        @Nullable
        @Override
        public List<ITEM> get(@NotNull final DimensionObjects dimensions) {
          List<TYPE> dimensionValues = dimensions.get(getDimension());
          if (dimensionValues == null || dimensionValues.isEmpty()) return null;
          Set<ITEM> result = new LinkedHashSet<ITEM>(); //this requires due hash function for the ITEM - might need review!
          for (TYPE dimensionValue : dimensionValues) {
            List<ITEM> items = filteringMapper.get(dimensionValue);
            if (items == null) continue;
            if (result.isEmpty()) {
              result.addAll(items);
            } else {
              result = CollectionsUtil.intersect(items, result);
            }
            if (result.isEmpty()) return new ArrayList<ITEM>(result); //already empty intersection -> exit early
          }
          return new ArrayList<ITEM>(result);
        }
      });
      return this;
    }

    @Override
    @NotNull
    public <TYPE_FROM_ITEM> TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FROM_ITEM> defaultFilter(@NotNull final Filter<TYPE, TYPE_FROM_ITEM> checker) {
      return new TypedFinderDimensionWithDefaultCheckerImpl<>(this, checker);
    }
  }

  private class TypedFinderDimensionWithDefaultCheckerImpl<TYPE, TYPE_FOR_FILTER> implements TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> {
    @NotNull private final TypedFinderDimensionImpl<TYPE> myOriginal;
    @NotNull private final Filter<TYPE, TYPE_FOR_FILTER> myDefaultChecker;

    private TypedFinderDimensionWithDefaultCheckerImpl(final @NotNull TypedFinderDimensionImpl<TYPE> original,
                                                       final @NotNull Filter<TYPE, TYPE_FOR_FILTER> defaultChecker) {
      myOriginal = original;
      myDefaultChecker = defaultChecker;
    }

    @NotNull
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> valueForDefaultFilter(@NotNull TypeFromItem<TYPE_FOR_FILTER, ITEM> retriever) {
      AbstractTypedFinder.this.filter(myOriginal.getDimension(), new Filter<TYPE, ITEM>() {
        @Override
        public boolean isIncluded(@NotNull final TYPE value, @NotNull final ITEM item) {
          return myDefaultChecker.isIncluded(value, retriever.get(item));
        }
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

    @NotNull
    @Override
    public TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> dimensionChecker(@NotNull final Checker<TYPE> checker) {
      myOriginal.dimensionChecker(checker);
      return this;
    }
  }

  TypedFinderDimension<ITEM, Long> dimensionLong(@NotNull final Dimension<Long> dimension) {
    return dimension(dimension, type(dimensionValue -> {
      try {
        return Long.parseLong(dimensionValue);
      } catch (NumberFormatException e) {
        throw new LocatorProcessException("Invalid value '" + dimensionValue + "'. Should be a number.");
      }
    }).description("number"));
  }

  TypedFinderDimension<ITEM, String> dimensionString(@NotNull final Dimension<String> dimension) {
    return dimension(dimension, type(dimensionValue -> dimensionValue).description("text"));
  }

  TypedFinderDimensionWithDefaultChecker<ITEM, Boolean, Boolean> dimensionBoolean(@NotNull final Dimension<Boolean> dimension) {
    return dimension(dimension, type(dimensionValue -> Locator.getBooleanByValue(dimensionValue)).description("boolean"))
      .defaultFilter((value, item) -> FilterUtil.isIncludedByBooleanFilter(value, item));
  }

  TypedFinderDimension<ITEM, List<BuildPromotion>> dimensionBuildPromotions(@NotNull final Dimension<List<BuildPromotion>> dimension,
                                                                            @NotNull final ServiceLocator serviceLocator) {
    return dimension(dimension, type(dimensionValue -> {
      final BuildPromotionFinder buildPromotionFinder = serviceLocator.getSingletonService(BuildPromotionFinder.class);
      return buildPromotionFinder.getItems(dimensionValue).myEntries;
    }).description("build locator"));
  }

  TypedFinderDimensionWithDefaultChecker<ITEM, ParameterCondition, ParametersProvider> dimensionParameterCondition(@NotNull final Dimension<ParameterCondition> dimension) {
    return dimension(dimension, type(dimensionValue -> ParameterCondition.create(dimensionValue)).description("parameter condition"))
      .defaultFilter((parameterCondition, item) -> parameterCondition.matches(item));
  }

  TypedFinderDimensionWithDefaultChecker<ITEM, ParameterCondition, InheritableUserParametersHolder> dimensionOwnParameterCondition(@NotNull final Dimension<ParameterCondition> dimension) {
    return dimension(dimension, type(dimensionValue -> ParameterCondition.create(dimensionValue)).description("parameter condition with own support"))
      .defaultFilter((parameterCondition, item) -> parameterCondition.matches(item));
  }

  TypedFinderDimensionWithDefaultChecker<ITEM, ValueCondition, String> dimensionValueCondition(@NotNull final Dimension<ValueCondition> dimension) {
    return dimension(dimension, type(dimensionValue -> ParameterCondition.createValueCondition(dimensionValue)).description("value condition"))
      .defaultFilter((valueCondition, item) -> valueCondition.matches(item));
  }

  TypedFinderDimensionWithDefaultChecker<ITEM, TimeCondition.ParsedTimeCondition, Date> dimensionTimeCondition(@NotNull final Dimension<TimeCondition.ParsedTimeCondition> dimension,
                                                                                                               @NotNull final TimeCondition timeCondition) {
    return dimension(dimension, type(dimensionValue -> timeCondition.getTimeCondition(dimensionValue)).description("time condition"))
      .defaultFilter((parsedTimeCondition, item) -> parsedTimeCondition.matches(item));
  }


  //============================= Main definition methods =============================

  <TYPE> TypedFinderDimension<ITEM, TYPE> dimension(@NotNull final Dimension<TYPE> dimension, @NotNull final Type<TYPE> typeMapper) { //typeMapper: dimensionValue->typed object
    if (myDimensions.containsKey(dimension.name)) throw new OperationException("Dimension with name '" + dimension.name + "' was already added");
    @NotNull TypedFinderDimensionImpl<TYPE> value = new TypedFinderDimensionImpl<>(dimension, typeMapper);
    myDimensions.put(dimension.name, value);
    return value;
  }

  void singleDimension(@NotNull final ItemsFromDimension<ITEM, String> singleDimensionHandler) {
    mySingleDimensionHandler = singleDimensionHandler;
  }

  AbstractTypedFinder<ITEM> multipleConvertToItems(@NotNull final DimensionConditionsImpl conditions,
                                                   @NotNull final ItemsFromDimensions<ITEM> parsedObjectsIfConditionsMatched) {
    myItemsConditions.put(conditions, parsedObjectsIfConditionsMatched);
    return this;
  }

  AbstractTypedFinder<ITEM> multipleConvertToItemHolder(@NotNull final DimensionConditionsImpl conditions,
                                                        @NotNull final ItemHolderFromDimensions<ITEM> parsedObjectsIfConditionsMatched) {
    myItemHoldersConditions.put(conditions, parsedObjectsIfConditionsMatched);
    return this;
  }

  AbstractTypedFinder<ITEM> filter(@NotNull final DimensionConditionsImpl conditions, @NotNull final ItemFilterFromDimensions<ITEM> parsedObjectsIfConditionsMatched) {
    myFiltersConditions.put(conditions, parsedObjectsIfConditionsMatched);
    return this;
  }


  //============================= helper methods =============================

  private AbstractTypedFinder<ITEM> defaults(@NotNull final DimensionConditionsImpl conditions, @NotNull final NameValuePairs defaults) {
    myDefaultDimensionsConditions.put(conditions, defaults);
    return this;
  }

  private <TYPE> AbstractTypedFinder<ITEM> filter(@NotNull final Dimension<TYPE> dimension, @NotNull final Filter<TYPE, ITEM> filteringMapper) {
    return filter(new DimensionConditionsImpl().when(dimension, Condition.PRESENT), new ItemFilterFromDimensions<ITEM>() {
      @Nullable
      @Override
      public ItemFilter<ITEM> get(@NotNull final DimensionObjects dimensions) {
        final List<TYPE> values = dimensions.get(dimension);
        if (values == null || values.isEmpty()) return null;
        MultiCheckerFilter<ITEM> result = new MultiCheckerFilter<ITEM>();
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
      }
    });
  }

  public <TYPE> TypeBuilder<TYPE> type(@NotNull final TypeBuilder.ValueRetriever<TYPE> retriever) {
    return new TypeBuilder<>(retriever);
  }

  //============================= Public subclasses =============================

  @SuppressWarnings("unused")
  public static class Dimension<TYPE> { //type is important here to let type inference for all the rest of the class usages
    @NotNull public final String name;

    public Dimension(@NotNull final String name) {
      if (name.length() == 0) throw new OperationException("Wrong name: empty");
      this.name = name;
    }

    @Override
    public String toString() {
      return "Dimension '" + name + "'";
    }
  }

  public static abstract class Type<TYPE> {
    @Nullable
    String getDescription() {
      return null;
    }

    @Nullable
    abstract TYPE get(@NotNull final String dimensionValue);
  }

  public static class TypeBuilder<TYPE> extends Type<TYPE> {
    @NotNull private final ValueRetriever<TYPE> myRetriever;
    @Nullable private String myDescription;

    public TypeBuilder(@NotNull final ValueRetriever<TYPE> retriever) {
      myRetriever = retriever;
    }

    @Nullable
    String getDescription() {
      return myDescription;
    }

    @Nullable
    TYPE get(@NotNull final String dimensionValue) {
      return myRetriever.get(dimensionValue);
    }

    @NotNull
    TypeBuilder<TYPE> description(@NotNull String description) {
      myDescription = description;
      return this;
    }

    public interface ValueRetriever<TYPE> {
      @Nullable
      TYPE get(@NotNull String dimensionValue);
    }
  }


  public static abstract class Checker<T> {
    @NotNull
    abstract T wrap(@NotNull final T value);
  }

  public static interface DimensionObjects {
    @Nullable
    <TYPE> List<TYPE> get(@NotNull Dimension<TYPE> dimension);
  }

  public static interface DimensionConditions {
    @NotNull
    DimensionConditions when(@NotNull Dimension dimension, @NotNull Condition conditionBasedOnValue);
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


  public static interface ItemsFromDimension<ITEM, TYPE> {
    /**
     * @return the list of items matching dimensions, 'null' is this provider should be ignored
     */
    @Nullable
    List<ITEM> get(@NotNull final TYPE dimension);
  }

  public static interface ItemsFromDimensions<ITEM> {
    /**
     * @return the list of items matching dimensions, 'null' is this provider should be ignored
     */
    @Nullable
    List<ITEM> get(@NotNull final DimensionObjects dimensions);
  }

  public static interface ItemHolderFromDimensions<ITEM> {
    @NotNull
    ItemHolder<ITEM> get(@NotNull final DimensionObjects dimensions);
  }

  public static interface ItemFilterFromDimensions<ITEM> {
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

  public static interface Filter<CHECK_VALUE, ACTUAL_VALUE> {
    boolean isIncluded(@NotNull CHECK_VALUE value, @NotNull ACTUAL_VALUE item);

  }

  public static interface TypeFromItem<TYPE, ITEM> {
    @NotNull
    TYPE get(@NotNull final ITEM item);
  }

  //============================= Helper subclasses =============================

  static class DimensionConditionsImpl implements DimensionConditions {
    @NotNull private final List<DimensionCondition> conditions = new ArrayList<>();

    @NotNull
    public DimensionConditionsImpl when(@NotNull Dimension dimension, @NotNull Condition conditionBasedOnValue) {
      conditions.add(new DimensionCondition(dimension, conditionBasedOnValue));
      return this;
    } //later can add context here (pass as input only only dimension value, but also some context returned from the previous conditions)

    boolean complies(@NotNull Locator locator) {
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

    static final DimensionConditionsImpl ALWAYS = new DimensionConditionsImpl() {
      @Override
      boolean complies(@NotNull final Locator locator) {
        return true;
      }

      @Override
      public String toString() {
        return "Conditions 'always'";
      }
    };

    @Override
    public String toString() {
      return "Conditions: {" + StringUtil.join(conditions, item -> item.toString(), ", ") + "}";
    }

    private static class DimensionCondition {
      @NotNull private final Dimension dimension;
      @NotNull private final Condition conditionBasedOnValue;
      //later can add context here (pass as input only dimension value, but also some context returned from the previous conditions)
      // use builders to construct Check

      DimensionCondition(@NotNull final Dimension dimension, @NotNull final Condition conditionBasedOnValue) {
        this.dimension = dimension;
        this.conditionBasedOnValue = conditionBasedOnValue;
      }

      @Override
      public String toString() {
        return dimension.toString() + " -> " + conditionBasedOnValue.toString();
      }
    }
  }


  private static class NameValuePairs {
    private final TreeMap<String, String> myPairs = new TreeMap<>();

    NameValuePairs add(@NotNull String name, @NotNull String value) {
      myPairs.put(name, value);
      return this;
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

  protected static final String HELP_DIMENSION = "$help";

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator locator = super.createLocator(locatorText, locatorDefaults);
    for (TypedFinderDimensionImpl dimension : myDimensions.values()) {
      if (dimension.getHidden()) locator.addHiddenDimensions(dimension.getDimension().name);
    }
    locator.addHiddenDimensions(HELP_DIMENSION);
    locator.setDescriptionProvider(includeHidden -> {
      StringBuilder result = new StringBuilder();
      result.append("Supported locator dimensions:\n");
      for (TypedFinderDimensionImpl dimension : myDimensions.values()) {
        if (!includeHidden && dimension.getHidden() && locator.getDimensionValue(dimension.getDimension().name).isEmpty() && (locator.getDimensionValue(HELP_DIMENSION).isEmpty())) {
          continue;
        }
        result.append(dimension.getDimension().name);
        String dimensionDescription = dimension.getDescription();
        if (dimensionDescription != null) {
          result.append(" - ");
          result.append(dimensionDescription);
        }
        String typeDescription = dimension.getType().getDescription();
        if (typeDescription != null) {
          result.append(" (type: ").append(typeDescription).append(")");
        }
        result.append("\n");
      }
      return result.toString();
    });
    return locator;
  }

  @NotNull
  @Override
  public String[] getKnownDimensions() {
    ArrayList<String> result = new ArrayList<>();
    for (TypedFinderDimensionImpl dimension : myDimensions.values()) {
      if (!dimension.getHidden()) result.add(dimension.getDimension().name);
    }
    return CollectionsUtil.toArray(result, String.class);
  }


  @Override
  protected ItemHolderAndFilter<ITEM> getItemHolderAndFilter(@NotNull final Locator locator) {
    final DimensionObjectsImpl dimensionObjects = new DimensionObjectsImpl(locator);

    final ItemHolder<ITEM> itemHolderResult = getPrefilteredItems(locator, dimensionObjects);
    final ItemFilter<ITEM> itemFilterResult = getFilter(locator, dimensionObjects);

    // proper mark unused dimensions if they were not queried
    Set<String> unusedDimensions = dimensionObjects.getUnusedDimensions();
    for (String unusedDimension : unusedDimensions) {
      locator.markUnused(unusedDimension);
    }

    return new ItemHolderAndFilter<ITEM>() {
      @NotNull
      @Override
      public ItemHolder<ITEM> getItemHolder() {
        return itemHolderResult;
      }

      @NotNull
      @Override
      public ItemFilter<ITEM> getItemFilter() {
        return itemFilterResult;
      }
    };
  }

  @NotNull
  protected ItemHolder<ITEM> getPrefilteredItems(@NotNull final Locator locator, @NotNull DimensionObjectsImpl dimensionObjects) {
    if (mySingleDimensionHandler != null && locator.isSingleValue()) {
      //noinspection ConstantConditions
      List<ITEM> items = mySingleDimensionHandler.get(locator.getSingleValue());
      if (items == null) throw new OperationException("Single value items provider returned 'null', but it cannot be ignored");
      return getItemHolder(items);
    }

    for (Map.Entry<DimensionConditionsImpl, NameValuePairs> entry : myDefaultDimensionsConditions.entrySet()) {
      DimensionConditionsImpl conditions = entry.getKey();
      if (conditions.complies(locator)) {
        NameValuePairs value = entry.getValue();
        Iterator<NameValuePairs.NameValue> iterator = value.iterator();
        while (iterator.hasNext()) {
          NameValuePairs.NameValue next = iterator.next();
          locator.setDimensionIfNotPresent(next.name, next.value);
        }
      }
    }

    for (DimensionConditionsImpl conditions : myItemsConditions.keySet()) {
      if (conditions.complies(locator)) {
        ItemsFromDimensions<ITEM> itemsFromDimensions = myItemsConditions.get(conditions);
        List<ITEM> itemsList = itemsFromDimensions.get(dimensionObjects);
        if (itemsList != null) return getItemHolder(itemsList);
        //add debug mode to collect all toItems and report their sizes if antoher order would be more effective
      }
      //consider processing other items as well and intersecting???

    }
    for (Map.Entry<DimensionConditionsImpl, ItemHolderFromDimensions<ITEM>> entry : myItemHoldersConditions.entrySet()) {
      if (entry.getKey().complies(locator)) {
        return entry.getValue().get(dimensionObjects);
      }
      //consider processing other items as well and intersecting???
    }

    throw new OperationException("No conditions matched"); //exception type
  }


  @NotNull
  protected ItemFilter<ITEM> getFilter(@NotNull final Locator locator, @NotNull DimensionObjectsImpl dimensionObjects) {
    MultiCheckerFilter<ITEM> result = new MultiCheckerFilter<>();
    for (Map.Entry<DimensionConditionsImpl, ItemFilterFromDimensions<ITEM>> entry : myFiltersConditions.entrySet()) {
      final Set<String> alreadyUsedDimensions = new HashSet<>(dimensionObjects.myUsedDimensions);
      if (entry.getKey().complies(locator)) {
        final Set<String> usedDimensions = new HashSet<>();
        ItemFilter<ITEM> checker = entry.getValue().get(new DimensionObjects() {
          @Nullable
          @Override
          public <TYPE> List<TYPE> get(@NotNull final Dimension<TYPE> dimension) {
            usedDimensions.add(dimension.name);
            return dimensionObjects.get(dimension);
          }
        });
        if (checker == null) continue;
        if (alreadyUsedDimensions.containsAll(usedDimensions)) continue; //all the dimensions were already used
        result.add(checker); //also support shouldStop
      }
    }
    return result;
  }

  @NotNull
  @Override
  protected ItemHolder<ITEM> getPrefilteredItems(@NotNull final Locator locator) {
    throw new OperationException("Should not be called when getItemHolderAndFilter is implemented");
  }

  @NotNull
  @Override
  protected ItemFilter<ITEM> getFilter(@NotNull final Locator locator) {
    throw new OperationException("Should not be called when getItemHolderAndFilter is implemented");
  }

  class DimensionObjectsImpl implements DimensionObjects {
    private final Map<String, List> myCache = new HashMap<>();
    private final Set<String> myUsedDimensions = new HashSet<>();

    public DimensionObjectsImpl(@NotNull final Locator locator) {
      for (TypedFinderDimensionImpl dimension : myDimensions.values()) {
        List values = getTypedDimensionByLocator(locator, dimension);
        if (values != null && !values.isEmpty()) myCache.put(dimension.getDimension().name, values);
      }
    }

    @Nullable
    private <TYPE> List<TYPE> getTypedDimensionByLocator(@NotNull final Locator locator, @NotNull final TypedFinderDimensionImpl<TYPE> typedDimension) {
      //noinspection unchecked
      List<String> dimensionValues = locator.getDimensionValue(typedDimension.getDimension().name);
      if (dimensionValues.isEmpty()) return null;
      List<TYPE> results = new ArrayList<>(dimensionValues.size());
      for (String dimensionValue : dimensionValues) {
        TYPE result;
        try {
          result = typedDimension.getType().get(dimensionValue);
        } catch (LocatorProcessException e) {
          throw new LocatorProcessException("Error in dimension '" + typedDimension.getDimension().name + "', value: '" + dimensionValue + "'", e);
        }
        if (result == null) continue; //dimension returned null (e.g. Boolean "any") - proceed as if not filtering is required by the dimension
        Checker<TYPE> checker = typedDimension.getChecker();
        if (checker != null) {
          results.add(checker.wrap(result));
        } else {
          results.add(result);
        }
      }
      return results;
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
      //noinspection unchecked
      return (List<TYPE>)myCache.get(dimension.name);
    }
  }
}


