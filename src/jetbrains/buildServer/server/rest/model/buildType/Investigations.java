package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
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
  @XmlElement(name = "investigation") public List<Investigation> items;
  @XmlAttribute public long count;
  @XmlAttribute(required = false) @Nullable public String nextHref;
  @XmlAttribute(required = false) @Nullable public String prevHref;

  public Investigations() {
  }

  public Investigations(@NotNull final Collection<InvestigationWrapper> itemsP, @Nullable final PagerData pagerData, @NotNull final DataProvider dataProvider, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    items = new ArrayList<Investigation>(itemsP.size());
    for (InvestigationWrapper item : itemsP) {
      items.add(new Investigation(item, dataProvider, apiUrlBuilder));
    }
    if (pagerData != null) {
      nextHref = pagerData.getNextHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getNextHref()) : null;
      prevHref = pagerData.getPrevHref() != null ? apiUrlBuilder.transformRelativePath(pagerData.getPrevHref()) : null;
    }
    count = items.size();
  }
}
