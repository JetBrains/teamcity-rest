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

package jetbrains.buildServer.server.rest.model.project;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "labeledValue")
public class LabeledValue {
  private String myLabel;
  private String myValue;

  public LabeledValue() { }

  public LabeledValue(@NotNull String value, @Nullable String label) {
    myValue = value;
    if(!Objects.equals(value, label)) {
      myLabel = label;
    }
  }

  @XmlAttribute(name = "label")
  public String getLabel() {
    return myLabel;
  }

  public void setLabel(String label) {
    myLabel = label;
  }

  @XmlAttribute(name = "value")
  public String getValue() {
    return myValue;
  }

  public void setValue(String value) {
    myValue = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LabeledValue that = (LabeledValue)o;
    return Objects.equals(myLabel, that.myLabel) && Objects.equals(myValue, that.myValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myLabel, myValue);
  }

  @Override
  public String toString() {
    if(myLabel == null) {
      return myValue;
    }

    return myLabel + "=>" + myValue;
  }
}
