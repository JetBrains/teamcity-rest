/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.util;

import graphql.kickstart.tools.GraphQLResolver;


public abstract class ModelResolver<MODEL extends ObjectIdentificationNode> implements GraphQLResolver<MODEL> {
  public static final char SEPARATOR = '|';

  /**
   * Generates id of the given model, implementing ObjectIdentificationNode interface. <br/>
   * This is required for compliance with GraphQL Object Identification specification.
   * @implSpec Overrides MUST return value starting with {@link #getIdPrefix()} + {@link #SEPARATOR}.
   * @param relayNode model class to generate 'id' for.
   * @return unique id, starting with prefix and SEPARATOR.
   */
  public String getId(MODEL relayNode) {
    return getIdPrefix() + SEPARATOR + relayNode.getRawId();
  }

  /**
   * Generate prefix to be used in id generation, see {@link #getId(ObjectIdentificationNode)}. Given prefix may be used to identify which ModelResolver should be used
   * when looking for a model by id, see {@link #findById(String)}.
   * @implNote Straightforward implementation looks like this:   <code>return MODEL.getClass().getSimpleName();</code>
   * @return prefix.
   */
  public abstract String getIdPrefix();

  /**
   * Find model by id.
   * @param id as described in {@link #getId(ObjectIdentificationNode)}.
   * @return model if possible, <code>null</code> otherwise.
   */
  public abstract MODEL findById(String id);
}
