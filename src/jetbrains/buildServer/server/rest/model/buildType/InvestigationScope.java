package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType
public class InvestigationScope {
  @XmlElement
  public BuildTypeRef buildType;

  public InvestigationScope() {
  }

  public InvestigationScope(final SBuildType buildTypeP, @NotNull final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    buildType = new BuildTypeRef(buildTypeP, dataProvider, apiUrlBuilder);
  }
}
