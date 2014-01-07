package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "investigations")
public class Investigations {
  @XmlAttribute public Long count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;
  @XmlAttribute(name = "href") public String href;

  @XmlElement(name = "investigation") public List<Investigation> items;

  public Investigations() {
  }

  public Investigations(@Nullable final Collection<InvestigationWrapper> itemsP,
                        @Nullable final Href hrefP,
                        @NotNull final Fields fields,
                        @Nullable final PagerData pagerData,
                        @NotNull final ServiceLocator serviceLocator,
                        @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this(itemsP, hrefP, fields, pagerData, new BeanContext(serviceLocator.getSingletonService(BeanFactory.class), serviceLocator, apiUrlBuilder));
  }

  public Investigations(@Nullable final Collection<InvestigationWrapper> itemsP,
                        @Nullable final Href hrefP,
                        @NotNull final Fields fields,
                        @Nullable final PagerData pagerData,
                        @NotNull final BeanContext beanContext) {
    href = hrefP != null ? hrefP.getHref() : null;

    if (fields.isAllFieldsIncluded()) {
      if (itemsP != null) {
        items = new ArrayList<Investigation>(itemsP.size());
        for (InvestigationWrapper item : itemsP) {
          items.add(new Investigation(item, fields.getNestedField("investigation"), beanContext));
        }
        count = (long)items.size();

        if (pagerData != null) {
          nextHref = pagerData.getNextHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getNextHref()) : null;
          prevHref = pagerData.getPrevHref() != null ? beanContext.getApiUrlBuilder().transformRelativePath(pagerData.getPrevHref()) : null;
        }
      }
    }
  }
}
