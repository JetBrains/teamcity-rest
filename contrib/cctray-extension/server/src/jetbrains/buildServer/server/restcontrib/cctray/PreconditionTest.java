/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.server.restcontrib.cctray;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.server.rest.RESTControllerExtensionAdapter;

/**
 * @author Yegor.Yarko
 *         Date: 08.08.2010
 */
public class PreconditionTest {
    final Logger LOG = Logger.getInstance(PreconditionTest.class.getName());

    public PreconditionTest() {
        try {
            RESTControllerExtensionAdapter.class.getName();
        } catch (NoClassDefFoundError e) {
            LOG.error("TeamCity REST API classes not found. Please ensure REST API plugin is patched to have \n"
                    +" 'false' in <deployment use-separate-classloader=\"false\"/> line of \n"+
                    "<TeamCity home>/webapps/ROOT/WEB-INF/plugins/rest-api.zip/teamcity-plugin.xml");
            throw new IllegalStateException("Could not initialize CCTray plugin, see TeamCity server log for details.");
        }
    }
}
