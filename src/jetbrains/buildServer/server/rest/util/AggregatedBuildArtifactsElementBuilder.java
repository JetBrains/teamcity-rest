/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.util;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BuildArtifactsFinder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 28/01/2016
 */
public class AggregatedBuildArtifactsElementBuilder {
  private static final Logger LOG = Logger.getInstance(AggregatedBuildArtifactsElementBuilder.class.getName());

  //improve messages by adding "names" to elements

  @NotNull private final List<Element> myElements = new ArrayList<>();
  @Nullable private String myElementName;
  @Nullable private String myFullElementName;
  private boolean myHasDirElements = false;

  @NotNull
  public static Element getBuildAggregatedArtifactElement(@NotNull final String path, @NotNull final List<BuildPromotion> builds, final @NotNull ServiceLocator serviceLocator) {
    final AggregatedBuildArtifactsElementBuilder result = new AggregatedBuildArtifactsElementBuilder();
    int i = 0;
    for (BuildPromotion buildPromotion : builds) {
      try {
        final Element artifactElement = BuildArtifactsFinder.getArtifactElement(buildPromotion, path, serviceLocator);
        LOG.debug("Found artifact file with path '" + path + "' in " + i + "/" + builds.size() + " build: " + LogUtil.describe(buildPromotion));
        result.add(artifactElement);
      } catch (NotFoundException e) {
        LOG.debug("Ignoring not found error in artifacts aggregation request: " + e.toString());
      } catch (AuthorizationFailedException e) {
        LOG.debug("Ignoring authentication error in artifacts aggregation request: " + e.toString());
      }
      i++;
    }
    return result.get();
  }

  public void add(@NotNull final Element element) {
    if (element.isContentAvailable()) {
      if (!myElements.isEmpty()) {
        if (myHasDirElements) {
          throw new BadRequestException("Conflict constructing aggregated file element: found file while there is already directory item found");
        }
        //can check if the same and completely ignore if so
        LOG.debug("Ignoring a conflict constructing aggregated file element: found file while there is already item found");
        return;
      }
    }

    if (myElementName != null) {
      if (!myElementName.equals(element.getName())) {
        throw new OperationException("Error constructing aggregated file element: " +
                                     "Not matching name: '" + myElementName + "' and '" + element.getFullName() + "'");
      }
    } else {
      myElementName = element.getName();
    }
    if (myFullElementName != null) {
      if (!myFullElementName.equals(element.getName())) {
        throw new OperationException("Error constructing aggregated file element: " +
                                     "Not matching full name: '" + myFullElementName + "' and '" + element.getFullName() + "'");
      }
    } else {
      myFullElementName = element.getName();
    }

    myElements.add(element);
    if (!element.isContentAvailable()) {
      myHasDirElements = true;
    }
  }

  @NotNull
  public Element get() {
    if (myElements.isEmpty()) {
      throw new NotFoundException("No artifact found while constructing aggregated file element");
    }

    return new Element() {
      @NotNull
      @Override
      public String getName() {
        //noinspection ConstantConditions
        return myElementName;
      }

      @NotNull
      @Override
      public String getFullName() {
        //noinspection ConstantConditions
        return myFullElementName;
      }

      @Override
      public boolean isLeaf() {
        if (myElements.size() == 1) return myElements.iterator().next().isLeaf();
        return false;
      }

      @Nullable
      @Override
      public Iterable<Element> getChildren() throws BrowserException {
        if (myElements.size() == 1) return myElements.iterator().next().getChildren();
        final LinkedHashSet<Element> result = new LinkedHashSet<>();
        for (Element element : myElements) {
          final Iterable<Element> children = element.getChildren();
          if (children != null) {
            for (Element child : children) {
              result.add(child);
            }
          }
        }
        return result;
      }

      @Override
      public boolean isContentAvailable() {
        if (myElements.size() == 1) return myElements.iterator().next().isContentAvailable();
        return false;
      }

      @NotNull
      @Override
      public InputStream getInputStream() throws IllegalStateException, IOException, BrowserException {
        if (myElements.size() == 1) return myElements.iterator().next().getInputStream();
        throw new IllegalStateException("Content is not available for an aggregated file element");
      }

      @Override
      public long getSize() {
        if (myElements.size() == 1) return myElements.iterator().next().getSize();
        return -1;
      }

      @NotNull
      @Override
      public Browser getBrowser() {
        throw new RuntimeException("'getBrowser' operation not supported for an aggregated file element");
      }
    };
  }
}
