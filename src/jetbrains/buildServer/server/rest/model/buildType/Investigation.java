package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType
public class Investigation {
  @XmlAttribute
  public String id;
  @XmlAttribute
  public String state;

  @XmlElement
  public UserRef responsible;

  @XmlElement
  public Comment assignment;

  @XmlElement
  public InvestigationScope scope;

  public Investigation() {
  }

  public Investigation(final @NotNull SBuildType buildType, final @NotNull DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    final ResponsibilityEntry responsibilityEntry = buildType.getResponsibilityInfo();
    final ResponsibilityEntry.State stateOjbect = responsibilityEntry.getState();
    state = stateOjbect.name();
    if (stateOjbect.equals(ResponsibilityEntry.State.NONE)){
      return;
    }
    id = buildType.getBuildTypeId(); // still uses internal id, TBD if appropriate
    scope = new InvestigationScope(buildType, dataProvider, apiUrlBuilder);
    responsible = new UserRef(responsibilityEntry.getResponsibleUser(), apiUrlBuilder);

    assignment = new Comment(responsibilityEntry.getReporterUser(), responsibilityEntry.getTimestamp(), responsibilityEntry.getComment(), apiUrlBuilder);
  }
}
