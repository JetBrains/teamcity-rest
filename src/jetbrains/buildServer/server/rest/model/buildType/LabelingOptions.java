package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 04.05.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "labeling")
public class LabelingOptions {
  @XmlAttribute(name = "label")
  public Boolean label;

  public LabelingOptions() {
  }

  public LabelingOptions(final Boolean label) {
    this.label = label;
  }
}
