/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.diagnostic;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Entity for abstract graphic axis.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "axis")
@XmlType
public class Axis {

  /**
   * Any description of data type. Like 'percent', 'time', 'absolute' or so.
   */
  @XmlElement
  public String type;
  /**
   * Name of axis.
   */
  @XmlElement
  public String name;
  /**
   * Values
   */
  @XmlElement
  public List<Double> dots;
  /**
   * Maximum possible value of the dots
   */
  @XmlElement
  public Double max;

  public Axis() {
  }

  public Axis(final String type, final String name, final List<Double> dots, final Double max) {
    this.type = type;
    this.name = name;
    this.dots = dots;
    this.max = max;
  }
}
