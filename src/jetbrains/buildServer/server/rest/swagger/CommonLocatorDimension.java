/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.swagger;

import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;

import java.lang.annotation.Annotation;

public class CommonLocatorDimension implements LocatorDimension {
  public final String value;
  public final String dataType;
  public final String format;
  public final String notes;
  public final String allowableValues;

  public CommonLocatorDimension(String value, String dataType, String format, String notes, String allowableValues) {
    this.value = value;
    this.dataType = dataType;
    this.format = format;
    this.notes = notes;
    this.allowableValues = allowableValues;
  }

  @Override
  public String value() {
    return this.value;
  }

  @Override
  public String dataType() {
    return this.dataType;
  }

  @Override
  public String format() {
    return this.format;
  }

  @Override
  public String notes() {
    return this.notes;
  }

  @Override
  public String allowableValues() { return this.allowableValues; }

  @Override
  public Class<? extends Annotation> annotationType() {
    return null;
  }
}
