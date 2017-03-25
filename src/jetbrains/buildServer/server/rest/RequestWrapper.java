/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import jetbrains.buildServer.server.rest.request.Constants;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.2009
 */
public class RequestWrapper extends HttpServletRequestWrapper {
  private static final Logger LOG = Logger.getInstance(RequestWrapper.class.getName());
  private final RequestPathTransformInfo myRequestPathTransformInfo;

  public RequestWrapper(HttpServletRequest request, RequestPathTransformInfo requestPathTransformInfo) {
    super(request);
    myRequestPathTransformInfo = requestPathTransformInfo;
    if (LOG.isDebugEnabled()) LOG.debug("Establishing request mapping: '" + request.getRequestURI() + "' -> '" + getRequestURI() + "'");
  }

  @Override
  public String getPathInfo() {
    return myRequestPathTransformInfo.getTransformedPath(super.getPathInfo());
  }

  @Override
  public String getRequestURI() {
    return myRequestPathTransformInfo.getTransformedPath(super.getRequestURI());
  }

  @Override
  public StringBuffer getRequestURL() {
    return new StringBuffer(myRequestPathTransformInfo.getTransformedPath(super.getRequestURL().toString()));
  }

  @Override
  public Enumeration getHeaderNames() {
    final ArrayList headerNames = Collections.list(super.getHeaderNames());
    headerNames.add(Constants.ORIGINAL_REQUEST_URI_HEADER_NAME);
    return Collections.enumeration(headerNames);
  }

  @Override
  public Enumeration getHeaders(final String name) {
    if (Constants.ORIGINAL_REQUEST_URI_HEADER_NAME.equals(name)) {
      return Collections.enumeration(Collections.singleton(super.getRequestURI()));
    }
    return super.getHeaders(name);
  }

  @Override
  public String getHeader(final String name) {
    if (Constants.ORIGINAL_REQUEST_URI_HEADER_NAME.equals(name)) {
      return super.getRequestURI();
    }
    return super.getHeader(name);
  }
}
