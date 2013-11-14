package jetbrains.buildServer.server.rest.data.investigations;


import java.util.Collections;
import jetbrains.buildServer.server.rest.data.AbstractFilter;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.model.PagerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public abstract class AnstractFinder<ITEM> {
  public static final String DIMENSION_ID = "id";

  private final String[] myKnownDimensions;
  private final ItemBridge<ITEM> myBridge;
  public static final String[] ADDITIONAL = new String[]{DIMENSION_ID, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME};

  public AnstractFinder(final ItemBridge<ITEM> bridge, final String[] knownDimensions) {
    myBridge = bridge;
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
      return new PagedSearchResult<ITEM>(myBridge.getAllItems(), null, null);
    }

    ITEM singleItem = findSingleItemAsList(locator);
    if (singleItem != null){
      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
    }

    AbstractFilter<ITEM> filter = getFilter(locator);
    locator.checkLocatorFullyProcessed();

    return new PagedSearchResult<ITEM>(myBridge.getItems(filter), filter.getStart(), filter.getCount());
  }

  protected abstract ITEM findSingleItemAsList(final Locator locator);

  protected abstract AbstractFilter<ITEM> getFilter(final Locator locator);
}
