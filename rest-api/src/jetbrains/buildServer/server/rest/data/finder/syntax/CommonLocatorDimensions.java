/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.syntax;

import java.util.*;
import java.util.function.Supplier;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.FinderImpl;
import jetbrains.buildServer.server.rest.data.locator.*;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.PlainValue;
import jetbrains.buildServer.server.rest.data.locator.SubDimensionSyntax;
import jetbrains.buildServer.server.rest.data.locator.definition.LocatorDefinition;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommonLocatorDimensions {
  public static final Dimension PAGER_COUNT = Dimension.ofName(PagerData.COUNT).description("For paginated calls, how many entities to return per page.")
                                                       .syntax(PlainValue.int64()).build();
  public static final Dimension PAGER_START = Dimension.ofName(PagerData.START).description("For paginated calls, from which entity to start rendering the page.")
                                                       .syntax(PlainValue.int64()).build();
  public static final Dimension UNIQUE = Dimension.ofName(FinderImpl.DIMENSION_UNIQUE).hidden().build();
  public static final Dimension LOOKUP_LIMIT = Dimension.ofName(FinderImpl.DIMENSION_LOOKUP_LIMIT).description("Limit processing to the latest `<lookupLimit>` entities.")
                                                        .syntax(PlainValue.int64()).hidden().build();

  public static final Dimension PROPERTY = Dimension.ofName(CommonLocatorDimensionsList.PROPERTY).syntax(
    Syntax.TODO("`property:(name:<name>,value:<value>,matchType:<matchType>)` where `matchType` is one of: [" +
                "exists,not-exists,equals,does-not-equal,starts-with,contains,does-not-contain,ends-with,any," +
                "matches,does-not-match,more-than,no-more-than,less-than,no-less-than,ver-more-than,ver-no-more-than,ver-less-than,ver-no-less-than" +
                "]"
    )
  ).build();

  public static Dimension SINGLE_VALUE(@NotNull String valueDescription) {
    return Dimension.ofName(Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME).description(valueDescription).syntax(PlainValue.string()).build();
  }

  public static Dimension SINGLE_VALUE(@NotNull String valueDescription, @NotNull Syntax syntax) {
    return Dimension.ofName(Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME).description(valueDescription).syntax(syntax).build();
  }

  public static Dimension ITEM(@NotNull Supplier<Class<? extends LocatorDefinition>> definition) {
    return Dimension.ofName(FinderImpl.DIMENSION_ITEM).syntax(() -> new SubDimensionSyntaxImpl(definition.get())).hidden().repeatable().build();
  }

  public static Dimension LOGICAL_OR(@NotNull Supplier<Class<? extends LocatorDefinition>> definition) {
    return Dimension.ofName(FinderImpl.LOGIC_OP_OR).syntax(() -> new LogicOpSyntax(new SubDimensionSyntaxImpl(definition.get()))).hidden().build();
  }

  public static Dimension LOGICAL_AND(@NotNull Supplier<Class<? extends LocatorDefinition>> definition) {
    return Dimension.ofName(FinderImpl.LOGIC_OP_AND).syntax(() -> new LogicOpSyntax(new SubDimensionSyntaxImpl(definition.get()))).hidden().build();
  }

  public static Dimension LOGICAL_NOT(@NotNull Supplier<Class<? extends LocatorDefinition>> definition) {
    return Dimension.ofName(FinderImpl.LOGIC_OP_NOT).syntax(() -> new SubDimensionSyntaxImpl(definition.get())).hidden().build();
  }

  /**
   * This class describes a logic operation (`and`, `or`) syntax, where each dimension from the source syntax
   * is now repeatable regardless of was it repeatable initially or not.
   * <p>
   * Example:
   * `buildType:EXTERNAL_ID,count:10,or:(state:finished,state:queued)`
   * this build locator is perfectly valid, while usually it is not allowed to pass `state` dimension twice.
   */
  private static class LogicOpSyntax implements SubDimensionSyntax {
    private final SubDimensionSyntax myDelegate;

    private LogicOpSyntax(@NotNull SubDimensionSyntax delegate) {
      myDelegate = delegate;
    }

    @Override
    public Collection<Dimension> getSubDimensions() {
      Collection<Dimension> originalSubDimensions = myDelegate.getSubDimensions();

      ArrayList<Dimension> wrappedSubdimensions = new ArrayList<>();
      for(Dimension dim : originalSubDimensions) {
        if(dim.isRepeatable()) {
          wrappedSubdimensions.add(dim);
        } else  {
          wrappedSubdimensions.add(new RepeatableWrapper(dim));
        }
      }

      return wrappedSubdimensions;
    }

    @Override
    public String getFormat() {
      return "LogicOpSyntax[" + myDelegate + "]";
    }

    @Override
    public String toString() {
      return getFormat();
    }

    private class RepeatableWrapper implements Dimension {
      private final Dimension myDelegate;
      RepeatableWrapper(@NotNull Dimension delegate) {
        myDelegate = delegate;
      }

      @NotNull
      @Override
      public String getName() {
        return myDelegate.getName();
      }

      @NotNull
      @Override
      public Syntax getSyntax() {
        return myDelegate.getSyntax();
      }

      @Nullable
      @Override
      public String getDescription() {
        return myDelegate.getDescription();
      }

      @Override
      public boolean isHidden() {
        return myDelegate.isHidden();
      }

      @Override
      public boolean isRepeatable() {
        return true;
      }
    }
  }
}
