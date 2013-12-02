package jetbrains.buildServer.server.rest.model.problem;

import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.STest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "tests")
public class Tests {
  @XmlElement(name = "test") public List<Test> items;
  @XmlAttribute public long count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  public Tests() {
  }

  public Tests(@NotNull final Collection<STest> itemsP, @Nullable final PagerData pagerData, @NotNull final BeanContext beanContext, @NotNull final Fields fields) {
    final List<STest> sortedItems = new ArrayList<STest>(itemsP);
    Collections.sort(sortedItems, new Comparator<STest>() {
      public int compare(final STest o1, final STest o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    items = new ArrayList<Test>(sortedItems.size());
    for (STest item : sortedItems) {
      items.add(new Test(item, beanContext, fields.getNestedField("test")));
    }
    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }
    count = items.size();
  }
}
