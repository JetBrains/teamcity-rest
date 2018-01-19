/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.jersey;

import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.server.rest.data.UserGroupFinder;

/**
 * @author Yegor.Yarko
 *         Date: 15.11.2009
 */
@Provider
public class UserGroupFinderContextProvider extends AbstractSingletonBeanProvider<UserGroupFinder> {
  public UserGroupFinderContextProvider(final UserGroupFinder object) {
    super(object, UserGroupFinder.class);
  }
}