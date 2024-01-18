/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import java.util.Collection;
import java.util.Map;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.serverSide.impl.PromotionCustomizationInfo;
import org.jetbrains.annotations.NotNull;

import static java.util.Collections.emptyMap;

/**
 * @author kir
 */
@XmlRootElement(name = "customizations")
@ModelDescription("Represents build customizations (artifact dependency overrides, custom parameters or changesets).")
public class Customizations {
  @XmlElement public Map<String, String> parameters;
  @XmlElement public Map<String, String> changes;
  @XmlElement public Map<String, String> artifactDependencies;

  public Customizations() {
  }

  public Customizations(@NotNull Collection<String> changed) {
    parameters = changed.contains(PromotionCustomizationInfo.PARAMS) ? emptyMap() : null;
    changes = changed.contains(PromotionCustomizationInfo.MOD_ID) || changed.contains(PromotionCustomizationInfo.REVISIONS) ? emptyMap() : null;
    artifactDependencies = changed.contains(PromotionCustomizationInfo.ARTIFACTS) ? emptyMap() : null;
  }
}