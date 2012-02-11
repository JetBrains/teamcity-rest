package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "investigations")
public class Investigations {
  @XmlElement(name = "investigation")
  public List<Investigation> investigations;

  public Investigations() {
  }

  public Investigations(@NotNull final SBuildType buildType, @NotNull final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    final ResponsibilityEntry.State state = buildType.getResponsibilityInfo().getState();
    if (state.equals(ResponsibilityEntry.State.NONE)) {
      investigations = Collections.emptyList();
    } else {
      investigations = Collections.singletonList(new Investigation(buildType, dataProvider, apiUrlBuilder));
    }
  }
}
