/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.agent;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "compatibilities")
@XmlType(name = "agents-compatibilities-ref")
@SuppressWarnings("PublicField")
public class Compatibilities {
  public static final String COMPATIBLE = "compatible";
  public static final String INCOMPATIBLE = "incompatible";

  @XmlElement(name = "compatibility")
  public List<Compatibility> compatibilities;

  public Compatibilities() {
  }

  public Compatibilities(@Nullable final List<Compatibility> compatible,
                         @Nullable final List<Compatibility> incompatible,
                         @NotNull final Fields fields) {
    final ArrayList<Compatibility> compatibilities = new ArrayList<Compatibility>();
    if (fields.isIncluded(COMPATIBLE, true, true) && compatible != null) compatibilities.addAll(compatible);
    if (fields.isIncluded(INCOMPATIBLE, false, true) && incompatible != null) compatibilities.addAll(incompatible);
    this.compatibilities = compatibilities;
  }
}
