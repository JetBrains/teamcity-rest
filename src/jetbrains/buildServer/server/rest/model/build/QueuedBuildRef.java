package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.WebLinks;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Yegor.Yarko
 *         Date: 03.11.13
 */
@XmlRootElement(name = "queuedBuild-ref")
@XmlType(name = "queuedBuild-ref",
         propOrder = {"id", "buildTypeId", "branchName", "defaultBranch", "unspecifiedBranch", "queuedDate", "href", "webUrl"})
public class QueuedBuildRef {
  protected SQueuedBuild myBuild;
  private ApiUrlBuilder myApiUrlBuilder;
  @Autowired private ServiceLocator myServiceLocator;

  public QueuedBuildRef() {
  }

  public QueuedBuildRef(@NotNull final SQueuedBuild build, final ApiUrlBuilder apiUrlBuilder, final BeanFactory factory) {
    myBuild = build;
    myApiUrlBuilder = apiUrlBuilder;
    factory.autowire(this);
  }

  @XmlAttribute
  public String getId() {
    return myBuild.getItemId();
  }

  @XmlAttribute
  public String getBuildTypeId() {
    return myBuild.getBuildType().getExternalId();
  }

  @XmlAttribute
  public String getBranchName() {
    jetbrains.buildServer.serverSide.Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return branch.getDisplayName();
  }

  @XmlAttribute
  public Boolean getDefaultBranch() {
    jetbrains.buildServer.serverSide.Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return branch.isDefaultBranch() ? Boolean.TRUE : null;
  }

  @XmlAttribute
  public Boolean getUnspecifiedBranch() {
    jetbrains.buildServer.serverSide.Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return jetbrains.buildServer.serverSide.Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()) ? Boolean.TRUE : null;
  }


  @XmlAttribute
  public String getQueuedDate() {
    return Util.formatTime(myBuild.getWhenQueued());
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuild);
  }

  @XmlAttribute
  public String getWebUrl() {
    return myServiceLocator.getSingletonService(WebLinks.class).getQueuedBuildUrl(myBuild);
  }

}
