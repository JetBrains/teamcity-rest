package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problemOccurrences")
public class ProblemOccurrences {
  @XmlElement(name = "problemOccurrence") public List<ProblemOccurrence> items;
  @XmlAttribute public long count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  public ProblemOccurrences() {
  }

  public ProblemOccurrences(@NotNull final List<BuildProblem> itemsP,
                            @Nullable final PagerData pagerData,
                            @NotNull final BeanContext beanContext,
                            @NotNull final Fields fields) {
    Collections.sort(itemsP, new Comparator<BuildProblem>() {
      public int compare(final BuildProblem o1, final BuildProblem o2) {
        return o1.getId()-o2.getId();
      }
    });
    items = new ArrayList<ProblemOccurrence>(itemsP.size());  //todo: consider adding ordering/sorting
    for (BuildProblem item : itemsP) {
      items.add(new ProblemOccurrence(item, beanContext, fields.getNestedField("items")));
    }
    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }
    count = items.size();
  }
}
