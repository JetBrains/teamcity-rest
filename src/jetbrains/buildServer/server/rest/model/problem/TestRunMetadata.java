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

package jetbrains.buildServer.server.rest.model.problem;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SimpleParameter;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "testRunMetadata")
@XmlType(name = "testRunMetadata", propOrder = {"count", "typedValues"})
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = ObjectType.LIST)
@ModelBaseType(
    value = ObjectType.LIST,
    baseEntity = "TypedValue"
)
public class TestRunMetadata implements DefaultValueAware {
  @XmlElement(name = "typedValues") public List<TypedValue> typedValues;
  @XmlAttribute public Integer count;

  public TestRunMetadata() {
  }

  public TestRunMetadata(@NotNull final jetbrains.buildServer.serverSide.stat.TestRunMetadata testRunMetadata, @NotNull final Fields fields) {
    typedValues = ValueWithDefault.decideDefault(fields.isIncluded("typedValue"), () -> {
      String locatorText = fields.getLocator();
      final ParameterCondition condition = locatorText == null ? null : ParameterCondition.create(new Locator(locatorText)); //todo: allow to filter by type as well
      ArrayList<TypedValue> result = new ArrayList<>();
      for (String name : testRunMetadata.getNames()) {
        final Number numValue = testRunMetadata.getNumValue(name);
        String value = numValue != null ? String.valueOf(numValue) : Objects.requireNonNull(testRunMetadata.getValue(name));
        if (condition == null || condition.parameterMatches(new SimpleParameter(name, value), null)) {
          result.add(new TypedValue(name, testRunMetadata.getType(name), value, fields.getNestedField("typedValue", Fields.SHORT, Fields.LONG)));
        }
      }
      return result;
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), () -> typedValues != null ? typedValues.size() : testRunMetadata.getNames().size());
  }

  @Override
  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, typedValues);
  }
}
