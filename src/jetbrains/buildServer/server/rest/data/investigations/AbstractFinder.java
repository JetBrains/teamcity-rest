package jetbrains.buildServer.server.rest.data.investigations;


import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.data.AbstractFilter;
import jetbrains.buildServer.server.rest.data.FilterItemProcessor;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public abstract class AbstractFinder<ITEM> {
  public static final String DIMENSION_ID = "id";

  private final String[] myKnownDimensions;
  public static final String[] ADDITIONAL = new String[]{DIMENSION_ID, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME};

  public AbstractFinder(final String[] knownDimensions) {
    myKnownDimensions = new String[knownDimensions.length + 2];
    System.arraycopy(knownDimensions, 0, myKnownDimensions, 0, knownDimensions.length);
    System.arraycopy(ADDITIONAL, 0, myKnownDimensions, knownDimensions.length, ADDITIONAL.length);
  }

  @NotNull
  public Locator createLocator(@Nullable final String locatorText) {
    final Locator result = new Locator(locatorText, myKnownDimensions);
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    return result;
  }

  @Nullable
  public Locator getLocatorOrNull(@Nullable final String locatorText) {
    return locatorText != null ? createLocator(locatorText) : null;
  }

  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText) {
    return getItems(getLocatorOrNull(locatorText));
  }

  public PagedSearchResult<ITEM> getItems(@Nullable final Locator locator) {
    if (locator == null) {
      return new PagedSearchResult<ITEM>(getAllItems(), null, null);
    }

    ITEM singleItem = findSingleItem(locator);
    if (singleItem != null){
      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
    }

    AbstractFilter<ITEM> filter = getFilter(locator);
    final List<ITEM> unfilteredItems = getPrefilteredItems(locator);
    locator.checkLocatorFullyProcessed();
    return new PagedSearchResult<ITEM>(getItems(filter, unfilteredItems), filter.getStart(), filter.getCount());
  }

  @NotNull
  protected List<ITEM> getItems(final @NotNull AbstractFilter<ITEM> filter, final @NotNull List<ITEM> unfilteredItems) {
    //todo: current implementation is not effective: consider pre-filtering by filter fields, if specified
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    AbstractFilter.processList(unfilteredItems, filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  @NotNull
  public ITEM getItem(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty locator is not supported.");
    }
    final Locator locator = createLocator(locatorText);

    locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
    final PagedSearchResult<ITEM> items = getItems(locator);
    if (items.myEntries.size() == 0) {
      throw new NotFoundException("Nothing is found by locator '" + locatorText + "'.");
    }
    assert items.myEntries.size()== 1;
    return items.myEntries.get(0);
  }

  protected List<ITEM> getPrefilteredItems(@NotNull Locator locator) {
    return getAllItems();
  }

  @Nullable
  protected ITEM findSingleItem(final Locator locator){
    return null;
  }

  @NotNull
  public abstract List<ITEM> getAllItems();

  protected abstract AbstractFilter<ITEM> getFilter(final Locator locator);
}
