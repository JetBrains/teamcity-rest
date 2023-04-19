/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.TimeCondition;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.util.FilterUtil;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.util.LocatorUtil;
import jetbrains.buildServer.server.rest.data.util.MultiCheckerFilter;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.*;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.files.FileApiUrlBuilder;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.browser.*;
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
  private static final Logger LOG = Logger.getInstance(BuildArtifactsFinder.class.getName());

  public static final String ARCHIVES_DIMENSION_NAME = "browseArchives";  //whether archives are treated as directories while browsing
  public static final String HIDDEN_DIMENSION_NAME = "hidden";  //whether .teamcity directory is included and it's children listed (does not affect anything if within .teamcity already)
  public static final String DIRECTORY_DIMENSION_NAME = "directory";  //whether to include entries which have children
  public static final String DIMENSION_RECURSIVE = "recursive";  //whether to list direct children or recursive children
  public static final String DIMENSION_PATTERNS = "pattern";
  public static final String DIMENSION_MODIFIED = "modified";
  public static final String DIMENSION_SIZE = "size";
  //todo: add "path" dimension to specify full path to a file or directory, including archives

  static final Comparator<ArtifactTreeElement> ARTIFACT_COMPARATOR = new ArtifactsComparator();
  private static final Pattern SLASHES_OR_SPACE_PATTERN = Pattern.compile("[\\/ ]", Pattern.LITERAL);


  @NotNull private final Element myBaseElement;
  @NotNull private final TimeCondition myTimeCondition;

  public BuildArtifactsFinder(@NotNull final Element baseElement, @NotNull final TimeCondition timeCondition) {
    super(HIDDEN_DIMENSION_NAME, ARCHIVES_DIMENSION_NAME, DIRECTORY_DIMENSION_NAME, DIMENSION_RECURSIVE, DIMENSION_PATTERNS, DIMENSION_MODIFIED, DIMENSION_SIZE);
    myBaseElement = baseElement;
    myTimeCondition = timeCondition;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final ArtifactTreeElement element) {
    throw new OperationException("'getItemLocator' is not implemented in BuildArtifactsFinder");
  }

  @NotNull
  @Override
  public ItemFilter<ArtifactTreeElement> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<ArtifactTreeElement> result = new MultiCheckerFilter<>();

    TimeCondition.FilterAndLimitingDate<ArtifactTreeElement> dateFiltering =
      myTimeCondition.processTimeConditions(DIMENSION_MODIFIED, locator, artifactTreeElement -> {
        Long lastModified = artifactTreeElement.getLastModified();
        return lastModified == null ? null : new Date(lastModified);
      }, null);

    if (dateFiltering != null){
      result.add(dateFiltering.getFilter());
    }

    final String sizeDimension = locator.getSingleDimensionValue(DIMENSION_SIZE);
    if (sizeDimension != null) {
      final long sizeLimit; //only one value is supported so far, treated as "less than"
      try {
        sizeLimit = StringUtil.parseFileSize(sizeDimension);
      } catch (NumberFormatException e) {
        throw new BadRequestException("Cannot parse size from '" + sizeDimension + "'. Should be a number (bytes) or <number>kb, <number>mb");
      }
      result.add(item -> !item.isContentAvailable() || item.getSize() <= sizeLimit);
    }

    return result;
  }

  private void setLocatorDefaults(@NotNull final Locator locator) {
    if (!locator.isSingleValue()) {
      locator.setDimensionIfNotPresent(DIMENSION_RECURSIVE, "false");
      locator.setDimensionIfNotPresent(HIDDEN_DIMENSION_NAME, "false");
      locator.setDimensionIfNotPresent(ARCHIVES_DIMENSION_NAME, "false");
    }
  }

  /**
   * @return 'null' if the locator is not default, value of dimension "count" otherwise (-1 no "count" dimension is present)
   */
  @Nullable
  public static Integer getCountIfDefaultLocator(@Nullable final String locatorText) {
    if (locatorText == null) return -1;
    try {
      Locator locator = new Locator(locatorText);
      if (locator.isSingleValue()) return null;
      if (!FilterUtil.isIncludedByBooleanFilter(false, locator.getSingleDimensionValueAsBoolean(DIMENSION_RECURSIVE))) return null;
      if (!FilterUtil.isIncludedByBooleanFilter(false, locator.getSingleDimensionValueAsBoolean(HIDDEN_DIMENSION_NAME))) return null;
      if (!FilterUtil.isIncludedByBooleanFilter(false, locator.getSingleDimensionValueAsBoolean(ARCHIVES_DIMENSION_NAME))) return null;
      Long count = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
      if (!locator.getUnusedDimensions().isEmpty()) return null;
      if (count == null) return -1;
      return count.intValue();
    } catch (LocatorProcessException e) {
      return null;
    }
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

    return new BuildArtifactsFinder(initialElement, serviceLocator.getSingletonService(TimeCondition.class)).getItems(filesLocator).getEntries();
  }

  @NotNull
  @Override
  public ItemHolder<ArtifactTreeElement> getPrefilteredItems(@NotNull final Locator locator) {
    setLocatorDefaults(locator);

    Boolean includeHidden = locator.getSingleDimensionValueAsBoolean(HIDDEN_DIMENSION_NAME);
    if (isWithinHidden(myBaseElement)) {
      includeHidden = null;
    }

    List<String> rules = new ArrayList<>();
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
      final Boolean parsedBoolean = LocatorUtil.getStrictBoolean(recursive);
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
      final Boolean parsedBoolean = LocatorUtil.getStrictBoolean(listArchives);
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

    final List<ArtifactTreeElement> result = new ArrayList<>();
    AntPatternTreeMatcher.ScanOption[] options = {};
    if (includeDirectories != null && !includeDirectories) {
      options = new AntPatternTreeMatcher.ScanOption[]{AntPatternTreeMatcher.ScanOption.LEAFS_ONLY};  // does not seem to have any effect, see TW-41662
    }

    final Node rootNode = new Node(myBaseElement, childrenNestingLevel, archiveChildrenNestingLevel, includeHidden, true);
    final Collection<Node> rawResult = AntPatternTreeMatcher.scan(rootNode, rules, options);
    final Boolean finalIncludeDirectories = includeDirectories;
    result.addAll(CollectionsUtil.filterAndConvertCollection(rawResult, Node::getElement, data -> {
      if (rootNode.equals(data)) {
        return false; //TeamCity API issue: should support not returning the first node in API
      }
      //noinspection RedundantIfStatement
      if (!FilterUtil.isIncludedByBooleanFilter(finalIncludeDirectories, !data.getElement().isLeaf())) {
        return false;
      }
      return true;
    }));

    try {
      Collections.sort(result, ARTIFACT_COMPARATOR);
    } catch (Exception e) {
      LOG.error("Error sorting results: " + result.stream().map(Element::getFullName).collect(Collectors.joining(", ", "{", "}")), e);
    }
    return ItemHolder.of(result);
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
    return createElement(buildPromotion, path, serviceLocator, buildPromotionEx, artifacts);
  }
  @NotNull
  public static Element getArtifactElementToServeContent(@NotNull final BuildPromotion buildPromotion, @NotNull final String path, final @NotNull ServiceLocator serviceLocator) {
    final BuildPromotionEx buildPromotionEx = (BuildPromotionEx)buildPromotion;
    boolean analyzeDownloadPath = TeamCityProperties.getBooleanOrTrue("teamcity.rest.artifactContent.analyzePath");
    BuildArtifactsViewMode mode = BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT;
    if (analyzeDownloadPath && !path.contains("!")) {
      // if path contains ! then we create an instance with ability to look inside archives
      // if path does not have ! we return faster implementation
      mode = BuildArtifactsViewMode.VIEW_ALL;
    }

    BuildArtifacts artifacts = buildPromotionEx.getArtifacts(mode);
    return createElement(buildPromotion, path, serviceLocator, buildPromotionEx, artifacts);
  }

  @NotNull
  private static ArtifactTreeElement createElement(@NotNull BuildPromotion buildPromotion,
                                                            @NotNull String path,
                                                            @NotNull ServiceLocator serviceLocator,
                                                            BuildPromotionEx buildPromotionEx,
                                                            BuildArtifacts artifacts) {
    if (!artifacts.isAvailable()) {
      return new BuildHoldingElement(artifacts.getRootArtifact(), buildPromotion);
    }
    final BuildArtifactHolder holder = artifacts.findArtifact(path);
    if (!holder.isAvailable() && !"".equals(path)) { // "".equals(path) is a workaround for no artifact directory case
      return getItem(new ArtifactsBrowserImpl(artifacts), path, LogUtil.describe(buildPromotionEx), serviceLocator);
    }
    if (!holder.isAccessible()) {
      throw new AuthorizationFailedException("Artifact is not accessible with current user permissions. Relative path: '" + holder.getRelativePath() + "'");
    }
    return new BuildHoldingElement(holder.getArtifact(), buildPromotion);
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
        return CollectionsUtil.filterAndConvertCollection(myElement.getChildren(), source -> {
          final Boolean nestedHidden = myHidden != null && myHidden && isHiddenDir(source) ? null : myHidden; //do not filter if we list hidden files and already within .teamcity
          return new Node(source, nextListChildrenLevel, nextListArchiveChildrenLevel, nestedHidden, false);
        }, data -> {
          return FilterUtil.isIncludedByBooleanFilter(myHidden, isHiddenDir(data)); //do not go into .teamcity
        });
      } catch (BrowserException e) {
        //noinspection ThrowableResultOfMethodCallIgnored
        if (ExceptionUtil.getCause(e, AccessDeniedException.class) != null) {
          throw new AuthorizationFailedException("Error listing children for artifact '" + myElement.getFullName() + "'.", e);
        }
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

  public static class BuildHoldingElement extends ArtifactElement {
    @NotNull private final BuildArtifact myBuildArtifact;
    @NotNull private final BuildPromotion myBuildPromotion;

    public BuildHoldingElement(@NotNull BuildArtifact e, @NotNull BuildPromotion build) {
      super(e);
      myBuildArtifact = e;
      myBuildPromotion = build;
    }

    @NotNull
    public BuildArtifact getBuildArtifact() {
      return myBuildArtifact;
    }

    @NotNull
    public BuildPromotion getBuildPromotion() {
      return myBuildPromotion;
    }
  }

  static boolean isWithinHidden(final @NotNull Element data) {
    final String fullName = data.getFullName();
    return fullName.equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)
           || fullName.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/")
           || fullName.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + ".");
  }

  static boolean isHiddenDir(final @NotNull Element data) {
    final String fullName = data.getFullName();
    return fullName.equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR)
           || fullName.equals(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + "/")
           || fullName.startsWith(ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + ".");
  }

  private static class ArtifactsComparator implements Comparator<ArtifactTreeElement> {
    static final char PS = '/'; //path separator

    public int compare(final ArtifactTreeElement o1, final ArtifactTreeElement o2) {
      if (o1 == o2) return 0;
      if (o1 == null) return -1;
      if (o2 == null) return 1;

      return compare(o1.getFullName(), o1.isContentAvailable(), o2.getFullName(), o2.isContentAvailable());
    }

    public int compare(@NotNull String s1, final boolean isFile1, @NotNull String s2, final boolean isFile2) {
      int n1 = s1.length();
      int n2 = s2.length();
      int min = Math.min(n1, n2);
      int characterComparisonResult = n1 - n2;
      boolean shouldStopOnFirstDiff = false;
      boolean shouldStopOnFirstDiffSet = false;
      int i;
      for (i = 0; i < min; i++) {
        char c1 = s1.charAt(i);
        char c2 = s2.charAt(i);
        if (c1 != c2) {
          if (PS == c1) return -1;
          if (PS == c2) return 1;
          //logic like in standard String.CASE_INSENSITIVE_ORDER
          c1 = Character.toUpperCase(c1);
          c2 = Character.toUpperCase(c2);
          if (c1 != c2) {
            c1 = Character.toLowerCase(c1);
            c2 = Character.toLowerCase(c2);
            if (c1 != c2) {
              characterComparisonResult = c1 - c2;
              break;
            }
          }
          // same char in different case
          if (!shouldStopOnFirstDiffSet) {
            shouldStopOnFirstDiff = !isFile1 || !isFile2 || s1.indexOf(PS, i) != -1 || s2.indexOf(PS, i) != -1;
            shouldStopOnFirstDiffSet = true;
          }
          characterComparisonResult = s2.charAt(i) - s1.charAt(i); //order is reversed to make lowercase characters appear before upper case ones
          if (shouldStopOnFirstDiff) {
            break;
          }
        }
      }

      if (i == min && n1 == n2 && characterComparisonResult != 0) { // characterComparisonResult holds firstDiff
        return characterComparisonResult; //define order for non-directories different only in case
      }

      //should go back and forth to scan a full digit
      if (i < min && (Character.isDigit(s1.charAt(i)) || Character.isDigit(s2.charAt(i)))) {
        long number1 = extractNumber(s1, i);
        long number2 = extractNumber(s2, i);
        if (number1 != -1 && number2 != -1 && number1 != number2) {
          characterComparisonResult = number1 - number2 < 0 ? -1 : 1;
        }
      }

      //noinspection SimplifiableConditionalExpression
      boolean containsNested1 = i < n1 ? s1.indexOf(PS, i) != -1 : false;
      //noinspection SimplifiableConditionalExpression
      boolean containsNested2 = i < n2 ? s2.indexOf(PS, i) != -1 : false;
      if (containsNested1 != containsNested2) {
        if (!containsNested1) return isFile1 ? 1 : characterComparisonResult;
        return isFile2 ? -1 : characterComparisonResult;
      } else {
        if (!containsNested1 && isFile1 != isFile2) {
          return isFile1 ? 1 : -1;
        } else {
          return characterComparisonResult;
        }
      }
    }

    /**
     * returns positive number covering position at index
     */
    private long extractNumber(final String text, final int index) {
      int numberStart = index;
      int numberEnd = index;
      while (numberStart > 0) {
        if (!Character.isDigit(text.charAt(numberStart - 1))) {
          break;
        }
        numberStart--;
      }
      while (numberEnd < text.length()) {
        if (!Character.isDigit(text.charAt(numberEnd))) {
          break;
        }
        numberEnd++;
      }
      try {
        return Long.parseLong(text.substring(numberStart, numberEnd));
      } catch (NumberFormatException ignore) {
        return -1;
      }
    }
  }
}
