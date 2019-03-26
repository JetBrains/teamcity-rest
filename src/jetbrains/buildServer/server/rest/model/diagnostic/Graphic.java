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
 * Entity for abstract graphic.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@XmlRootElement(name = "graphic")
@XmlType
public class Graphic {

  /**
   * Any description about graphic. Like 'memory', 'cpu' or so.
   */
  @XmlElement
  public String type;
  /**
   * Axis for graphic's abscissa ('x') line.
   */
  @XmlElement(name = "abscissa")
  public Axis abscissa;
  /**
   * Axises for graphic's ordinates ('y') lines.
   * Fj(Xk)=Yk where y = ordinates[j].dots[k], x = abscissa.dots[k]
   */
  @XmlElement(name = "ordinates")
  public List<Axis> ordinates;

  public Graphic() {
  }

  public Graphic(final String type, final Axis abscissa, final List<Axis> ordinates) {
    this.type = type;
    this.abscissa = abscissa;
    this.ordinates = ordinates;
  }
}
