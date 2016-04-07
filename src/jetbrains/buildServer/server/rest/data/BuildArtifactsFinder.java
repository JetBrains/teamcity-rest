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

package jetbrains.buildServer.server.rest.data;

import com.google.common.collect.ComparisonChain;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.files.FileApiUrlBuilder;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.RunningBuildsManagerEx;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.browser.*;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.util.pathMatcher.AntPatternTreeMatcher;
import jetbrains.buildServer.util.pathMatcher.PathNode;
import jetbrains.buildServer.web.artifacts.browser.ArtifactElement;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import jetbrains.buildServer.web.artifacts.browser.ArtifactsBrowserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 27.04.13
 */
public class BuildArtifactsFinder extends AbstractFinder<ArtifactTreeElement> {
  public static final String ARCHIVES_DIMENSION_NAME = "browseArchives";  //whether archives are treated as directories while browsing
  public static final String HIDDEN_DIMENSION_NAME = "hidden";  //whether .teamcity directory is included and it's children listed (does not affect anything if within .teamcity already)
  public static final String DIRECTORY_DIMENSION_NAME = "directory";  //whether to include entries which have children
  public static final String DIMENSION_RECURSIVE = "recursive";  //whether to list direct children or recursive children
  public static final String DIMENSION_PATTERNS = "pattern";
  public static final String DIMENSION_MODIFIED = "modified";

  protected static final Comparator<ArtifactTreeElement> ARTIFACT_COMPARATOR = new Comparator<ArtifactTreeElement>() {
    public int compare(final ArtifactTreeElement o1, final ArtifactTreeElement o2) {
      return ComparisonChain.start()
                            .compareFalseFirst(o1.isContentAvailable(), o2.isContentAvailable())
                            .compare(o1.getFullName(), o2.getFullName())
                            .result();
    }
  };
  private static final Pattern SLASHES_OR_SPACE_PATTERN = Pattern.compile("[\\/ ]", Pattern.LITERAL);


  @NotNull private final Element myBaseElement;
  private final TimeService myTimeService;

  public BuildArtifactsFinder(@NotNull final Element baseElement, final RunningBuildsManagerEx timeServiceContainer) {
    super(HIDDEN_DIMENSION_NAME, ARCHIVES_DIMENSION_NAME, DIRECTORY_DIMENSION_NAME, DIMENSION_RECURSIVE, DIMENSION_PATTERNS, DIMENSION_MODIFIED);
    myBaseElement = baseElement;
    myTimeService = timeServiceContainer.getTimeService();
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final ArtifactTreeElement element) {
    throw new OperationException("'getItemLocator' is not implemented in BuildArtifactsFinder");
  }

  @NotNull
  @Override
  protected ItemFilter<ArtifactTreeElement> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<ArtifactTreeElement> result = new MultiCheckerFilter<ArtifactTreeElement>();

    TimeCondition.FilterAndLimitingDate<ArtifactTreeElement> dateFiltering =
      TimeCondition.processTimeConditions(DIMENSION_MODIFIED, locator, new TimeCondition.ValueExtractor<ArtifactTreeElement, Date>() {
        @Nullable
        @Override
        public Date get(@NotNull final ArtifactTreeElement artifactTreeElement) {
          Long lastModified = artifactTreeElement.getLastModified();
          return lastModified == null ? null : new Date(lastModified);
        }
      }, null, null, myTimeService);

    if (dateFiltering != null){
      result.add(dateFiltering.getFilter());
    }

    return result;
  }

  private void setLocatorDefaults(@NotNull final Locator locator) {
    locator.setDimensionIfNotPresent(DIMENSION_RECURSIVE, "false");
    locator.setDimensionIfNotPresent(HIDDEN_DIMENSION_NAME, "false");
    locator.setDimensionIfNotPresent(ARCHIVES_DIMENSION_NAME, "false");
  }

  // =============================================================
  @NotNull
  public static ArtifactTreeElementWrapper getItem(@NotNull final Browser browser, @NotNull final String path, @NotNull final String where,
                                                   final @NotNull ServiceLocator serviceLocator) {
    Element element;
    if (SLASHES_OR_SPACE_PATTERN.matcher(path).replaceAll("").length() == 0){ //TeamCity API issue: cannot list root of the Browser by empty string or "/"
      element = browser.getRoot();
    }else{
      element = browser.getElement(path);
    }
    if (element == null) {
      element = getSingleItemByPatternPath(path, browser.getRoot(), browser, serviceLocator);
      if (element == null)
        throw new NotFoundException("Path '" + path + "' is not found in " + where + " or an error occurred");
      //TeamCity API: or error occurred (related http://youtrack.jetbrains.com/issue/TW-34377)
    }
    return new ArtifactTreeElementWrapper(element);
  }

  @NotNull
  public static ArtifactTreeElement getItem(@NotNull final java.io.File rootPath, @NotNull final String path, @NotNull final String where,
                                            final @NotNull ServiceLocator serviceLocator) {
    // does not work for archives so far...
    // return getItem(new ZipAwareBrowser(new FileSystemBrowser(rootPath)), path, "");
    return getItem(new FileSystemBrowser(rootPath), path, where, serviceLocator);
  }

  @NotNull
  public static List<ArtifactTreeElement> getItems(@NotNull final Element initialElement,
                                                   final @Nullable String basePath,
                                                   final @Nullable String filesLocator,
                                                   final @Nullable FileApiUrlBuilder urlBuilder,
                                                   final @NotNull ServiceLocator serviceLocator) {
    return makeRelativeToBasePath(BuildArtifactsFinder.getItems(initialElement, filesLocator, urlBuilder, serviceLocator), basePath);
  }

  @NotNull
  public static List<ArtifactTreeElement> getItems(@NotNull final Element initialElement,
                                                   final @Nullable String filesLocator,
                                                   final @Nullable FileApiUrlBuilder urlBuilder,
                                                   final @NotNull ServiceLocator serviceLocator) {
    if (initialElement.isLeaf()) {
      String additionalMessage = "";
      if (urlBuilder != null) {
        additionalMessage = " To get content use '" + urlBuilder.getContentHref(initialElement) + "'.";
      }
      throw new BadRequestException("Cannot provide children list for file '" + initialElement.getFullName() + "'." + additionalMessage);
    }

    return new BuildArtifactsFinder(initialElement, serviceLocator.getSingletonService(RunningBuildsManagerEx.class)).getItems(filesLocator).myEntries;
  }

  @NotNull
  @Override
  protected ItemHolder<ArtifactTreeElement> getPrefilteredItems(@NotNull final Locator locator) {
    setLocatorDefaults(locator);

    Boolean includeHidden = locator.getSingleDimensionValueAsBoolean(HIDDEN_DIMENSION_NAME);
    if (isWithinHidden(myBaseElement)) {
      includeHidden = null;
    }

    List<String> rules = new ArrayList<String>();
    final String filePatterns = locator.getSingleDimensionValue(DIMENSION_PATTERNS);
    if (filePatterns != null) {
      final String[] splittedPatterns = filePatterns.split(","); //might consider smarter splitting later
      if (splittedPatterns.length > 0) {
        rules.addAll(Arrays.asList(splittedPatterns));
      }
    } else {
      rules.add("+:**");
    }

    long childrenNestingLevel = 1;
    final String recursive = locator.getSingleDimensionValue(DIMENSION_RECURSIVE);
    if (recursive != null) {
      final Boolean parsedBoolean = Locator.getStrictBoolean(recursive);
      if (parsedBoolean != null) {
        if (parsedBoolean) {
          childrenNestingLevel = -1;
        } else {
          childrenNestingLevel = 1;
        }
      } else {
        //treat as nesting number
        try {
          childrenNestingLevel = Long.parseLong(recursive);
        } catch (NumberFormatException e) {
          throw new BadRequestException("Cannot parse value '" + recursive + "' for dimension '" + DIMENSION_RECURSIVE + "': should be boolean or nesting level number");
        }
      }
    }

    long archiveChildrenNestingLevel = 0;
    final String listArchives = locator.getSingleDimensionValue(ARCHIVES_DIMENSION_NAME);
    if (listArchives != null) {
      final Boolean parsedBoolean = Locator.getStrictBoolean(listArchives);
      if (parsedBoolean != null) {
        if (parsedBoolean) {
          archiveChildrenNestingLevel = 1;
        } else {
          archiveChildrenNestingLevel = 0;
        }
      } else {
        //treat as nesting number
        try {
          archiveChildrenNestingLevel = Long.parseLong(listArchives);
        } catch (NumberFormatException e) {
          throw new BadRequestException("Cannot parse value '" + listArchives + "' for dimension '" + ARCHIVES_DIMENSION_NAME + "': should be boolean or nesting level number");
        }
      }
    }

    Boolean includeDirectories = locator.getSingleDimensionValueAsBoolean(DIRECTORY_DIMENSION_NAME);

    final List<ArtifactTreeElement> result = new ArrayList<ArtifactTreeElement>();
    AntPatternTreeMatcher.ScanOption[] options = {};
    if (includeDirectories != null && !includeDirectories) {
      options = new AntPatternTreeMatcher.ScanOption[]{AntPatternTreeMatcher.ScanOption.LEAFS_ONLY};  // does not seem to have any effect, see TW-41662
    }

    final Node rootNode = new Node(myBaseElement, childrenNestingLevel, archiveChildrenNestingLevel, includeHidden, true);
    final Collection<Node> rawResult = AntPatternTreeMatcher.scan(rootNode, rules, options);
    final Boolean finalIncludeDirectories = includeDirectories;
    result.addAll(CollectionsUtil.filterAndConvertCollection(rawResult, new Converter<ArtifactTreeElement, Node>() {
      public ArtifactTreeElement createFrom(@NotNull final Node source) {
        return source.getElement();
      }
    }, new Filter<Node>() {
      public boolean accept(@NotNull final Node data) {
        if (rootNode.equals(data)) {
          return false; //TeamCity API issue: should support not returning the first node in API
        }
        //noinspection RedundantIfStatement
        if (!FilterUtil.isIncludedByBooleanFilter(finalIncludeDirectories, !data.getElement().isLeaf())) {
          return false;
        }
        return true;
      }
    }));

    Collections.sort(result, ARTIFACT_COMPARATOR);
    return getItemHolder(result);
  }

  @NotNull
  private static List<ArtifactTreeElement> makeRelativeToBasePath(@NotNull final List<ArtifactTreeElement> items, @Nullable final String basePath) {
    if (StringUtil.isEmpty(basePath)){
      return items;
    }
    return CollectionsUtil.convertCollection(items, new Converter<ArtifactTreeElement, ArtifactTreeElement>() {
      @Override
      public ArtifactTreeElement createFrom(@NotNull final ArtifactTreeElement source) {
        return new BuildArtifactsFinder.ArtifactTreeElementWrapper(source) {
          @NotNull
          @Override
          public String getFullName() {
            return relativeToBase(super.getFullName(), basePath);
          }
        };
      }
    });
  }

  @NotNull
  private static String relativeToBase(@NotNull final String name, @Nullable final String basePath) {
    if (StringUtil.isEmpty(basePath)) return name;

    final String normalizedName = removeLeadingDelimeters(name);
    if (!normalizedName.startsWith(removeLeadingDelimeters(basePath))) {
      return name;
    }
    return removeLeadingDelimeters(normalizedName.substring(basePath.length()));
  }

  @NotNull
  private static String removeLeadingDelimeters(@NotNull String result) {
    return removeLeading(removeLeading(result, "!"), "/");
  }

  @NotNull
  private static String removeLeading(final @NotNull String result, final String prefix) {
    return result.startsWith(prefix) ? result.substring(prefix.length()) : result;
  }

  @NotNull
  public static Element getArtifactElement(@NotNull final BuildPromotion buildPromotion, @NotNull final String path, final @NotNull ServiceLocator serviceLocator) {
    final BuildPromotionEx buildPromotionEx = (BuildPromotionEx)buildPromotion;
    final BuildArtifacts artifacts = buildPromotionEx.getArtifacts(BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT);
    final BuildArtifactHolder holder = artifacts.findArtifact(path);
    if (!holder.isAvailable() && !"".equals(path)) { // "".equals(path) is a workaround for no artifact directory case
      return getItem(new ArtifactsBrowserImpl(artifacts), path, LogUtil.describe(buildPromotionEx), serviceLocator);
    }
    if (!holder.isAccessible()) {
      throw new AuthorizationFailedException("Artifact is not accessible with current user permissions. Relative path: '" + holder.getRelativePath() + "'");
    }
    return new ArtifactElement(holder.getArtifact());
  }

  @Nullable
  private static Element getSingleItemByPatternPath(final @NotNull String pathWithPatterns, final @NotNull Element root, final @NotNull Browser browser,
                                                    final @NotNull ServiceLocator serviceLocator) {
    final List<ArtifactTreeElement> items = getItems(root, Locator.getStringLocator(DIMENSION_PATTERNS, pathWithPatterns), null, serviceLocator);
    if (items.size() > 0){
      final ArtifactTreeElement first = items.get(0);
      //now find it in browser to make sure archive's children can be listed
      final Element foundAgain = browser.getElement(first.getFullName());
        return foundAgain != null ? foundAgain : first;
    }
    return null;
  }

  private static class Node implements PathNode<Node> {
    @NotNull private final ArtifactTreeElement myElement;
    private final long myListChildrenLevel;
    private final long myListArchiveChildrenLevel;
    private final Boolean myHidden;
    private final boolean myFirstNode;

    /**
     * @param element
     * @param listChildrenLevel       number of nesting to list children for; -1 for unlimited level, 0 for no children listed
     * @param listArchiveChildrenLevel  treat archives as directories (up to the specified archive nesting number)
     * @param hidden           list files under .teamcity, "null" to include both hidden and not
     * @param firstNode
     */
    public Node(@NotNull final Element element,
                final long listChildrenLevel,
                final long listArchiveChildrenLevel,
                final Boolean hidden,
                final boolean firstNode) {
      myElement = new ArtifactTreeElementWrapper(element);
      myListChildrenLevel = listChildrenLevel;
      myListArchiveChildrenLevel = listArchiveChildrenLevel;
      myHidden = hidden;
      myFirstNode = firstNode;
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean shouldHideChildren() {
      if (myListChildrenLevel == 0) return true;
      if (myFirstNode) return false;
      if (myElement.isArchive() && myListArchiveChildrenLevel == 0) return true;
      return false;
    }

    @NotNull
    public String getName() {
      return myElement.getName();
    }

    private Iterable<Node> myCachedChildren;
    private boolean myCachedChildrenInitialized = false;

    public Iterable<Node> getChildren() {
      if (!myCachedChildrenInitialized) {
        myCachedChildren = getChildrenInternal();
        myCachedChildrenInitialized = true;
      }
      return myCachedChildren;
    }

    private Iterable<Node> getChildrenInternal() {
      if (shouldHideChildren()) {
        return null;
      }
      try {
        final long nextListChildrenLevel = myListChildrenLevel > 0 ? myListChildrenLevel - 1 : myListChildrenLevel;
        final long nextListArchiveChildrenLevel =
          (myElement.isArchive() && myListArchiveChildrenLevel > 0 && !myFirstNode) ? myListArchiveChildrenLevel - 1 : myListArchiveChildrenLevel;
        //noinspection unchecked
        return CollectionsUtil.filterAndConvertCollection(myElement.getChildren(), new Converter<Node, Element>() {
          public Node createFrom(@NotNull final Element source) {
            final Boolean nestedHidden = myHidden != null && myHidden && isHiddenDir(source) ? null : myHidden; //do not filter if we list hidden files and already within .teamcity
            return new Node(source, nextListChildrenLevel, nextListArchiveChildrenLevel, nestedHidden, false);
          }
        }, new Filter<Element>() {
          public boolean accept(@NotNull final Element data) {
            return FilterUtil.isIncludedByBooleanFilter(myHidden, isHiddenDir(data)); //do not go into .teamcity
          }
        });
      } catch (BrowserException e) {
        throw new OperationException("Error listing children for artifact '" + myElement.getFullName() + "'.", e);
      }
    }

    @NotNull
    public ArtifactTreeElement getElement() {
      if (myFirstNode || !myElement.isArchive() || myListArchiveChildrenLevel != 0) {
        return myElement;
      }
      return new ArtifactTreeElementWrapper(myElement) {
        @Override
        public boolean isLeaf() {
          return true;
        }

        @Nullable
        @Override
        public Iterable<Element> getChildren() throws BrowserException {
          return null;
        }

        @Override
        public String toString() {
          return myElement.toString() + " with children concealed";
        }
      };
    }

    @Override
    public String toString() {
      return "Node '" + myElement.toString() + "', childrenLevel: " + myListChildrenLevel +
             ", archiveLevel: " + myListArchiveChildrenLevel +
             ", includeHidden: " + myHidden +
             ", first: " + myFirstNode + ")";

    }
  }

  public static class ArtifactTreeElementWrapper implements ArtifactTreeElement {

    @NotNull private final Element myElement;
    @Nullable private final ZipElement myZipElement;
    @Nullable private final ArtifactTreeElement myArtifactTreeElement;
    @Nullable private final File myFile;

    public ArtifactTreeElementWrapper(@NotNull final Element element) {
      myElement = element;
      if (myElement instanceof ZipElement) {
        myZipElement = (ZipElement)myElement;
      } else {
        myZipElement = null;
      }
      if (myElement instanceof ArtifactTreeElement) {
        myArtifactTreeElement = (ArtifactTreeElement)myElement;
      } else {
        myArtifactTreeElement = null;
      }
      if (myElement instanceof FileSystemBrowser.FileElement) {
        myFile = ((FileSystemBrowser.FileElement)myElement).getFile();
      }  else{
        myFile = null;
      }
    }

    @NotNull
    public String getName() {
      return myElement.getName();
    }

    @NotNull
    public String getFullName() {
      return myElement.getFullName();
    }

    public boolean isLeaf() {
      return myElement.isLeaf();
    }

    @Nullable
    public Iterable<Element> getChildren() throws BrowserException {
      return myElement.getChildren();
    }

    public boolean isContentAvailable() {
      return myElement.isContentAvailable();
    }

    @NotNull
    public InputStream getInputStream() throws IllegalStateException, IOException, BrowserException {
      return myElement.getInputStream();
    }

    public long getSize() {
      return myElement.getSize();
    }

    @NotNull
    public Browser getBrowser() {
      return myElement.getBrowser();
    }

    @SuppressWarnings("SimplifiableConditionalExpression")
    public boolean isArchive() {
      return myZipElement != null ? myZipElement.isArchive() : false;
    }

    @SuppressWarnings("SimplifiableConditionalExpression")
    public boolean isInsideArchive() {
      return myZipElement != null ? myZipElement.isInsideArchive() : false;
    }

    @Nullable
    public Long getLastModified() {
      if (myArtifactTreeElement != null) {
        return myArtifactTreeElement.getLastModified();
      } else if (myFile != null){
        return myFile.lastModified();
      }
      return null;
    }

    @Override
    public String toString() {
      return myElement.toString() + " unified";
    }
  }

  static boolean isWithinHidden(final @NotNull Element data) {
    final String fullName = data.getFullName();
    return fullName.equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR) ||
           fullName.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/");
  }

  static boolean isHiddenDir(final @NotNull Element data) {
    final String fullName = data.getFullName();
    return fullName.equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR) ||
           fullName.equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/");
  }
}
