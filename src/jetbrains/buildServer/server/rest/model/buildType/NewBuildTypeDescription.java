/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import com.intellij.openapi.diagnostic.Logger;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Yegor.Yarko
 *         Date: 04.01.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "newBuildTypeDescription")
@XmlType(propOrder = {"shareVCSRoots", "copyAllAssociatedSettings", "sourceBuildTypeLocator", "name"})
public class NewBuildTypeDescription {
  private static final Logger LOG = Logger.getInstance(NewBuildTypeDescription.class.getName());

  public NewBuildTypeDescription() {
  }

  @XmlAttribute public String name;

  @XmlAttribute public String sourceBuildTypeLocator;

  @XmlAttribute public Boolean copyAllAssociatedSettings;
  @XmlAttribute public Boolean shareVCSRoots;
}
