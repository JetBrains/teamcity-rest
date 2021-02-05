/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.build;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.TagData;
import jetbrains.buildServer.tags.TagsManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Yegor.Yarko
 *         Date: 26.11.2014
 */
@LocatorResource(
    value = LocatorName.TAG,
    baseEntity = "Tag"
)
public class TagFinder extends AbstractFinder<TagData> {

  @LocatorDimension("name") public static final String NAME = "name";
  @LocatorDimension("private") public static final String PRIVATE = "private";
  @LocatorDimension("owner") public static final String OWNER = "owner";
  protected static final String CONDITION = "condition";

  @NotNull private final UserFinder myUserFinder;
  private final BuildPromotion myBuildPromotion;

  public TagFinder(final @NotNull UserFinder userFinder, final @NotNull BuildPromotion buildPromotion) {
    this(userFinder, buildPromotion, true);
  }

  private TagFinder(final @NotNull UserFinder userFinder, @Nullable final BuildPromotion buildPromotion, @SuppressWarnings("unused") boolean internalConstructor) {
    super(NAME, PRIVATE, OWNER);
    setHiddenDimensions(Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, CONDITION, //experimental
                        DIMENSION_LOOKUP_LIMIT
    );
    myUserFinder = userFinder;
    myBuildPromotion = buildPromotion;
  }

  public static boolean isIncluded(@NotNull final BuildPromotion item, @Nullable final String singleTag, @NotNull final UserFinder userFinder) {
    return new TagFinder(userFinder, item).getItems(singleTag, getDefaultLocator()).myEntries.size() > 0; //the code should correspond to locator.isSingleValue() case processing in getFilter method
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final TagData tagData) {
    final Locator result = Locator.createEmptyLocator();
    result.setDimension(NAME, tagData.getLabel());
    if (tagData.getOwner() != null) result.setDimension(OWNER, myUserFinder.getCanonicalLocator(tagData.getOwner()));
    return result.getStringRepresentation();
  }

  @NotNull
  public static Locator getDefaultLocator(){
    Locator defaultLocator = Locator.createEmptyLocator();
    defaultLocator.setDimension(TagFinder.PRIVATE, "false");
    return defaultLocator;
  }

  @NotNull
  @Override
  public ItemHolder<TagData> getPrefilteredItems(@NotNull final Locator locator) {
    if (myBuildPromotion == null) throw new OperationException("Attempt to use the tags locator without setting build");

    final ArrayList<TagData> result = new ArrayList<TagData>(myBuildPromotion.getTagDatas());
    Collections.sort(result, new Comparator<TagData>() {
      public int compare(final TagData o1, final TagData o2) {
        if (o1 == o2) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;

        if (o1.isPublic()){
          if (o2.isPublic()){
            return o1.getLabel().compareToIgnoreCase(o2.getLabel());
          }
          return -1;
        }
        if (o2.isPublic()){
          return 1;
        }
        final SUser user1 = o1.getOwner();
        final SUser user2 = o2.getOwner();
        if (user1 == user2 || user1 == null || user2 == null) return o1.getLabel().compareToIgnoreCase(o2.getLabel());
        return user1.getUsername().compareToIgnoreCase(user2.getUsername());
      }
    });
    return getItemHolder(result);
  }

  @NotNull
  @Override
  public ItemFilter<TagData> getFilter(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      final String singleValue = locator.getSingleValue();
      final MultiCheckerFilter<TagData> result = new MultiCheckerFilter<TagData>();
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          return item.isPublic() && item.getLabel().equals(singleValue); //the code should correspond to TagFinder.isIncluded method
        }
      });
      return result;
    }

    final MultiCheckerFilter<TagData> result = new MultiCheckerFilter<TagData>();

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          return nameDimension.equalsIgnoreCase(item.getLabel()); //conditions are supported via "condition" dimension
        }
      });
    }

    final Boolean privateDimension = locator.getSingleDimensionValueAsBoolean(PRIVATE);
    if (privateDimension != null) {
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          return FilterUtil.isIncludedByBooleanFilter(privateDimension, item.getOwner() != null);
        }
      });
    }

    final String ownerLocator = locator.getSingleDimensionValue(OWNER);
    if (ownerLocator != null) {
      final SUser user = myUserFinder.getItem(ownerLocator);
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          final SUser owner = item.getOwner();
          if (privateDimension == null && owner == null) {
            //locator "private:any,owner:<user>" should return all public and private of the user (the defaults)
            return true;
          }
          return user.equals(owner);
        }
      });
    }

    final String condition = locator.getSingleDimensionValue(CONDITION);
    if (condition != null) {
      final ValueCondition parameterCondition = ParameterCondition.createValueCondition(condition);
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          return parameterCondition.matches(item.getLabel());
        }
      });
    }

    return result;
  }

  /**
   * Gets superset of builds, which can be matched by the tagLocator tag locator. More builds can be returned, so additional filtering is necessary
   * @return null if cannot construct the set effectively
   */
  @Nullable
  public static Stream<BuildPromotion> getPrefilteredFinishedBuildPromotions(@NotNull final List<String> tagLocators, @NotNull final ServiceLocator serviceLocator) {
    FilterOptions filterOptions = getFilterOptions(tagLocators, serviceLocator);
    if (filterOptions == null) return null;

    if (filterOptions.getTagOwner() != null) {
      Stream<BuildPromotion> finishedBuilds = serviceLocator.getSingletonService(TagsManager.class).findAll(filterOptions.getTagName(), filterOptions.getTagOwner()).stream()
                                                            .map(build -> ((SBuild)build).getBuildPromotion());
      finishedBuilds = finishedBuilds.sorted(BuildPromotionFinder.BUILD_PROMOTIONS_COMPARATOR); //workaround for TW-53934
      return finishedBuilds;
    }

    Stream<BuildPromotion> finishedBuilds =
      serviceLocator.getSingletonService(TagsManager.class).findAll(filterOptions.getTagName()).stream().map(build -> ((SBuild)build).getBuildPromotion());
    finishedBuilds = finishedBuilds.sorted(BuildPromotionFinder.BUILD_PROMOTIONS_COMPARATOR); //workaround for TW-53934
    return finishedBuilds;
  }

  @Nullable
  public static FilterOptions getFilterOptions(@NotNull final List<String> tagLocators, @NotNull final ServiceLocator serviceLocator) {
    //todo: try to optimize performance by filtering by the one with exact match before others (if present)
    //todo: consider making "tag" locator case sensitive by default
    if (tagLocators.size() != 1) return null; //so far supporting only single tag filter

    String tagLocator = tagLocators.get(0);

    TagFinder tagFinder = new TagFinder(serviceLocator.getSingletonService(UserFinder.class), null, true);
    Locator locator = tagFinder.createLocator(tagLocator, getDefaultLocator()); //the locator is not checked later with checkFullyProcessed as the result of the method is partial and the locator should still be processed later

    if (locator.getSingleDimensionValue(NAME) != null) return null; //no effective API to filter by tag name in the case-insensitive way as it is supported by the main filter

    String tagName = locator.getSingleValue();

    final String condition = locator.getSingleDimensionValue(CONDITION);
    if (condition != null) {
      final ValueCondition valueCondition = ParameterCondition.createValueCondition(condition);
      tagName = valueCondition.getConstantValueIfSimpleEqualsCondition();
    }
    if (tagName == null) return null; //no case sensitive tag name is set


    final Boolean privateDimension = locator.isSingleValue() ? Boolean.FALSE : locator.getSingleDimensionValueAsBoolean(PRIVATE); //this is set to false by locator defaults

    final String ownerLocator = locator.getSingleDimensionValue(OWNER);
    if (ownerLocator != null && privateDimension != null && privateDimension) {
      SUser user = tagFinder.myUserFinder.getItem(ownerLocator);
      return new FilterOptions(tagName, user);
    }

    if (ownerLocator == null && privateDimension != null && !privateDimension) {
      return new FilterOptions(tagName, null);
    }

    return null;
  }

  public static class FilterOptions {
    @NotNull private final String myTagName;
    @Nullable private final User myTagOwner;

    FilterOptions(@NotNull final String tagName, @Nullable User tagOwner) {
      myTagName = tagName;
      myTagOwner = tagOwner;
    }

    @NotNull
    public String getTagName() {
      return myTagName;
    }

    @Nullable
    public User getTagOwner() {
      return myTagOwner;
    }
  }
}
