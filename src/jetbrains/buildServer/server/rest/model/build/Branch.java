package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Yegor.Yarko
 *         Date: 28.07.12
 */
@XmlRootElement(name = "branch")
@XmlType(name = "branch", propOrder = {"name", "default", "unspecified"})
public class Branch {
  private jetbrains.buildServer.serverSide.Branch myBranch;

  public Branch() {
  }

  public Branch(jetbrains.buildServer.serverSide.Branch branch) {
    myBranch = branch;
  }

  @XmlAttribute(name = "name")
  String getName(){
    return myBranch.getDisplayName();
  }

  @XmlAttribute(name = "default")
  Boolean isDefault(){
    return myBranch.isDefaultBranch() ? Boolean.TRUE : null;
  }

  @XmlAttribute(name = "unspecified")
  Boolean isUnspecified(){
    return jetbrains.buildServer.serverSide.Branch.UNSPECIFIED_BRANCH_NAME.equals(myBranch.getName()) ? Boolean.TRUE : null;
  }
}
