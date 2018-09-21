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

package jetbrains.buildServer.server.rest.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.Finder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 20/09/2018
 */
@SuppressWarnings({"PublicField", "WeakerAccess"})
@XmlRootElement(name = "multipleOperationResult")
public class MultipleOperationResult {
    @XmlAttribute
    public Integer count;
    @XmlAttribute
    public Integer errorCount;

    @XmlElement(name = "operationResult")
    public List<OperationResult> operationResults;

    @SuppressWarnings("unused")
    public MultipleOperationResult() {
    }

    public MultipleOperationResult(Data data, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
      operationResults = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("operationResult"), () -> {
        Fields nestedFields = fields.getNestedField("operationResult", Fields.LONG, Fields.LONG);
        return data.myData.stream().map(d -> new OperationResult(d, nestedFields, beanContext)).collect(Collectors.toList());
      });
      count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), data.myData.size());
      errorCount = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("errorCount"), data.myErrorCount);
    }

    public static class Data {
      private final Collection<OperationResult.Data> myData;
      private int myErrorCount;

      public Data(@NotNull List<OperationResult.Data> data, final int errorCount) {
        myData = data;
        myErrorCount = errorCount;
      }

      @NotNull
      public static <T> Data process(@Nullable final String locator, @NotNull final Finder<T> finder, @NotNull Consumer<T> action) {
        if (locator == null){
          throw new BadRequestException("Empty locator specified.");
        }
        List<T> items = finder.getItems(locator).myEntries;
        Data result = new Data(new ArrayList<>(), 0);
        items.forEach(item -> {
          try {
            action.accept(item);
            result.myData.add(OperationResult.Data.createSuccess(new RelatedEntity.Entity(item)));
          } catch (Exception e) {
            result.myErrorCount++;
            result.myData.add(OperationResult.Data.createError(e.getMessage(), new RelatedEntity.Entity(item)));
          }
        });
        return result;
      }
    }
}
