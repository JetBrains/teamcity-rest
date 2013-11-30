package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType
@XmlRootElement(name = "investigation")
public class Investigation {
  @XmlAttribute
  public String id;
  @XmlAttribute
  public String state;
  @XmlAttribute
  public String href;

  @XmlElement
  public UserRef responsible;

  @XmlElement
  public Comment assignment;

  @XmlElement
  public InvestigationScope scope;

  public Investigation() {
  }

  public Investigation(final @NotNull InvestigationWrapper investigation,
                       final @NotNull Fields fields,
                       final @NotNull ServiceLocator serviceLocator,
                       final @NotNull ApiUrlBuilder apiUrlBuilder) {
    final ResponsibilityEntry.State stateOjbect = investigation.getState();
    state = stateOjbect.name();
    if (stateOjbect.equals(ResponsibilityEntry.State.NONE)){
      return;
    }

    id = investigation.getId();

    /*
    //todo: THIS MIGHT NOT WORK!!!
    final ResponsibilityEntryEx responsibilityEntryEx = (ResponsibilityEntryEx)investigation;
    id = responsibilityEntryEx.getProblemId();
    */

    href = InvestigationRequest.getHref(investigation);
    if (fields.isAllFieldsIncluded() || true) {

      scope = new InvestigationScope(investigation, fields.getNestedField("scope"), serviceLocator, apiUrlBuilder);
      responsible = new UserRef(investigation.getResponsibleUser(), apiUrlBuilder);

      //todo: add all investigation fields: state, removeType, etc.
      assignment = new Comment(investigation.getReporterUser(), investigation.getTimestamp(), investigation.getComment(), apiUrlBuilder);
    }
  }
}
