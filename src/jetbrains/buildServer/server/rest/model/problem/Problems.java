package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problems")
public class Problems {
  @XmlElement(name = "problem") public List<Problem> items;
  @XmlAttribute public long count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  public Problems() {
  }

  public Problems(@NotNull final List<ProblemWrapper> itemsP,
                  @Nullable final PagerData pagerData,
                  final ServiceLocator serviceLocator,
                  @NotNull final ApiUrlBuilder apiUrlBuilder) {
    final List<ProblemWrapper> sortedItems = new ArrayList<ProblemWrapper>(itemsP);
    Collections.sort(sortedItems, new Comparator<ProblemWrapper>() {
      public int compare(final ProblemWrapper o1, final ProblemWrapper o2) {
        return o1.getId().compareTo(o2.getId());
      }
    });
    items = new ArrayList<Problem>(sortedItems.size());  //todo: consider adding ordering/sorting
    for (ProblemWrapper item : sortedItems) {
      items.add(new Problem(item, serviceLocator, apiUrlBuilder, new Fields()));
    }
    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getPrevHref()) : null;
    }
    count = items.size();
  }
}
