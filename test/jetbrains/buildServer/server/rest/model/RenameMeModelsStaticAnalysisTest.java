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

package jetbrains.buildServer.server.rest.model;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.testUtil.ClasspathScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.fail;

/**
 * This class is a replacement for static analysis tool.
 * Tests in this class go through all model classes via reflection and verify if the class sctructure is valid.
 */
public class RenameMeModelsStaticAnalysisTest {

  private List<Class<?>> modelTypes;

  @BeforeClass
  public void setUp() throws IOException, ClassNotFoundException {
    modelTypes = ClasspathScanner.scanForTypes("jetbrains.buildServer.server", (cl) ->
      cl.isAnnotationPresent(ModelDescription.class)
      || cl.isAnnotationPresent(ModelBaseType.class)
      || cl.isAnnotationPresent(XmlRootElement.class)
    );
  }

  @Test
  public void testDummyTest() {
  }

  @DataProvider(name= "modelTypes")
  public Object[][] getModelTypes() {
    return modelTypes.stream().map(it -> new Object[]{it}).collect(Collectors.toList()).toArray(new Object[0][0]);
  }

  @Test(dataProvider = "modelTypes")
  public void testXmlPropertiesAreDistinct(Class<?> type) {
      List<ModelProperty> propertyNamesAnnotated = getPropertyNamesAnnotated(type);
      List<String> duplicatePropertyReports = propertyNamesAnnotated
        .stream()
        .collect(Collectors.groupingBy(it -> it.getFinalXmlName()))
        .entrySet()
        .stream()
        .filter(name2property -> name2property.getValue().size() > 1)
        .map(name2property -> name2property.getKey() + " met " + name2property.getValue().size() + " times")
        .collect(Collectors.toList());
      if (!duplicatePropertyReports.isEmpty()) {
        fail(type.getName() + " has duplicating xml properties: " + duplicatePropertyReports);
      }
  }

  @Test(dataProvider = "modelTypes")
  public void testPropertiesAreListedInXmlTypeAnnotation(Class<?> type) {
      List<ModelProperty> propertyNamesAnnotated = getPropertyNamesAnnotated(type);
      if (type.isAnnotationPresent(XmlType.class)) {
        validateXmlTypeAnnotation(type, propertyNamesAnnotated.stream().map(ModelProperty::getJavaName).collect(Collectors.toList()));
      }
  }

  private List<ModelProperty> getPropertyNamesAnnotated(Class<?> type) {
    Stream<ModelProperty> xmlAttributeProperties = Stream
      .concat(Arrays.stream(type.getDeclaredFields()), Arrays.stream(type.getDeclaredMethods()))
      .filter(member -> member.isAnnotationPresent(XmlAttribute.class))
      .map(member -> new ModelProperty(getPropertyNameForGetter(member), member.getAnnotation(XmlAttribute.class).name()));
    Stream<ModelProperty> xmlElementProperties = Stream
      .concat(Arrays.stream(type.getDeclaredFields()), Arrays.stream(type.getDeclaredMethods()))
      .filter(member -> member.isAnnotationPresent(XmlElement.class))
      .map(member -> new ModelProperty(getPropertyNameForGetter(member), member.getAnnotation(XmlElement.class).name()));
    return Stream.concat(xmlElementProperties, xmlAttributeProperties).collect(Collectors.toList());
  }

  private String getPropertyNameForGetter(Member method) {
    if (method instanceof Field) {
      return method.getName();
    }
    if (method instanceof Method) {
      if (method.getName().startsWith("get")) {
        String propertyNameCapitalized = method.getName().substring("get".length());
        return propertyNameCapitalized.substring(0, 1).toLowerCase() + propertyNameCapitalized.substring(1);
      }
      if (method.getName().startsWith("is")) {
        String propertyNameCapitalized = method.getName().substring("is".length());
        return propertyNameCapitalized.substring(0, 1).toLowerCase() + propertyNameCapitalized.substring(1);
      }
    }
    throw new IllegalArgumentException("???");
  }

  private static void validateXmlTypeAnnotation(Class<?> myType, List<String> propertyNames) {
    List<String> xmlPropOrder = Arrays.stream(myType.getAnnotation(XmlType.class).propOrder()).collect(Collectors.toList());
    try {
      if (xmlPropOrder.equals(Arrays.asList((Object[])XmlType.class.getMethod("propOrder").getDefaultValue()))) {
        return;
      }
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
    List<String> missingPropertyNames = new ArrayList<>(propertyNames);
    missingPropertyNames.removeAll(xmlPropOrder);
    if (!missingPropertyNames.isEmpty()) {
      fail("Properties '" + missingPropertyNames + "' are not listed for '" + myType.getName() + "' in @XmlType(propOrder=" + xmlPropOrder + ")");
    }
    List<String> unknownPropertyNames = new ArrayList<>(xmlPropOrder);
    unknownPropertyNames.removeAll(propertyNames);
    if (!unknownPropertyNames.isEmpty()) {
      fail("Properties '" + unknownPropertyNames + "' are listed in propOrder, but do not exist in class '" + myType.getName() + "'");
    }
  }

  public class ModelProperty {
    private final String javaName;
    @Nullable
    private final String xmlName;

    public ModelProperty(String javaName, @Nullable String xmlName) {
      this.javaName = javaName;
      this.xmlName = xmlName;
    }

    public String getJavaName() {
      return javaName;
    }

    @Nullable
    public String getDeclaredXmlName() {
      return xmlName;
    }

    @NotNull
    public String getFinalXmlName() {
      String xmlName = getDeclaredXmlName();
      return (xmlName != null && !xmlName.equals("##default")) ? xmlName : getJavaName();
    }
  }

}
