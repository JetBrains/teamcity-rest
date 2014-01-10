package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.OccurrencesSummary;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problemOccurrences")
@XmlType(name = "problemOccurrences", propOrder = {"count", "href", "nextHref", "prevHref",
  "items"})
public class ProblemOccurrences extends OccurrencesSummary {
  @XmlElement(name = "problemOccurrence") public List<ProblemOccurrence> items;
  @XmlAttribute public Integer count;
  @XmlAttribute(name = "href") public String href;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  public ProblemOccurrences() {
  }

  public ProblemOccurrences(@NotNull final List<BuildProblem> itemsP,
                            @Nullable final String shortHref,
                            @Nullable final PagerData pagerData,
                            @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    this(itemsP, null, null, null, null, null, null, shortHref, pagerData, fields, beanContext);
  }

  public ProblemOccurrences(@Nullable final List<BuildProblem> itemsP,
                            @Nullable final Integer count,
                            @Nullable final Integer passed,
                            @Nullable final Integer failed,
                            @Nullable final Integer newFailed,
                            @Nullable final Integer ignored,
                            @Nullable final Integer muted,
                            @Nullable final String shortHref,
                            @Nullable final PagerData pagerData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    super(passed, failed, newFailed, ignored, muted, fields);
    if (itemsP != null) {
      items = ValueWithDefault.decideDefault(fields.isIncluded("problemOccurrence"), new ValueWithDefault.Value<List<ProblemOccurrence>>() {
        @Nullable
        public List<ProblemOccurrence> get() {
          final List<BuildProblem> sortedItems = new ArrayList<BuildProblem>(itemsP);
          Collections.sort(sortedItems, new Comparator<BuildProblem>() {
            public int compare(final BuildProblem o1, final BuildProblem o2) {
              return o1.getId() - o2.getId();
            }
          });
          final ArrayList<ProblemOccurrence> result = new ArrayList<ProblemOccurrence>(sortedItems.size());
          for (BuildProblem item : sortedItems) {
            result.add(new ProblemOccurrence(item, beanContext, fields.getNestedField("problemOccurrence")));
          }
          return result;
        }
      });
      this.count = ValueWithDefault.decideDefault(fields.isIncluded("count", true), items.size());
    } else {
      this.count = ValueWithDefault.decideDefault(fields.isIncluded("count"), count);
    }

    this.href = ValueWithDefault.decide(fields.isIncluded("href"),
                                        shortHref != null ? beanContext.getApiUrlBuilder().transformRelativePath(shortHref) : null,
                                        null,
                                        !ValueWithDefault.isAllDefault(count, passed, failed, newFailed, ignored, muted));

    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
    }
  }

  @Override
  public boolean isDefault() {
    return ValueWithDefault.isAllDefault(count, href, items) && super.isDefault();
  }

}
