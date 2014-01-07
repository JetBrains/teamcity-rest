package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.request.TestRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "test")
@XmlType(name = "test", propOrder = {"id", "name",
  "mutes", "investigations", "testOccurrences"})
public class Test {
  @XmlAttribute public long id;
  @XmlAttribute public String name;
  @XmlAttribute public String href;

  @XmlElement public Mutes mutes;  // todo: also make this href
  @XmlElement public Investigations investigations;
  @XmlElement public Href testOccurrences;

  public Test() {
  }

  public Test(final @NotNull STest test, final @NotNull BeanContext beanContext, @NotNull final Fields fields) {
    id = test.getTestNameId();
    name = test.getName().getAsString();

    final ApiUrlBuilder apiUrlBuilder = beanContext.getApiUrlBuilder();
    href = apiUrlBuilder.transformRelativePath(TestRequest.getHref(test));

    if (fields.isAllFieldsIncluded()) {
      final ArrayList<MuteInfo> muteInfos = new ArrayList<MuteInfo>();
      final CurrentMuteInfo currentMuteInfo = test.getCurrentMuteInfo(); //todo: TeamCity API: how to get unique mutes?
      if (currentMuteInfo != null) {
        muteInfos.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getProjectsMuteInfo().values())); //add with deduplication
        muteInfos.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getBuildTypeMuteInfo().values())); //add with deduplication
      }
      if (muteInfos.size() > 0) {
        mutes = new Mutes(muteInfos, null, null, beanContext);
      }
      if (test.getAllResponsibilities().size() > 0) {
        investigations = new Investigations(beanContext.getSingletonService(InvestigationFinder.class).getInvestigationWrappers(test),
                                            new Href(InvestigationRequest.getHref(test), apiUrlBuilder),
                                            fields.getNestedField("investigations"),
                                            null,
                                            beanContext);
      }
      testOccurrences = new Href(TestOccurrenceRequest.getHref(test), apiUrlBuilder);
    }
  }
}
