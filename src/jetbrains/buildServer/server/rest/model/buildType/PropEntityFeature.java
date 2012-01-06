package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.serverSide.ParametersDescriptor;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "feature")
public class PropEntityFeature extends PropEntity{
  public PropEntityFeature() {
  }
  public PropEntityFeature(ParametersDescriptor descriptor) {
    super(descriptor);
  }
}
