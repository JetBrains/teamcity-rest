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

package jetbrains.buildServer.server.rest.jersey;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import jetbrains.buildServer.server.rest.PathTransformator;
import jetbrains.buildServer.server.rest.PathTransformer;
import jetbrains.buildServer.server.rest.RequestPathTransformInfo;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;

/**
* @author Yegor.Yarko
*         Date: 27.01.14
*/
public class SimplePathTransformer implements PathTransformer {

  private final HttpServletRequest myRequest;
  private final HttpHeaders myHeaders;
  private final RequestPathTransformInfo myRequestPathTransformInfo;

  public SimplePathTransformer(final HttpServletRequest request, final HttpHeaders headers, final RequestPathTransformInfo requestPathTransformInfo) {
    myRequest = request;
    myHeaders = headers;
    myRequestPathTransformInfo = requestPathTransformInfo;
  }

  public String transform(final String path) {
    if (!TeamCityProperties.getBoolean("rest.beans.href.useFullURLs") || myRequest == null) {
      return getRequestTranslator().getTransformedPath(path);
    }
    final String scheme = myRequest.getScheme();
    final int port = myRequest.getServerPort();
    return scheme + "://" + myRequest.getServerName() +
           ((("http".equals(scheme) && port > 80) || ("https".equals(scheme) && port != 443)) ? ":" + port : "")
           + myRequest.getServletContext().getContextPath() + getRequestTranslator().getTransformedPath(path);
  }

  private PathTransformator getRequestTranslator() {
    final String originalRequestPath =
      myHeaders.getRequestHeader(Constants.ORIGINAL_REQUEST_URI_HEADER_NAME).get(0); //todo report appropriate message
    return myRequestPathTransformInfo.getReverseTransformator(originalRequestPath, false);
  }
}
