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

package jetbrains.buildServer.server.rest.util.fieldInclusion;

import com.intellij.openapi.diagnostic.Logger;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.server.rest.model.Fields;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;

public class FieldInclusionChecker {
  private static final Map<Class<?>, FieldInclusionChecker> INSTANCES = new ConcurrentHashMap<>();

  @NotNull
  public static FieldInclusionChecker getForClass(@NotNull Class<?> clazz) {
    if(clazz.getAnnotation(FieldStrategySupported.class) == null)
      throw new IllegalArgumentException(String.format("Given class %s does not support annotation-based field inclusion checks.", clazz.getName()));

    return INSTANCES.computeIfAbsent(clazz, FieldInclusionChecker::new);
  }

  private static final Function<AnnotatedElement, FieldStrategy> DEFAULT_STRATEGY_GENERATOR = m -> new FieldStrategy() {
    @Override
    public Class<? extends Annotation> annotationType() {
      return FieldStrategy.class;
    }

    @NotNull
    @Override
    public String name() {
      return m.toString();
    }

    @NotNull
    @Override
    public FieldRule defaultForShort() {
      return FieldRule.INCLUDE;
    }

    @NotNull
    @Override
    public FieldRule defaultForLong() {
      return FieldRule.INCLUDE;
    }
  };

  private final Map<String, FieldStrategy> myStrategies = new HashMap<>();
  private final Logger myLog;

  private FieldInclusionChecker(@NotNull Class<?> clazz) {
    myLog = Logger.getInstance(String.format("%s for %s", FieldInclusionChecker.class.getName(), clazz.getName()));

    for(Method m : clazz.getMethods()) {
      recordStrategy(m);
    }

    for(Field f : clazz.getFields()) {
      recordStrategy(f);
    }
  }

  private void recordStrategy(@NotNull AnnotatedElement m) {
    Annotation strategyAnnotation = m.getAnnotation(FieldStrategy.class);
    if (strategyAnnotation == null) {
      if(!willBeSerialized(m)) return;

      myLog.info(String.format(
        "%s is not annotated with a %s. This field will always be included into the response.",
        m, FieldStrategy.class.getName())
      );

      myStrategies.put(m.toString(), DEFAULT_STRATEGY_GENERATOR.apply(m));

    } else {
      FieldStrategy rule = (FieldStrategy)strategyAnnotation;
      myStrategies.put(rule.name(), rule);
    }
  }

  private boolean willBeSerialized(@NotNull AnnotatedElement m) {
    return m.getAnnotation(XmlAttribute.class) != null ||
           m.getAnnotation(XmlElement.class) != null;
  }

  public Boolean isIncluded(@NotNull String fieldName, @NotNull Fields fields) {
    FieldStrategy rule = myStrategies.get(fieldName);
    if(rule != null) {
      return fields.isIncluded(fieldName, rule.defaultForShort().asBoolean(), rule.defaultForLong().asBoolean());
    }

    // Let's be pessimistic and treat fields with unknown inclusion rule as included
    return true;
  }

  public Set<String> getAllPotentiallyIncludedFields(@NotNull Fields fields) {
    return myStrategies.values().stream()
                       .filter(rule -> BooleanUtils.isNotFalse(fields.isIncluded(rule.name(), rule.defaultForShort().asBoolean(), rule.defaultForLong().asBoolean())))
                       .map(FieldStrategy::name)
                       .collect(Collectors.toSet());
  }
}