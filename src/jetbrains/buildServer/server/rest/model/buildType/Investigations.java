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
    href = hrefP != null ? hrefP.getHref() : null;

    if (fields.isAllFieldsIncluded()) {
      if (itemsP != null) {
        items = new ArrayList<Investigation>(itemsP.size());
        for (InvestigationWrapper item : itemsP) {
          items.add(new Investigation(item, fields.getNestedField("investigation"), serviceLocator, apiUrlBuilder));
        }
        count = (long)items.size();

        if (pagerData != null) {
          nextHref = pagerData.getNextHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getNextHref()) : null;
          prevHref = pagerData.getPrevHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getPrevHref()) : null;
        }
      }
    }
  }
}
