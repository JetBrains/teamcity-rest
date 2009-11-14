/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 14.11.2009
 */
public class RequestTranslator {
  final Logger LOG = Logger.getInstance(RequestTranslator.class.getName());

  private String myOriginalRequestPath;
  private String myNewRequestPath;

  public RequestTranslator() {
  }

  public void setOriginalRequestPath(final String originalRequestPath) {
    myOriginalRequestPath = originalRequestPath;
  }

  public void setNewRequestPath(@NotNull final String newRequestPath) {
    myNewRequestPath = newRequestPath;
  }

  public String getOriginalPathByPatched(final String patchedRequestPath) {
    if (myOriginalRequestPath == null) {
      return patchedRequestPath;
    }
    return replaceAtStart(patchedRequestPath, myNewRequestPath, myOriginalRequestPath);
  }

  public HttpServletRequest getPathPatchedRequest(final HttpServletRequest request) {
    if (myOriginalRequestPath == null) {
      return request;
    }
    LOG.info("Establishing mapping for '" + myOriginalRequestPath + "' requests to '" + myNewRequestPath + "'");
    return new HttpServletRequestWrapper(request) {
      @Override
      public String getPathInfo() {
        return replaceAtStart(super.getPathInfo(), myOriginalRequestPath, myNewRequestPath);
      }

      @Override
      public String getRequestURI() {
        return replaceAtStart(super.getRequestURI(), myOriginalRequestPath, myNewRequestPath);
      }

      @Override
      public StringBuffer getRequestURL() {
        final StringBuffer originalURL = super.getRequestURL();
        final String urlString = originalURL.toString();
        final int indexOfOriginalPart = urlString.indexOf(myOriginalRequestPath);
        if (indexOfOriginalPart == -1) {
          return originalURL;
        }
        final StringBuffer result = new StringBuffer(originalURL);
        return result.replace(indexOfOriginalPart, indexOfOriginalPart + myOriginalRequestPath.length(), myNewRequestPath);
      }
    };
  }

  private static String replaceAtStart(final @NotNull String text, final @NotNull String originalPart, final @NotNull String newPart) {
    if (!text.startsWith(originalPart)) {
      return text;
    }
    return newPart + text.substring(originalPart.length());
  }

}
