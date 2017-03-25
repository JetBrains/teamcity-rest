/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.TagData;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 26.11.2014
 */
public class TagFinder extends AbstractFinder<TagData> {

  public static final String NAME = "name";
  public static final String PRIVATE = "private";
  public static final String OWNER = "owner";
  protected static final String CONDITION = "condition";

  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildPromotion myBuildPromotion;

  public TagFinder(final @NotNull UserFinder userFinder, final @NotNull BuildPromotion buildPromotion) {
    super(NAME, PRIVATE, OWNER);
    setHiddenDimensions(Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, CONDITION, //experimental
                        DIMENSION_LOOKUP_LIMIT
    );
    myUserFinder = userFinder;
    myBuildPromotion = buildPromotion;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final TagData tagData) {
    final Locator result = Locator.createEmptyLocator();
    result.setDimension(NAME, tagData.getLabel());
    if (tagData.getOwner() != null) result.setDimension(OWNER, myUserFinder.getCanonicalLocator(tagData.getOwner()));
    return result.getStringRepresentation();
  }

  public static Locator getDefaultLocator(){
    Locator defaultLocator = Locator.createEmptyLocator();
    defaultLocator.setDimension(TagFinder.PRIVATE, "false");
    return defaultLocator;
  }

  @NotNull
  @Override
  public ItemHolder<TagData> getPrefilteredItems(@NotNull final Locator locator) {
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
          return item.isPublic() && item.getLabel().equals(singleValue);
        }
      });
      return result;
    }

    final MultiCheckerFilter<TagData> result = new MultiCheckerFilter<TagData>();

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          return nameDimension.equalsIgnoreCase(item.getLabel());
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
}
