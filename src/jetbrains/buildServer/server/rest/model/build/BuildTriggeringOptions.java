/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;

/**
 * Used when triggering a build for settingas not making sense for a queued build (processed during the triggering)
 * @author Yegor.Yarko
 *         Date: 18.01.14
 */
@XmlRootElement(name = "buildTriggeringOptions")
@XmlType(name = "buildTriggeringOptions", propOrder = {"cleanSources", "rebuildAllDependencies", "queueAtTop",
  "rebuildDependencies"})
public class BuildTriggeringOptions {
    @XmlAttribute public Boolean cleanSources;
    @XmlAttribute public Boolean rebuildAllDependencies;
    @XmlAttribute public Boolean queueAtTop;
    /**
     * Specifies which of the snapshot dependnecies to rebuild. Build types of direct or indirect dependencies can be specified.
     * Makes sence only if "rebuildAllDependencies" is not set to "true"
     */
    @XmlElement(name = "rebuildDependencies") public BuildTypes rebuildDependencies;
}

